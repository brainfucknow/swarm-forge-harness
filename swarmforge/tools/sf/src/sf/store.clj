(ns sf.store
  "Tasks in plain git. A task is its branch swarm/<id>; its first commit adds
  tasks/<id>.md, and every sf action is a record commit whose trailers hold the
  state. A task is finished when tasks/done/<id>.md is in the main checkout's
  HEAD. git runs in the main checkout."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.record :as record]
            [sf.shell :as shell]))

(defn- run [opts & args]
  (apply shell/run (merge {:dir (shell/repo-root)} opts) "git" args))

(defn- git-with [opts & args]
  (shell/chomp (apply shell/must (merge {:dir (shell/repo-root)} opts) "git" args)))

(defn git [& args]
  (apply git-with {} args))

(defn ref-of [id]
  (str "refs/heads/" (record/branch id)))

(defn open-path [id] (str "tasks/" id ".md"))
(defn done-path [id] (str "tasks/done/" id ".md"))

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

(defn branch-ids []
  (shell/lines (git "for-each-ref" "--format=%(refname:lstrip=3)" "refs/heads/swarm/")))

(defn done-ids []
  (let [result (run {} "ls-tree" "--name-only" "HEAD" "tasks/done/")]
    (when (shell/ok? result)
      (keep #(second (re-matches #"tasks/done/(.+)\.md" %)) (shell/lines (:out result))))))

(defn finished? [id]
  (boolean (some #{id} (done-ids))))

(defn has-branch? [id]
  (shell/ok? (run {} "rev-parse" "--verify" "--quiet" (ref-of id))))

(defn resolve-id
  "The task that arg names, among the task branches and the finished tasks."
  [arg]
  (when (str/blank? arg) (shell/die "task id required"))
  (let [[status found] (record/resolve arg (concat (branch-ids) (done-ids)))]
    (case status
      :ok found
      :ambiguous (shell/die "task id '" arg "' is ambiguous: " (str/join " " found))
      :none (shell/die "no task '" arg "'"))))

(defn new-id
  "slug-hex, with a random hex part that no task has."
  [title]
  (record/new-id title
                 (map record/hex-part (concat (branch-ids) (done-ids)))
                 (repeatedly #(format "%07x" (rand-int 0x10000000)))))

(def ^:private log-format "%h%x1f%p%x1f%B%x1f%(trailers:only,unfold)%x1e")

(defn- read-commits [output]
  (for [entry (str/split output #"\x1e")
        :let [[sha parents message trailers] (str/split (str/replace entry #"^\n" "") #"\x1f" -1)]
        :when (not (str/blank? sha))]
    {:sha sha :parents (str/split parents #" ") :message message :trailers (shell/lines trailers)}))

(defn records-at
  "The records of the task on the first-parent history of rev, oldest first."
  [rev id]
  (->> (git "log" "--first-parent" (str "--grep=^SwarmForge-Task: " id "$") (str "--format=" log-format) rev "--")
       read-commits
       (map record/parse)
       (filter #(= id (:task %)))
       reverse
       record/role-commits))

(defn with-files
  "The records with the files of each review and handoff."
  [records]
  (record/with-files records (fn [from to]
                               (when from (shell/lines (git "diff" "--name-only" from to))))))

(defn show-file
  "The text of path at rev, or nil."
  [rev path]
  (let [result (run {} "show" (str rev ":" path))]
    (when (shell/ok? result) (:out result))))

(defn task-file
  "The task file: tasks/done/<id>.md in HEAD once finished, else from the branch."
  [id]
  (record/read-task-file
   (or (if (finished? id)
         (show-file "HEAD" (done-path id))
         (or (show-file (ref-of id) (open-path id)) (show-file (ref-of id) (done-path id))))
       "")))

(defn state
  "The newest record of the task branch, or the stage and route of the archive
  when the branch is gone."
  [id]
  (if (has-branch? id)
    (or (last (records-at (ref-of id) id)) (shell/die "swarm/" id " has no records"))
    (record/archived-state (task-file id))))

(defn- tree-with
  "The tree of rev with the paths in removed taken out and files (path to
  text) added, built in a temporary index."
  [rev removed files]
  (let [common (git "rev-parse" "--path-format=absolute" "--git-common-dir")
        dir (fs/create-temp-dir {:dir common :prefix "sf-index-"})
        index {:env {"GIT_INDEX_FILE" (str (fs/path dir "index"))}}]
    (try
      (git-with index "read-tree" rev)
      (doseq [path removed] (git-with index "update-index" "--force-remove" path))
      (doseq [[path text] files]
        (let [blob (git-with {:in text} "hash-object" "-w" "--stdin")]
          (git-with index "update-index" "--add" "--cacheinfo" (str "100644," blob "," path))))
      (git-with index "write-tree")
      (finally (fs/delete-tree dir)))))

(defn- commit-tree [tree parents message]
  (apply git-with {:in message :env (author-env)} "commit-tree" tree (mapcat #(vector "-p" %) parents)))

(defn create!
  "Makes swarm/<id> at HEAD with one commit that adds the task file."
  [id text message]
  (let [head (git "rev-parse" "HEAD")
        commit (commit-tree (tree-with head [] {(open-path id) text}) [head] message)]
    (git "update-ref" (ref-of id) commit "")))

(defn write!
  "Appends the record that (make records) returns to the task branch, records
  being the branch's records, oldest first. A record with :archive moves the
  task file to tasks/done/ with that text; any other record changes no file.
  The branch moves only when its tip is still the one read; otherwise sf
  reads it again and retries once. No worktree is touched. Returns the record."
  [id make]
  (loop [attempt 1]
    (let [tip (git "rev-parse" (ref-of id))
          rec (assoc (make (records-at tip id)) :task id)
          tree (if-let [text (:archive rec)]
                 (tree-with tip [(open-path id)] {(done-path id) text})
                 (git "rev-parse" (str tip "^{tree}")))
          commit (commit-tree tree [tip] (record/message rec))
          result (run {} "update-ref" (ref-of id) commit tip)]
      (cond (shell/ok? result) rec
            (= 1 attempt) (recur 2)
            :else (shell/die (shell/chomp (:err result)))))))

(defn merge!
  "Merges the task branch into the checked-out branch with --no-ff; the merge
  commit has message."
  [id message]
  (git-with {:env (author-env)} "merge" "--no-ff" "--no-edit" "--quiet" "-m" message (record/branch id)))
