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

  (it "leaves a signed message as it is"
    (should= "Fix\n\nBy qa.\n" (commit-msg "qa" "Fix\n\nBy qa.\n")))

  (it "needs a role and a message file"
    (should-throw clojure.lang.ExceptionInfo (hook/commit-msg ["" "msg"]))
    (should-throw clojure.lang.ExceptionInfo (hook/commit-msg ["qa" ""]))))
