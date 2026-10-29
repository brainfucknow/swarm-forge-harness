(ns sf.adapters
  "Agent files for each subagent role, one per harness."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.shell :as shell]))

(def ^:private targets
  [".claude/agents" ".github/agents" ".codex/agents"])

(defn- role-summary
  "The first non-blank line after the title of the role prompt."
  [role]
  (let [file (fs/path (config/conf-dir) "roles" (str role ".md"))]
    (when-not (fs/exists? file) (shell/die "missing role prompt " file))
    (first (remove str/blank? (rest (str/split-lines (slurp (str file))))))))

(defn- agent-files [role tier]
  (let [body (str/replace (shell/template "adapter.md") "{{role}}" role)
        description (str "SwarmForge " role " role. " (role-summary role) " Use for a task whose stage is " role ".")]
    {(str ".claude/agents/" role ".md")
     (str "---\nname: " role "\ndescription: " description "\nmodel: " (config/tier-field tier 2)
          "\n---\n\n" body)

     (str ".github/agents/" role ".agent.md")
     (str "---\nname: " role "\ndescription: " description "\nmodel: " (config/tier-field tier 4)
          "\ntools: [\"*\"]\ninclude-custom-instructions: true\n---\n\n" body)

     (str ".codex/agents/" role ".toml")
     (str "name = \"" role "\"\ndescription = \"" description "\"\nmodel_reasoning_effort = \""
          (config/tier-field tier 3) "\"\ndeveloper_instructions = \"\"\"\n" body "\"\"\"\n")}))

(defn- digest [text]
  (let [bytes (.digest (java.security.MessageDigest/getInstance "SHA-256")
                       (.getBytes text "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

(defn- adapter-path? [path]
  (boolean (and (string? path)
                (re-matches #"\.(?:claude/agents/[a-z][a-z0-9-]*\.md|github/agents/[a-z][a-z0-9-]*\.agent\.md|codex/agents/[a-z][a-z0-9-]*\.toml)" path))))

(defn- read-ownership [file]
  (when (or (fs/sym-link? file) (fs/sym-link? (fs/parent file)))
    (shell/die "adapter ownership must not be a symbolic link: " file))
  (when (and (fs/exists? (fs/parent file)) (not (fs/directory? (fs/parent file))))
    (shell/die "invalid adapter ownership directory: " (fs/parent file)))
  (if-not (fs/exists? file)
    {}
    (let [owned (edn/read-string (slurp (str file)))]
      (when-not (and (map? owned)
                     (every? (fn [[path hash]]
                               (and (adapter-path? path) (string? hash)
                                    (re-matches #"[a-f0-9]{64}" hash))) owned))
        (shell/die "invalid adapter ownership in " file))
      owned)))

(defn- legacy-shape [path text]
  (str/replace-first text
                     (if (str/ends-with? path ".toml")
                       #"(?m)^model_reasoning_effort = \"[^\r\n]*\"\n"
                       #"(?m)^model: [^\r\n]*\n") ""))

(defn- legacy-ownership [root owned]
  (into {} (for [prompt (fs/glob (fs/path (config/conf-dir) "roles") "*.md")
                 [path text] (agent-files (str/replace (fs/file-name prompt) #"\.md$" "")
                                         (ffirst (config/rows "tiers.conf")))
                 :let [file (fs/path root path)]
                 :when (and (not (contains? owned path)) (not (fs/sym-link? file))
                            (fs/regular-file? file))
                 :let [actual (slurp (str file))]
                 :when (= (legacy-shape path text) (legacy-shape path actual))]
             [path (digest actual)])))

(defn- tracked-canonical? [root path text]
  (every? (fn [revision]
            (let [{:keys [exit out]} (shell/run {:dir root} "git" "show" (str revision path))]
              (and (zero? exit) (= text out))))
          ["HEAD:" ":"]))

(defn- refreshed-ownership [root desired recorded]
  (into {} (for [[path hash] recorded
                 :let [text (get desired path)
                       expected (when text (digest text))
                       file (fs/path root path)]]
             [path (if (and text (not= hash expected) (not (fs/sym-link? file))
                            (fs/regular-file? file) (= text (slurp (str file)))
                            (tracked-canonical? root path text))
                     expected
                     hash)])))

(defn- preflight [root paths owned]
  (doseq [dir targets
          part [dir (fs/parent dir)]
          :let [path (fs/path root part)]
          :when (or (fs/sym-link? path) (and (fs/exists? path) (not (fs/directory? path))))]
    (shell/die "conflicting project agent directory: " dir))
  (doseq [path paths
          :let [file (fs/path root path)]
          :when (or (fs/exists? file) (fs/sym-link? file))]
    (when-not (and (not (fs/sym-link? file)) (fs/regular-file? file)
                   (= (get owned path) (digest (slurp (str file)))))
      (shell/die "conflicting project agent: " path "; preserve or move it before regenerating adapters"))))

(defn adapters [_]
  (shell/quietly config/check)
  (let [root (shell/repo-root)
        roles (filter #(= "subagent" (nth % 2)) (config/rows "roles.conf"))
        desired (into {} (mapcat (fn [[role tier]] (agent-files role tier)) roles))
        state (fs/path root ".swarmforge" "adapters.edn")
        recorded (read-ownership state)
        owned (merge (legacy-ownership root recorded) (refreshed-ownership root desired recorded))]
    (preflight root (distinct (concat (keys desired) (keys owned))) owned)
    (doseq [path (remove (set (keys desired)) (keys owned))]
      (fs/delete-if-exists (fs/path root path)))
    (doseq [[path text] desired]
      (fs/create-dirs (fs/parent (fs/path root path)))
      (spit (str (fs/path root path)) text))
    (fs/create-dirs (fs/parent state))
    (spit (str state) (pr-str (into (sorted-map) (map (fn [[path text]] [path (digest text)]) desired))))
    (doseq [[role tier] roles] (println (str role ": " tier)))))
