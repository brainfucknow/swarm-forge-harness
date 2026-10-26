(ns sf.hook
  "The commit-msg hook that adds the `By <role>.` byline in task worktrees."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.shell :as shell]))

(def marker "# swarmforge commit-msg hook")

(defn worktree-dir []
  (shell/env "SWARMFORGE_WORKTREE_DIR" ".worktrees"))

(defn- body []
  (-> (shell/template "commit-msg")
      (str/replace "{{marker}}" marker)
      (str/replace "{{worktree-dir}}" (worktree-dir))))

(defn install
  "Writes the hook unless core.hooksPath is set or another hook is in place."
  [root]
  (let [hooks-path (shell/chomp (:out (shell/run {:dir root} "git" "config" "--get" "core.hooksPath")))
        hooks (fs/path (shell/chomp (shell/must {:dir root} "git" "rev-parse" "--path-format=absolute" "--git-common-dir")) "hooks")
        hook (fs/path hooks "commit-msg")]
    (cond
      (seq hooks-path)
      (println "HOOK: core.hooksPath is set; add the 'By <role>.' byline yourself")

      (and (fs/exists? hook) (not (str/includes? (slurp (str hook)) marker)))
      (println "HOOK: existing commit-msg hook left alone; add the 'By <role>.' byline yourself")

      :else
      (do (fs/create-dirs hooks)
          (spit (str hook) (body))
          (fs/set-posix-file-permissions hook "rwxr-xr-x")))))
