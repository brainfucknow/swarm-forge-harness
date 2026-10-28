(ns acceptance.adapter-steps
  (:require [acceptance.support :as support :refer [check require-ok]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def dirs [".claude/agents" ".github/agents" ".codex/agents"])
(def suffixes [".md" ".agent.md" ".toml"])
(defn new-world [] {})

(defn- run-command [world command]
  (if (= "install.sh" command)
    (support/run (:repo world) ["bash" (str (fs/path support/checkout "install.sh")) (:repo world)])
    (support/run (:repo world) [(support/sf-path (:repo world)) "adapters"])))

(defn- generate! [world]
  (require-ok (run-command world "sf adapters") "sf adapters")
  world)

(defn- snapshot [world]
  (into {} (for [dir dirs
                 file (fs/glob (fs/path (:repo world) dir) "*")]
             [(str (fs/relativize (:repo world) file)) (vec (fs/read-all-bytes file))])))

(defn- write-file! [world path text]
  (let [file (fs/path (:repo world) path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text))
  world)

(defn- change-role! [world role field value]
  (let [file (fs/path (:repo world) "swarmforge/roles.conf")
        rows (for [line (str/split-lines (slurp (str file)))]
               (if (re-find (re-pattern (str "^" role "\\s")) line)
                 (str/join " " (assoc (vec (str/split line #"\s+")) field value))
                 line))]
    (spit (str file) (str (str/join "\n" rows) "\n")))
  world)

(defn- require-role-files [world role present?]
  (doseq [[dir suffix] (map vector dirs suffixes)]
    (let [path (fs/path (:repo world) dir (str role suffix))]
      (check (= present? (fs/exists? path)) (str "unexpected adapter existence: " path))))
  world)

(def handlers
  [[#"a scratch git project prepared for adapter generation"
    (fn [world] (assoc world :repo (support/project false)))]

   [#"an unrelated project agent at \"([^\"]+)\" containing \"([^\"]*)\""
    (fn [world path text] (write-file! world path text))]

   [#"I run \"([^\"]+)\" in the scratch project"
    (fn [world command]
      (assoc world :before (snapshot world) :result (run-command world command)))]

   [#"the exit status is (\d+)"
    (fn [world expected]
      (check (= (parse-long expected) (get-in world [:result :exit])) (pr-str (:result world)))
      world)]

   [#"\"([^\"]+)\" still contains exactly \"([^\"]*)\""
    (fn [world path expected]
      (check (= expected (slurp (str (fs/path (:repo world) path)))) (str path " changed"))
      world)]

   [#"adapters exist for the configured subagent roles in all three harnesses"
    (fn [world]
      (doseq [role ["specifier" "coder" "cleaner" "architect" "hardener" "qa"]]
        (require-role-files world role true))
      world)]

   [#"adapters were generated for the configured roles"
    generate!]

   [#"the role \"([^\"]+)\" is changed from subagent to session"
    (fn [world role] (change-role! world role 2 "session"))]

   [#"the role \"([^\"]+)\" uses the tier \"([^\"]+)\""
    (fn [world role tier] (change-role! world role 1 tier))]

   [#"no generated adapter remains for \"([^\"]+)\" in any harness"
    (fn [world role] (require-role-files world role false))]

   [#"the generated adapters for \"([^\"]+)\" use the configured tier settings"
    (fn [world role]
      (let [[_ claude codex copilot]
            (some #(let [fields (str/split % #"\s+")] (when (= "fast" (first fields)) fields))
                  (str/split-lines (slurp (str (fs/path (:repo world) "swarmforge/tiers.conf")))))]
        (doseq [[dir suffix metadata] (map vector dirs suffixes
                                          [(str "model: " claude) (str "model: " copilot)
                                           (str "model_reasoning_effort = \"" codex "\"")])]
          (check (str/includes? (slurp (str (fs/path (:repo world) dir (str role suffix)))) metadata)
                 (str "wrong configured model in " dir))))
      world)]

   [#"a second \"sf adapters\" run succeeds without changing agent files"
    (fn [world]
      (let [before (snapshot world)]
        (generate! world)
        (check (= before (snapshot world)) "repeat generation changed files"))
      world)]

   [#"the scratch project has previously generated coder adapters only"
    (fn [world]
      (doseq [role ["specifier" "cleaner" "architect" "hardener" "qa"]]
        (change-role! world role 2 "session"))
      (generate! world))]

   [#"an unrelated project agent occupies the new role target \"([^\"]+)\" containing \"([^\"]*)\""
    (fn [world path text]
      (when (str/includes? path "/specialist.")
        (spit (str (fs/path (:repo world) "swarmforge/roles.conf")) "specialist fast subagent\n" :append true)
        (write-file! world "swarmforge/roles/specialist.md" "# specialist\n\nProject specialist.\n"))
      (assoc (write-file! world path text) :conflict path))]

   [#"the error output identifies \"([^\"]+)\" as a conflicting project agent"
    (fn [world path]
      (let [err (get-in world [:result :err])]
        (check (and (str/includes? err path) (str/includes? err "conflicting project agent")) err))
      world)]

   [#"no file in the three harness agent directories was created, changed or removed"
    (fn [world]
      (check (= (:before world) (snapshot world)) "collision changed agent files")
      world)]

   [#"the project holds the previously shipped generated adapters"
    (fn [world]
      (doseq [dir dirs]
        (fs/copy-tree (fs/path support/checkout dir) (fs/path (:repo world) dir)))
      world)]

   [#"no local adapter ownership state is present"
    (fn [world]
      (fs/delete-if-exists (fs/path (:repo world) ".swarmforge/adapters.edn"))
      world)]])
