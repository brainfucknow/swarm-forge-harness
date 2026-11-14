(ns sf.store
  "Tasks in plain git. A task is its local branch swarm/<id>: its first commit
  is the created record, which holds the title and the request, and every sf
  action is a record commit whose trailers hold the state. A task is finished
  when the history of the main checkout's HEAD, any parent included, has its
  finished record: the merge of sf finish. git runs in the main checkout."
  (:require [clojure.string :as str]
            [sf.git :as git]
            [sf.record :as record]
            [sf.shell :as shell]))

(defn ref-of [id]
  (str "refs/heads/" (record/branch id)))

(defn branch-ids []
  (shell/lines (git/git "for-each-ref" "--format=%(refname:lstrip=3)" "refs/heads/swarm/")))

(def ^:private log-format
  "With git log -z, six NUL-ended fields per commit. Neither an author name
  nor a commit message can hold NUL, so no field can end early."
  "%h%x00%p%x00%at%x00%an%x00%B%x00%(trailers:only,unfold)")

(defn- read-commits [output]
  (for [[sha parents author-time author-name message trailers] (partition 6 (str/split output #"\x00" -1))]
    {:sha sha :parents (str/split parents #" ") :author-time author-time :author-name author-name
     :message message :trailers (shell/lines trailers)}))

(defn- finished-records
  "The finished records in the history of the main checkout's HEAD, any parent
  included, newest first; only those of task id when given. A finished record
  is a merge, so a squash commit that repeats its message is not one."
  [& [id]]
  (let [result (apply git/run {} "log" "-z" "--merges" "--all-match" "--grep=^SwarmForge-Record: finished$"
                      (concat (when id [(str "--grep=^SwarmForge-Task: " id "$")])
                              [(str "--format=" log-format) "HEAD" "--"]))]
    (when (shell/ok? result)
      (->> (read-commits (:out result))
           (map record/parse)
           (filter #(and (= "finished" (:record %)) (or (nil? id) (= id (:task %)))))))))

(defn finished-tasks
  "The newest finished record of each task in HEAD's history, the task whose
  record is the oldest first."
  []
  (let [newest-first (finished-records)]
    (reverse (for [id (distinct (map :task newest-first))]
               (first (filter #(= id (:task %)) newest-first))))))

(defn finished-ids []
  (distinct (map :task (finished-records))))

(defn finished-record
  "The finished record of the task in HEAD's history, or nil."
  [id]
  (first (finished-records id)))

(defn finished? [id]
  (some? (finished-record id)))

(defn open-ids
  "The ids of the task branches whose task is not finished, sorted."
  []
  (sort (remove (set (finished-ids)) (branch-ids))))

(defn tip
  "The commit at the tip of the task branch, or nil."
  [id]
  (git/rev (ref-of id)))

(defn has-branch? [id]
  (some? (tip id)))

(defn unclosed-tasks
  "The newest reachable record per task without a branch or finished record,
  sorted by id. These histories have no lifecycle closure."
  []
  (let [known (set (concat (branch-ids) (finished-ids)))
        records (->> (git/git "log" "-z" "--topo-order" "--grep=^SwarmForge-Task: "
                             (str "--format=" log-format) "HEAD" "--")
                     read-commits
                     (map record/parse)
                     (filter record/recognized?))]
    (->> records
         (remove #(known (:task %)))
         (group-by :task)
         (sort-by key)
         (map (comp first val)))))

(defn unclosed-record [id]
  (first (filter #(= id (:task %)) (unclosed-tasks))))

(defn refuse-unclosed!
  "Refuses execution of branchless histories; callers retain finished-task behavior."
  [id]
  (when (and (not (has-branch? id)) (not (finished? id)))
    (shell/die "task " id " has no local branch; history is unclosed")))

(defn resolve-id
  "The task that arg names, among branches, finished tasks and reachable unclosed histories."
  [arg]
  (when (str/blank? arg) (shell/die "task id required"))
  (let [known (concat (branch-ids) (finished-ids))
        ids (if (some #{arg} known) known (concat known (map :task (unclosed-tasks))))
        [status found] (record/resolve arg ids)]
    (case status
      :ok found
      :ambiguous (shell/die "task id '" arg "' is ambiguous: " (str/join " " found))
      :none (shell/die "no task '" arg "'"))))

(defn new-id
  "slug-hex, with a random hex part that no task has."
  [title]
  (record/new-id title
                 (map record/hex-part (concat (branch-ids) (finished-ids) (map :task (unclosed-tasks))))
                 (repeatedly #(format "%07x" (rand-int 0x10000000)))))

(defn records-at
  "The records of the task on the first-parent history of rev, oldest first."
  [rev id]
  (->> (git/git "log" "-z" "--first-parent" (str "--grep=^SwarmForge-Task: " id "$") (str "--format=" log-format) rev "--")
       read-commits
       (map record/parse)
       (filter record/recognized?)
       (filter #(= id (:task %)))
       reverse
       record/role-commits))

(defn trailers-between
  "The trailer lines of each commit after from up to to, oldest first, without
  the record commits."
  [from to]
  (->> (git/git "log" "-z" "--reverse" (str "--format=" log-format) (str from ".." to) "--")
       read-commits
       (map :trailers)
       (remove (fn [trailers] (some #(str/starts-with? % "SwarmForge-Task:") trailers)))))

(defn- range-files
  "The files that from..to changed, without what a merge in the range brought
  in: only the files of the range's first-parent non-merge commits."
  [from to]
  (let [changed (shell/lines (git/git "diff" "--name-only" "--no-renames" from to))
        own (set (shell/lines (git/git "log" "--first-parent" "--no-merges" "--no-renames" "--name-only" "--format=" (str from ".." to))))]
    (filter own changed)))

(defn with-files
  "The records with the files of each review and handoff."
  [records]
  (record/with-files records (fn [from to]
                               (when from (range-files from to)))))

(defn- show-file
  "The text of path at rev."
  [rev path]
  (git/out {} "show" (str rev ":" path)))

(defn title-and-request
  "The :title and :request of the task whose records these are: from the free
  text of its created record, or from the task file that the created commit
  of a task made before sf kept them there adds."
  [records]
  (let [{:keys [sha text]} (first (filter #(= "created" (:record %)) records))
        file (when sha (first (shell/lines (git/git "diff-tree" "--no-commit-id" "--name-only" "-r" sha))))]
    (if file
      (record/read-task-file (show-file sha file))
      (record/title-and-request text))))

(defn open-records
  "The records of the task branch, oldest first; stops when it has none."
  [id]
  (or (not-empty (records-at (ref-of id) id)) (shell/die "swarm/" id " has no records")))

(defn state
  "The newest branch record, finished record, or reachable unclosed record."
  [id]
  (if (has-branch? id)
    (last (open-records id))
    (or (finished-record id) (unclosed-record id))))

(defn recording-backend
  "The backend that a record sf writes now carries, from the environment of
  this sf command: :backend and :dispatched, or nil."
  []
  (record/backend (shell/env "SWARMFORGE_DISPATCHED" nil) (shell/env "SWARMFORGE_BACKEND" nil)
                (shell/env "CLAUDECODE" nil)))

(defn create!
  "Makes swarm/<id> at HEAD with one commit that changes no file."
  [id message]
  (let [head (git/git "rev-parse" "HEAD")
        commit (git/commit-tree (str head "^{tree}") [head] message)]
    (git/git "update-ref" (ref-of id) commit "")))

(defn- task-files
  "The paths of the files named <id>.md on rev: the task file of a task made
  before sf kept it in the created record."
  [rev id]
  (let [name (str id ".md")]
    (filter #(or (= name %) (str/ends-with? % (str "/" name)))
            (shell/lines (git/git "ls-tree" "-r" "--name-only" rev)))))

(defn- tree-for
  "The tree of the record after tip: tip's tree, without the task files once
  the record is at done."
  [tip {:keys [stage task]}]
  (let [stale (when (= "done" stage) (task-files tip task))]
    (if (seq stale)
      (git/tree-with tip stale {})
      (git/git "rev-parse" (str tip "^{tree}")))))

(defn write!
  "Appends the record that (make records) returns to the task branch, records
  being the branch's records, oldest first. The record carries the recording
  backend unless it holds :backend and :dispatched itself. It changes no
  file, except that a record at done removes the task file of an older task.
  The branch moves only when its tip is still the one read; otherwise sf
  reads it again and retries once. No worktree is touched. Returns the record."
  [id make]
  (let [{:keys [rec error]}
        (git/advance! (ref-of id)
                      (fn [tip]
                        (let [tip (or tip (shell/die "no branch " (record/branch id)))
                              rec (assoc (merge (recording-backend) (make (records-at tip id))) :task id)]
                          {:rec rec :commit (git/commit-tree (tree-for tip rec) [tip] (record/message rec))})))]
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

(defn in-head?
  "True when the main checkout's HEAD contains commit."
  [commit]
  (shell/ok? (git/run {} "merge-base" "--is-ancestor" commit "HEAD")))

(defn record-merge!
  "Commits message on the main checkout's HEAD as the merge of the task
  branch, which HEAD already contains: its parents are HEAD and the branch tip,
  its tree is HEAD's. No commit is rewritten, so a HEAD that a host merged and
  others pulled stays theirs too."
  [id message]
  (let [head (git/git "rev-parse" "HEAD")
        commit (git/commit-tree (str head "^{tree}") [head (tip id)] message)]
    (git/git "update-ref" "HEAD" commit head)))
