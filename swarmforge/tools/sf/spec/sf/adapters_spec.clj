(ns sf.adapters-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.adapters :as adapters]
            [sf.repo-support :refer [*dir* with-repo write-conf! captured]]))

(def paths [".claude/agents/coder.md" ".github/agents/coder.agent.md" ".codex/agents/coder.toml"])

(defn write-agent [path text]
  (let [file (fs/path *dir* path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text)))

(defn snapshot []
  (into {} (for [dir [".claude/agents" ".github/agents" ".codex/agents"]
                 file (fs/glob (fs/path *dir* dir) "*")]
             [(str file) (slurp (str file))])))

(describe "adapter ownership"
  (around [it] (with-repo (fn [] (it))))

  (it "preserves unrelated agents in every shared directory"
    (doseq [path [".claude/agents/specialist.md" ".github/agents/specialist.agent.md" ".codex/agents/specialist.toml"]]
      (write-agent path "project-owned bytes\n"))
    (adapters/adapters [])
    (doseq [file (filter #(re-find #"specialist" (str %)) (keys (snapshot)))]
      (should= "project-owned bytes\n" (slurp file)))
    (should= 12 (count (snapshot))))

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
    (should-contain "model_reasoning_effort = \"low\"" (slurp (str (fs/path *dir* (last paths))))))

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
    (should-contain "model_reasoning_effort = \"high\"" (slurp (str (fs/path *dir* (last paths))))))

  (it "preserves symbolic links at adapter targets"
    (write-agent "project-agent" "keep")
    (fs/create-dirs (fs/path *dir* ".codex/agents"))
    (fs/create-sym-link (fs/path *dir* (last paths)) (fs/path *dir* "project-agent"))
    (should= 1 (:status (captured #(adapters/adapters []))))
    (should (fs/sym-link? (fs/path *dir* (last paths))))
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

  (it "refuses modified obsolete adapters rather than removing project edits"
    (adapters/adapters [])
    (write-agent ".codex/agents/coder.toml" "edited")
    (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder strong session\ncleaner fast subagent\n")
    (let [before (snapshot)]
      (should= 1 (:status (captured #(adapters/adapters []))))
      (should= before (snapshot)))))
