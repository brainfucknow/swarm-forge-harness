(ns sf.git
  "git object plumbing in the main checkout: commands, the commit identity,
  trees built in a temporary index, commits, and ref moves that lose to a
  concurrent writer instead of overwriting it. sf.store keeps tasks with it
  and sf.metrics keeps the manifest cache; neither touches a worktree."
  (:require [babashka.fs :as fs]
            [sf.shell :as shell]))

(defn run [opts & args]
  (apply shell/run (merge {:dir (shell/repo-root)} opts) "git" args))

(defn out
  "The stdout of a git command that must succeed, as git wrote it."
  [opts & args]
  (apply shell/must (merge {:dir (shell/repo-root)} opts) "git" args))

(defn git-with [opts & args]
  (shell/chomp (apply out opts args)))

(defn git [& args]
  (apply git-with {} args))

(defn rev
  "The commit that ref names, or nil when there is none."
  [ref]
  (let [result (run {} "rev-parse" "--verify" "--quiet" ref)]
    (when (shell/ok? result) (shell/chomp (:out result)))))

(defn author
  "git's user.name and user.email, or SWARMFORGE_USER and SWARMFORGE_EMAIL
  when git has none."
  []
  (let [config #(shell/chomp (:out (run {} "config" "--get" %)))
        [name email] (map config ["user.name" "user.email"])]
    (if (and (seq name) (seq email))
      [name email]
      (let [user (shell/env "SWARMFORGE_USER" "operator")]
        [user (shell/env "SWARMFORGE_EMAIL" (str user "@swarmforge.local"))]))))

(defn author-env []
  (let [[name email] (author)]
    {"GIT_AUTHOR_NAME" name "GIT_AUTHOR_EMAIL" email
     "GIT_COMMITTER_NAME" name "GIT_COMMITTER_EMAIL" email}))

(defn tree-with
  "The tree of rev (the empty tree when nil) with the paths in removed taken
  out and files (path to text) added, built in a temporary index."
  [rev removed files]
  (let [common (git "rev-parse" "--path-format=absolute" "--git-common-dir")
        dir (fs/create-temp-dir {:dir common :prefix "sf-index-"})
        index {:env {"GIT_INDEX_FILE" (str (fs/path dir "index"))}}]
    (try
      (when rev (git-with index "read-tree" rev))
      (doseq [path removed] (git-with index "update-index" "--force-remove" path))
      (doseq [[path text] files]
        (let [blob (git-with {:in text} "hash-object" "-w" "--stdin")]
          (git-with index "update-index" "--add" "--cacheinfo" (str "100644," blob "," path))))
      (git-with index "write-tree")
      (finally (fs/delete-tree dir)))))

(defn commit-tree [tree parents message]
  (apply git-with {:in message :env (author-env)} "commit-tree" tree (mapcat #(vector "-p" %) parents)))

(defn advance!
  "Calls (make tip), tip being the commit ref names or nil, and moves ref to
  the :commit of the map it returns while ref still names tip. When another
  writer moves ref first, it calls make again on the new tip, once. Returns
  that map, with git's :error when ref did not move; nil when make returns nil."
  [ref make]
  (loop [attempt 1]
    (let [tip (rev ref)]
      (when-let [step (make tip)]
        (let [result (run {} "update-ref" ref (:commit step) (or tip ""))]
          (cond (shell/ok? result) step
                (= 1 attempt) (recur 2)
                :else (assoc step :error (shell/chomp (:err result)))))))))
