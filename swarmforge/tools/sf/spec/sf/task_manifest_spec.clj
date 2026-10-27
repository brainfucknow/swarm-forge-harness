(ns sf.task-manifest-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should=]]
            [sf.bug :as bug]
            [sf.shell :as shell]
            [sf.task :as task]))

(defn- on-task [dir example]
  (with-redefs [bug/bug-id identity
                bug/short-id identity
                sf.task/worktree-of (constantly dir)]
    (example)))

(describe "sf manifest"
  (it "reports a usage error for an unknown command"
    (should= "usage: sf manifest load|save|tested <id>"
             (try (task/manifest ["bogus" "x"]) (catch clojure.lang.ExceptionInfo e (ex-message e)))))

  (it "prints what the command of an existing worktree reports"
    (fs/with-temp-dir [dir {:prefix "task-manifest-spec"}]
      (shell/must {:dir (str dir)} "git" "init" "-q" "-b" "main")
      (on-task (str dir)
               #(do (should= "MANIFESTS: .metrics/ is not ignored by git; nothing loaded\n"
                             (with-out-str (task/manifest ["load" "t1"])))
                    (should= "" (with-out-str (task/manifest ["tested" "t1"])))))))

  (it "stops when the task worktree is missing"
    (on-task "/nonexistent/worktree"
             #(should= "task worktree missing; run: sf start t1"
                       (try (task/manifest ["load" "t1"]) (catch clojure.lang.ExceptionInfo e (ex-message e)))))))
