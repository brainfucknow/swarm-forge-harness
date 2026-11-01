(ns sf.config
  "The catalogue files under swarmforge/: roles, tiers, routes, and tools."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.catalogue :as catalogue]
            [sf.flow :as flow]
            [sf.shell :as shell]))

(defn conf-dir []
  (str (fs/path (shell/repo-root) "swarmforge")))

(defn conf-file [name]
  (str (fs/path (conf-dir) name)))

(defn rows [name]
  (shell/conf-rows (conf-file name)))

(defn- field
  "Column (1-based) of the first row whose first field is key."
  [rows key column]
  (some #(when (= key (first %)) (get % (dec column))) rows))

(defn pipeline []
  (map first (rows "roles.conf")))

(defn role-field [role column]
  (field (rows "roles.conf") role column))

(defn tier-field [tier column]
  (field (rows "tiers.conf") tier column))

(defn require-role [role]
  (when-not (some #{role} (pipeline))
    (shell/die "unknown role '" role "' (see swarmforge/roles.conf)")))

(defn- role-flags [role]
  (drop 3 (some #(when (= role (first %)) %) (rows "roles.conf"))))

(defn role-audits? [role]
  (boolean (some #{"audit"} (role-flags role))))

(defn named-routes []
  (rows "routes.conf"))

(defn catalogue-route []
  (str/join "," (pipeline)))

(defn resolve-route
  "The comma-separated role list of a route name or list. The roles must exist
  and be in catalogue order."
  [spec]
  (let [route (or (field (named-routes) spec 2) spec)
        roles (flow/route-list route)
        catalogue (vec (pipeline))]
    (when (empty? roles) (shell/die "empty route"))
    (reduce (fn [last role]
              (let [index (.indexOf catalogue role)]
                (when (neg? index)
                  (shell/die "unknown role '" role "' in route '" spec "' (see swarmforge/roles.conf)"))
                (when (<= index last)
                  (shell/die "route '" spec "' is out of order; follow swarmforge/roles.conf: " (catalogue-route)))
                index))
            -1 roles)
    route))

(defn default-route []
  (let [route (shell/env "SWARMFORGE_ROUTE" nil)]
    (cond route (resolve-route route)
          (field (named-routes) "feature" 2) (resolve-route "feature")
          :else (let [roles (remove #(some #{"optional"} (role-flags %)) (pipeline))]
                  (when (empty? roles) (shell/die "empty route"))
                  (str/join "," roles)))))

;; sf check

(defn- scan
  "Problems from (check row seen) for each row; seen holds the earlier first fields."
  [rows check]
  (:problems (reduce (fn [{:keys [seen] :as acc} row]
                       (-> acc
                           (update :problems into (check row seen))
                           (update :seen conj (first row))))
                     {:seen #{} :problems []}
                     rows)))

(defn- check-tiers []
  (if-not (fs/exists? (conf-file "tiers.conf"))
    ["tiers.conf: missing"]
    (scan (rows "tiers.conf")
          (fn [[tier :as row] seen]
            (cond-> []
              (not= 4 (count row)) (conj (str "tiers.conf: '" tier "' needs exactly <tier> <claude-model> <codex-effort> <copilot-model>"))
              (seen tier) (conj (str "tiers.conf: tier '" tier "' listed twice")))))))

(defn- flag-problems [role flags]
  (concat
    (for [flag (distinct flags) :when (not (#{"audit" "optional"} flag))]
      (str "roles.conf: '" role "' has unknown flag '" flag "' (only audit, optional)"))
    (for [[flag n] (frequencies flags) :when (> n 1)]
      (str "roles.conf: '" role "' repeats flag '" flag "'"))))

(defn- check-role [[role tier runs & flags :as row] seen]
  (if-not (<= 3 (count row) 5)
    [(str "roles.conf: '" role "' needs <role> <tier> <runs-as> [audit] [optional]")]
    (cond-> (vec (flag-problems role flags))
      (not (re-matches #"[a-z][a-z0-9-]*" role)) (conj (str "roles.conf: role name '" role "' must be lowercase letters, digits, and hyphens"))
      (#{"done" "operator"} role) (conj (str "roles.conf: '" role "' is reserved"))
      (seen role) (conj (str "roles.conf: role '" role "' listed twice"))
      (not (tier-field tier 2)) (conj (str "roles.conf: '" role "' uses unknown tier '" tier "' (see tiers.conf)"))
      (not (#{"subagent" "session"} runs)) (conj (str "roles.conf: '" role "' runs-as must be subagent or session, not '" runs "'"))
      (not (fs/exists? (fs/path (conf-dir) "roles" (str role ".md")))) (conj (str "roles/" role ".md: missing role prompt for '" role "'")))))

(defn- check-roles []
  (if-not (fs/exists? (conf-file "roles.conf"))
    ["roles.conf: missing"]
    (cond-> (scan (rows "roles.conf") check-role)
      (empty? (pipeline)) (conj "roles.conf: no roles"))))

(defn- route-problem [route]
  (try (resolve-route route) nil
       (catch clojure.lang.ExceptionInfo e (ex-message e))))

(defn- check-routes []
  (scan (named-routes)
        (fn [[name route :as row] seen]
          (if (not= 2 (count row))
            [(str "routes.conf: '" name "' needs exactly <name> <role,role,...>")]
            (cond-> []
              (seen name) (conj (str "routes.conf: route '" name "' listed twice"))
              (route-problem route) (conj (str "routes.conf: " name ": " (route-problem route))))))))

(defn- tool-problems
  "The problems of one tools.conf row; seen holds the earlier tool names."
  [tools row seen]
  (let [{:keys [name kind rewrite columns] :as tool} (catalogue/tool row)
        whole? (= 7 columns)]
    (concat (when (seen name) [(str "tool '" name "' listed twice")])
            (when (and whole? (not (catalogue/kinds kind))) [(str "'" name "' has unknown kind '" kind "'")])
            (when (and whole? (not (catalogue/rewrites rewrite))) [(str "'" name "' has unknown rewrite '" rewrite "'")])
            (catalogue/ref-problems tool tools))))

(defn- check-tools []
  (let [tools (map catalogue/tool (rows "tools.conf"))]
    (map #(str "tools.conf: " %) (scan (rows "tools.conf") (partial tool-problems tools)))))

(defn check
  "Validates the catalogue files and prints a summary; stops on any problem."
  []
  (let [problems (concat (check-tiers) (check-roles) (check-routes) (check-tools))]
    (when (seq problems)
      (binding [*out* *err*]
        (doseq [problem problems] (println (str "CHECK: " problem))))
      (shell/die (count problems) " configuration problem(s) in " (conf-dir)))
    (println (str "CHECK: ok (" (count (pipeline)) " roles, " (count (named-routes)) " routes)"))))
