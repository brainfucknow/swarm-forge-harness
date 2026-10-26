(ns sf.adapters
  "Agent files for each subagent role, one per harness."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.shell :as shell]))

(def ^:private targets
  [{:dir ".claude/agents" :pattern "*.md"}
   {:dir ".github/agents" :pattern "*.agent.md"}
   {:dir ".codex/agents" :pattern "*.toml"}])

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

(defn adapters [_]
  (shell/quietly config/check)
  (let [root (shell/repo-root)]
    (doseq [{:keys [dir pattern]} targets]
      (fs/create-dirs (fs/path root dir))
      (run! fs/delete (fs/glob (fs/path root dir) pattern)))
    (doseq [[role tier runs] (config/rows "roles.conf")
            :when (= "subagent" runs)]
      (doseq [[path text] (agent-files role tier)]
        (spit (str (fs/path root path)) text))
      (println (str role ": " tier)))))
