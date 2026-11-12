(ns sf.adapters-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.adapters :as adapters]
            [sf.shell :as shell]
            [clojure.string :as str]
            [sf.repo-support :refer [*dir* with-repo write-conf! captured git]]))

(def paths [".claude/agents/coder.md" ".github/agents/coder.agent.md" ".codex/agents/coder.toml" ".kilo/agent/coder.md"])

(defn write-agent [path text]
  (let [file (fs/path *dir* path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text)))

(def gate-paths [".claude/agents/falsifier.md" ".github/agents/falsifier.agent.md" ".codex/agents/falsifier.toml" ".kilo/agent/falsifier.md"])

(defn write-gate!
  "A catalogue with the falsifier gate and its role prompt."
  []
  (write-conf! *dir* "roles.conf" "specifier frontier subagent\nfalsifier strong subagent gate\ncoder strong subagent\ncleaner fast subagent\n")
  (write-agent "swarmforge/roles/falsifier.md" "# falsifier\n\nAudits a specification.\n"))

(defn snapshot []
  (into {} (for [dir [".claude/agents" ".github/agents" ".codex/agents" ".kilo/agent"]
                 file (fs/glob (fs/path *dir* dir) "*")]
             [(str file) (slurp (str file))])))

(defn stale-checkout []
  (write-agent ".gitignore" ".swarmforge/\n")
  (adapters/adapters [])
  (git *dir* "add" ".")
  (git *dir* "commit" "-q" "-m" "Generate A")
  (git *dir* "checkout" "-q" "-b" "generated-b")
  (write-conf! *dir* "tiers.conf" "frontier inherit high gpt\nstrong ultra high new-model\nfast sonnet low gpt-mini\n")
  (adapters/adapters [])
  (git *dir* "add" ".")
  (git *dir* "commit" "-q" "-m" "Generate B")
  (git *dir* "checkout" "-q" "main"))

(describe "adapter ownership"
  (around [it] (with-repo (fn [] (it))))

  (it "preserves unrelated agents in every shared directory"
    (doseq [path [".claude/agents/specialist.md" ".github/agents/specialist.agent.md" ".codex/agents/specialist.toml" ".kilo/agent/specialist.md"]]
      (write-agent path "project-owned bytes\n"))
    (adapters/adapters [])
    (doseq [file (filter #(re-find #"specialist" (str %)) (keys (snapshot)))]
      (should= "project-owned bytes\n" (slurp file)))
    (should= 16 (count (snapshot))))

  (it "generates optional reviewers through the same provider tier mappings"
    (write-conf! *dir* "roles.conf" "coder strong subagent\nsecurity-reviewer strong subagent optional\n")
    (write-conf! *dir* "routes.conf" "feature coder\n")
    (write-agent "swarmforge/roles/security-reviewer.md" "# Fixture\n\nReview fixture.\n")
    (adapters/adapters [])
    (doseq [[path model] [[".claude/agents/security-reviewer.md" "model: opus"]
                          [".github/agents/security-reviewer.agent.md" "model: gpt-5"]
                          [".codex/agents/security-reviewer.toml" "model_reasoning_effort = \"medium\""]
                          [".kilo/agent/security-reviewer.md" "model: kilo/strong-model"]]]
      (should-contain model (slurp (str (fs/path *dir* path)))))
    (let [before (snapshot)]
      (adapters/adapters [])
      (should= before (snapshot))))

  (it "generates a gate's adapters from the gate template on its tier and recognizes them without ownership state"
    (write-gate!)
    (adapters/adapters [])
    (let [body (fn [template role] (str/replace (shell/template template) "{{role}}" role))]
      (doseq [path gate-paths
              :let [text (slurp (str (fs/path *dir* path)))]]
        (should-contain (body "gate-adapter.md" "falsifier") text)
        (should-contain "Audits a specification." text))
      (should-contain (body "adapter.md" "coder") (slurp (str (fs/path *dir* (first paths))))))
    (should-contain "model: opus" (slurp (str (fs/path *dir* (first gate-paths)))))
    (let [before (snapshot)]
      (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
      (should= 0 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot))))

  (it "gives the Claude and Copilot gate adapters read-only tools, and every stage all tools"
    (write-gate!)
    (adapters/adapters [])
    (let [read #(slurp (str (fs/path *dir* %)))]
      (should-contain "\ntools: Read, Grep, Glob, Bash\n" (read ".claude/agents/falsifier.md"))
      (should-contain "\ntools: [\"read\", \"search\", \"shell\"]\n" (read ".github/agents/falsifier.agent.md"))
      (should-not-contain "tools:" (read ".claude/agents/coder.md"))
      (should-contain "\ntools: [\"*\"]\n" (read ".github/agents/coder.agent.md"))))

  (it "generates the judge's adapters from the judge template with a gate's read-only tools"
    (write-conf! *dir* "roles.conf" "specifier frontier subagent\nfalsifier strong subagent gate\njudge strong subagent judge\ncoder strong subagent\ncleaner fast subagent\n")
    (write-agent "swarmforge/roles/falsifier.md" "# falsifier\n\nAudits a specification.\n")
    (write-agent "swarmforge/roles/judge.md" "# judge\n\nRules on a round.\n")
    (should= 0 (:status (captured #(adapters/adapters []))))
    (let [read #(slurp (str (fs/path *dir* %)))
          body (str/replace (shell/template "judge-adapter.md") "{{role}}" "judge")]
      (doseq [path [".claude/agents/judge.md" ".github/agents/judge.agent.md" ".codex/agents/judge.toml" ".kilo/agent/judge.md"]]
        (should-contain body (read path))
        (should-contain "Rules on a round." (read path)))
      (should-contain "\ntools: Read, Grep, Glob, Bash\n" (read ".claude/agents/judge.md"))
      (should-contain "\ntools: [\"read\", \"search\", \"shell\"]\n" (read ".github/agents/judge.agent.md"))
      (should-contain "model: opus" (read ".claude/agents/judge.md"))))

  (it "replaces gate adapters that an older sf wrote with all tools, without ownership state"
    (write-gate!)
    (adapters/adapters [])
    (let [before (snapshot)
          claude (fs/path *dir* ".claude/agents/falsifier.md")
          copilot (fs/path *dir* ".github/agents/falsifier.agent.md")]
      (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
      (spit (str claude) (str/replace (slurp (str claude)) "tools: Read, Grep, Glob, Bash\n" ""))
      (spit (str copilot) (str/replace (slurp (str copilot)) "tools: [\"read\", \"search\", \"shell\"]" "tools: [\"*\"]"))
      (should= 0 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot))))

  (it "removes the adapters of a gate whose row is deleted, without ownership state"
    (write-gate!)
    (adapters/adapters [])
    (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
    (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder strong subagent\ncleaner fast subagent\n")
    (should= 0 (:status (captured #(adapters/adapters []))))
    (doseq [path gate-paths]
      (should-not (fs/exists? (fs/path *dir* path))))
    (should (fs/exists? (fs/path *dir* (first paths)))))

  (it "omits the Kilo model when the tier inherits the session model"
    (write-conf! *dir* "tiers.conf" "frontier inherit high gpt inherit\nstrong opus medium gpt-5 inherit\nfast sonnet low gpt-mini inherit\n")
    (adapters/adapters [])
    (doseq [role ["specifier" "coder" "cleaner"]]
      (let [text (slurp (str (fs/path *dir* ".kilo/agent" (str role ".md"))))]
        (should-contain "mode: subagent" text)
        (should-not-contain "model:" text))))

  (it "refuses an unowned collision before writing any harness"
    (write-agent ".codex/agents/coder.toml" "project-owned")
    (let [before (snapshot)
          {:keys [status err]} (captured #(adapters/adapters []))]
      (should= 1 status)
      (should-contain ".codex/agents/coder.toml" err)
      (should= before (snapshot))))

  (it "updates managed adapters and removes only obsolete managed roles"
    (adapters/adapters [])
    (write-agent ".codex/agents/specialist.toml" "custom")
    (write-conf! *dir* "roles.conf" "specifier frontier session\ncoder fast subagent\ncleaner fast subagent\n")
    (adapters/adapters [])
    (should-not (fs/exists? (fs/path *dir* ".claude/agents/specifier.md")))
    (should-contain "model: sonnet" (slurp (str (fs/path *dir* (first paths)))))
    (should= "custom" (slurp (str (fs/path *dir* ".codex/agents/specialist.toml"))))
    (let [before (snapshot)]
      (adapters/adapters [])
      (should= before (snapshot))))

  (it "refuses locally edited managed adapters without partially regenerating"
    (adapters/adapters [])
    (write-agent ".codex/agents/coder.toml" "edited project instructions")
    (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder fast subagent\ncleaner fast subagent\n")
    (let [before (snapshot)]
      (should= 1 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot))))

  (it "recognizes shipped adapters when local ownership state is absent"
    (adapters/adapters [])
    (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
    (write-conf! *dir* "roles.conf" "specifier frontier session\ncoder fast subagent\ncleaner fast subagent\n")
    (should= 0 (:status (captured #(adapters/adapters []))))
    (should-not (fs/exists? (fs/path *dir* ".codex/agents/specifier.toml")))
    (should-contain "model_reasoning_effort = \"low\"" (slurp (str (fs/path *dir* (nth paths 2))))))

  (it "refuses a Kilo adapter whose mode was edited when ownership state is absent"
    (adapters/adapters [])
    (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
    (let [path ".kilo/agent/coder.md"
          file (fs/path *dir* path)
          edited (str/replace (slurp (str file)) "mode: subagent" "mode: primary")]
      (write-agent path edited)
      (let [before (snapshot)]
        (should= 1 (:status (captured #(adapters/adapters []))))
        (should= before (snapshot))
        (should= edited (slurp (str file))))))

  (it "refreshes stale ownership after clean checkouts and fast-forward pulls"
    (stale-checkout)
    (should= "" (git *dir* "status" "--porcelain"))
    (let [before (snapshot)]
      (should= 0 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot)))
    (git *dir* "pull" "-q" "--ff-only" "." "generated-b")
    (should= "" (git *dir* "status" "--porcelain"))
    (let [before (snapshot)]
      (should= 0 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot)))
    (should= "" (git *dir* "status" "--porcelain")))

  (it "refreshes each clean adapter despite unrelated project edits"
    (stale-checkout)
    (write-agent "project.txt" "unrelated work")
    (write-conf! *dir* "tools.conf" "# unrelated configuration edit\n")
    (should= 0 (:status (captured #(adapters/adapters []))))
    (should= "unrelated work" (slurp (str (fs/path *dir* "project.txt")))))

  (it "does not adopt local model-only or body edits through stale ownership"
    (doseq [change [#(str/replace % "model_reasoning_effort = \"medium\"" "model_reasoning_effort = \"low\"")
                    #(str % "project instructions\n")]]
      (with-repo
        (fn []
          (stale-checkout)
          (let [path (nth paths 2)
                original (slurp (str (fs/path *dir* path)))]
            (write-agent path (change original))
            (let [before (snapshot)]
              (should= 1 (:status (captured #(adapters/adapters []))))
              (should= before (snapshot))))))))

  (it "refuses staged edits even when the working file equals its clean canonical version"
    (stale-checkout)
    (let [path (nth paths 2)
          canonical (slurp (str (fs/path *dir* path)))]
      (write-agent path (str/replace canonical "model_reasoning_effort = \"medium\"" "model_reasoning_effort = \"low\""))
      (git *dir* "add" path)
      (write-agent path canonical)
      (let [before (snapshot)]
        (should= 1 (:status (captured #(adapters/adapters []))))
        (should= before (snapshot)))))

  (it "does not claim an unknown tracked file when ownership is stale"
    (stale-checkout)
    (write-agent (nth paths 2) "project agent instructions")
    (git *dir* "add" (nth paths 2))
    (git *dir* "commit" "-q" "-m" "Install project agent")
    (let [before (snapshot)]
      (should= 1 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot))))

  (it "does not claim an untracked canonical-looking file through stale ownership"
    (stale-checkout)
    (let [path (nth paths 2)
          canonical (slurp (str (fs/path *dir* path)))]
      (git *dir* "rm" "-q" path)
      (git *dir* "commit" "-q" "-m" "Remove old adapter")
      (write-agent path canonical)
      (let [before (snapshot)]
        (should= 1 (:status (captured #(adapters/adapters []))))
        (should= before (snapshot)))))

  (it "rejects ownership paths outside the agent directories"
    (write-agent "unrelated.txt" "keep")
    (write-agent ".swarmforge/adapters.edn" (pr-str {"unrelated.txt" (apply str (repeat 64 "0"))}))
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should= "keep" (slurp (str (fs/path *dir* "unrelated.txt"))))
    (should= {} (snapshot)))

  (it "migrates shipped adapters even when tier model settings have changed"
    (adapters/adapters [])
    (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
    (write-conf! *dir* "tiers.conf" "frontier inherit high gpt\nstrong ultra high new-model\nfast sonnet low gpt-mini\n")
    (should= 0 (:status (captured #(adapters/adapters []))))
    (should-contain "model: ultra" (slurp (str (fs/path *dir* (first paths)))))
    (should-contain "model_reasoning_effort = \"high\"" (slurp (str (fs/path *dir* (nth paths 2))))))

  (it "preserves symbolic links at adapter targets"
    (write-agent "project-agent" "keep")
    (fs/create-dirs (fs/path *dir* ".codex/agents"))
    (fs/create-sym-link (fs/path *dir* (nth paths 2)) (fs/path *dir* "project-agent"))
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should (fs/sym-link? (fs/path *dir* (nth paths 2))))
    (should= "keep" (slurp (str (fs/path *dir* "project-agent")))))

  (it "refuses symbolic links in the shared agent directories"
    (fs/create-dirs (fs/path *dir* "custom-agents"))
    (fs/create-dirs (fs/path *dir* ".codex"))
    (fs/create-sym-link (fs/path *dir* ".codex/agents") (fs/path *dir* "custom-agents"))
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should= [] (vec (fs/list-dir (fs/path *dir* "custom-agents")))))

  (it "preflights conflicting agent directory paths before creating other adapters"
    (write-agent ".codex/agents" "project-owned file")
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should= "project-owned file" (slurp (str (fs/path *dir* ".codex/agents"))))
    (should-not (fs/exists? (fs/path *dir* ".claude/agents"))))

  (it "preserves a directory occupying an adapter target"
    (fs/create-dirs (fs/path *dir* ".claude/agents/coder.md"))
    (write-agent ".claude/agents/coder.md/project.txt" "project files")
    (let [{:keys [status err]} (captured #(adapters/adapters []))]
      (should= 1 status)
      (should-contain ".claude/agents/coder.md" err)
      (should= "project files" (slurp (str (fs/path *dir* ".claude/agents/coder.md/project.txt"))))
      (should-not (fs/exists? (fs/path *dir* ".github/agents")))))

  (it "refuses a conflicting parent directory path before generating adapters"
    (write-agent ".claude" "project-owned file")
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should= "project-owned file" (slurp (str (fs/path *dir* ".claude"))))
    (should-not (fs/exists? (fs/path *dir* ".codex/agents"))))

  (it "refuses a dangling ownership link in a worktree before generating adapters"
    (let [wt (fs/path *dir* "wt")
          state (fs/path wt ".swarmforge/adapters.edn")]
      (fs/create-dirs (fs/parent state))
      (fs/create-sym-link state (fs/path *dir* "outside.edn"))
      (with-redefs [sf.shell/checkout-root (constantly (str wt))]
        (should= 1 (:status (captured #(adapters/adapters [])))))
      (should-not (fs/exists? (fs/path *dir* "outside.edn")))
      (should-not (fs/exists? (fs/path wt ".claude")))))

  (it "does not generate through a symbolic link at the harness root"
    (fs/create-dirs (fs/path *dir* "project-claude"))
    (fs/create-sym-link (fs/path *dir* ".claude") (fs/path *dir* "project-claude"))
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should (fs/sym-link? (fs/path *dir* ".claude")))
    (should= [] (vec (fs/list-dir (fs/path *dir* "project-claude"))))
    (should-not (fs/exists? (fs/path *dir* ".codex/agents"))))

  (it "refuses modified obsolete adapters rather than removing project edits"
    (adapters/adapters [])
    (write-agent ".codex/agents/coder.toml" "edited")
    (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder strong session\ncleaner fast subagent\n")
    (let [before (snapshot)]
      (should= 1 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot)))))
