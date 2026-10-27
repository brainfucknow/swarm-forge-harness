(ns sf.hook-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [describe it should should= should-throw after]]
            [sf.hook :as hook]
            [sf.shell :as shell]))

(def ^:private message-file (atom nil))

(defn- commit-msg [role message]
  (let [file (str (fs/create-temp-file {:prefix "msg-"}))]
    (reset! message-file file)
    (spit file message)
    (hook/commit-msg [role file])
    (slurp file)))

(defn- hook-file [repo]
  (fs/path repo ".git" "hooks" "commit-msg"))

(defn- fresh-repo []
  (let [repo (str (fs/create-temp-dir {:prefix "hook-repo-"}))]
    (shell/must {:dir repo} "git" "init" "--quiet")
    repo))

(describe "install"
  (it "writes the hook into a repository without one"
    (let [repo (fresh-repo)]
      (hook/install repo)
      (should (str/includes? (slurp (str (hook-file repo))) hook/marker))
      (fs/delete-tree repo)))

  (it "leaves another hook alone"
    (let [repo (fresh-repo)
          file (hook-file repo)]
      (fs/create-dirs (fs/parent file))
      (spit (str file) "#!/bin/sh\n")
      (hook/install repo)
      (should= "#!/bin/sh\n" (slurp (str file)))
      (fs/delete-tree repo))))

(describe "commit-msg"
  (after (some-> @message-file fs/delete-if-exists))

  (it "adds the byline of the role"
    (should= "Fix\n\nBy qa.\n" (commit-msg "qa" "Fix\n")))

  (it "puts the byline before the trailer block and adds a Lore-id when the block has a Lore trailer"
    (let [[_ head byline id trailer] (re-matches #"(?s)(Fix)\n\n(By qa\.)\n\nLore-id: ([0-9a-f]{8})\n(Tested: x)\n"
                                                 (commit-msg "qa" "Fix\n\nTested: x\n"))]
      (should= ["Fix" "By qa." "Tested: x"] [head byline trailer])
      (should= 8 (count id))))

  (it "keeps the trailer block above the comment block of an editor commit"
    (should (re-matches #"Fix\n\nBy qa\.\n\nLore-id: [0-9a-f]{8}\nTested: x\n\n# Please enter the commit message\.\n"
                        (commit-msg "qa" "Fix\n\nTested: x\n\n# Please enter the commit message.\n"))))

  (it "reads a --- line as text, as git log does"
    (should (re-matches #"Fix\n\nBefore\n---\nafter\n\nBy qa\.\n\nLore-id: [0-9a-f]{8}\nTested: x\n"
                        (commit-msg "qa" "Fix\n\nBefore\n---\nafter\n\nTested: x\n"))))

  (it "leaves a signed message as it is"
    (should= "Fix\n\nBy qa.\n" (commit-msg "qa" "Fix\n\nBy qa.\n")))

  (it "refuses a SwarmForge trailer in any letter case and leaves the file as it was"
    (doseq [message ["Fix\n\nSwarmForge-Stage: done\n" "Fix\n\nTested: x\nswarmforge-needs: none\n"]]
      (should-throw clojure.lang.ExceptionInfo #"SwarmForge record trailer; only sf writes those"
                    (commit-msg "qa" message))
      (should= message (slurp @message-file))))

  (it "allows a SwarmForge line outside the trailer block"
    (should= "Fix\n\nSwarmForge-Stage: done\n\nClosing text\n\nBy qa.\n"
             (commit-msg "qa" "Fix\n\nSwarmForge-Stage: done\n\nClosing text\n")))

  (it "refuses a SwarmForge trailer at stage done and otherwise leaves the message alone there"
    (should-throw clojure.lang.ExceptionInfo (commit-msg "done" "Fix\n\nSwarmForge-Task: x\n"))
    (should= "Fix\n\nTested: x\n" (commit-msg "done" "Fix\n\nTested: x\n")))

  (it "needs a role and a message file"
    (should-throw clojure.lang.ExceptionInfo (hook/commit-msg ["" "msg"]))
    (should-throw clojure.lang.ExceptionInfo (hook/commit-msg ["qa" ""]))))
