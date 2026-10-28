(ns sf.store
  "Tasks in plain git. A task is its branch swarm/<id>; its first commit adds
  tasks/<id>.md, and every sf action is a record commit whose trailers hold the
  state. A task is finished when tasks/done/<id>.md is in the main checkout's
  HEAD. git runs in the main checkout."
  (:require [clojure.string :as str]
            [sf.git :as git]
            [sf.record :as record]
            [sf.shell :as shell]))

(defn ref-of [id]
  (str "refs/heads/" (record/branch id)))

(defn open-path [id] (str "tasks/" id ".md"))
(defn done-path [id] (str "tasks/done/" id ".md"))

(defn branch-ids []
  (shell/lines (git/git "for-each-ref" "--format=%(refname:lstrip=3)" "refs/heads/swarm/")))

(defn done-ids []
  (let [result (git/run {} "ls-tree" "--name-only" "HEAD" "tasks/done/")]
    (when (shell/ok? result)
      (keep #(second (re-matches #"tasks/done/(.+)\.md" %)) (shell/lines (:out result))))))

(defn finished? [id]
  (boolean (some #{id} (done-ids))))

(defn tip
  "The commit at the tip of the task branch, or nil."
  [id]
  (git/rev (ref-of id)))

(defn has-branch? [id]
  (some? (tip id)))

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
  (->> (git/git "log" "--first-parent" (str "--grep=^SwarmForge-Task: " id "$") (str "--format=" log-format) rev "--")
       read-commits
       (map record/parse)
       (filter #(= id (:task %)))
       reverse
       record/role-commits))

(defn with-files
  "The records with the files of each review and handoff."
  [records]
  (record/with-files records (fn [from to]
                               (when from (shell/lines (git/git "diff" "--name-only" from to))))))

(defn show-file
  "The text of path at rev, or nil."
  [rev path]
  (let [result (git/run {} "show" (str rev ":" path))]
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

(defn create!
  "Makes swarm/<id> at HEAD with one commit that adds the task file."
  [id text message]
  (let [head (git/git "rev-parse" "HEAD")
        commit (git/commit-tree (git/tree-with head [] {(open-path id) text}) [head] message)]
    (git/git "update-ref" (ref-of id) commit "")))

(defn write!
  "Appends the record that (make records) returns to the task branch, records
  being the branch's records, oldest first. A record with :archive moves the
  task file to tasks/done/ with that text; any other record changes no file.
  The branch moves only when its tip is still the one read; otherwise sf
  reads it again and retries once. No worktree is touched. Returns the record."
  [id make]
  (let [{:keys [rec error]}
        (git/advance! (ref-of id)
                      (fn [tip]
                        (let [tip (or tip (shell/die "no branch " (record/branch id)))
                              rec (assoc (make (records-at tip id)) :task id)
                              tree (if-let [text (:archive rec)]
                                     (git/tree-with tip [(open-path id)] {(done-path id) text})
                                     (git/git "rev-parse" (str tip "^{tree}")))]
                          {:rec rec :commit (git/commit-tree tree [tip] (record/message rec))})))]
    (when error (shell/die error))
    rec))

(defn merging?
  "True while a merge is in progress in the main checkout."
  []
  (some? (git/rev "MERGE_HEAD")))

(defn merge!
  "Merges the task branch into the checked-out branch with --no-ff; the merge
  commit has message. Returns the conflicted paths when the merge stops on
  conflicts, and leaves it in progress; else nil."
  [id message]
  (let [result (git/run {:env (git/author-env) :err :inherit}
                        "merge" "--no-ff" "--no-edit" "--quiet" "-m" message (record/branch id))]
    (when-not (shell/ok? result)
      (or (seq (shell/lines (git/git "diff" "--name-only" "--diff-filter=U")))
          (shell/fail (:exit result) nil)))))

(defn head-parents
  "The parents of the main checkout's HEAD, in order."
  []
  (shell/lines (git/git "rev-parse" "HEAD^@")))

(defn reword-head!
  "Gives the main checkout's HEAD message, with the same parents and tree."
  [message]
  (let [head (git/git "rev-parse" "HEAD")
        commit (git/commit-tree (str head "^{tree}") (head-parents) message)]
    (git/git "update-ref" "HEAD" commit head)))
