(ns sf.adapters-main-state-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should should-not around]]
            [sf.adapters :as adapters]
            [sf.shell :as shell]
            [sf.repo-support :refer [*dir* with-repo captured]]))

(def ^:private digest @#'sf.adapters/digest)

(describe "adapters in a worktree with no ownership state of its own"
  (around [it] (with-repo (fn [] (it))))

  (it "reads the ownership the main checkout recorded, and removes an adapter it owns"
    (let [wt (fs/path *dir* "wt")
          path ".claude/agents/ghost.md"
          text "generated once\n"]
      (fs/create-dirs (fs/path wt ".claude/agents"))
      (spit (str (fs/path wt path)) text)
      (fs/create-dirs (fs/path *dir* ".swarmforge"))
      (spit (str (fs/path *dir* ".swarmforge" "adapters.edn")) (pr-str {path (digest text)}))
      (with-redefs [shell/checkout-root (constantly (str wt))]
        (should (zero? (:status (captured #(adapters/adapters []))))))
      (should-not (fs/exists? (fs/path wt path))))))
