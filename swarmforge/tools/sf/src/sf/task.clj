(ns sf.task
  "The commands that create, move, and close tasks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.audit :as audit]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.git :as plumbing]
            [sf.history :as history]
            [sf.hook :as hook]
            [sf.metrics :as metrics]
            [sf.options :as options]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.verify :as verify]))

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" args)))

(defn- worktree-of [id]
  (str (shell/repo-root) "/" (hook/worktree-dir) "/" id))

(defn- acting-role [state]
  (or (shell/env "SWARMFORGE_ROLE" nil) (:stage state)))

(defn- refuse
  "Stops with the refusal, when there is one."
  [refusal]
  (when refusal (shell/die refusal)))

(defn- open-task
  "The task that arg names; stops when it is finished."
  [arg]
  (let [id (store/resolve-id arg)]
    (when (store/finished? id) (shell/die "task " id " is finished"))
    id))

(defn- write!
  "Appends the record that follows the current state: type, from (a value, or
  a function of the state), and the fields that (change state) returns."
  [id type from change]
  (store/write! id (fn [records]
                     (let [state (last records)]
                       (flow/follow state type (if (fn? from) (from state) from) (change state))))))

(defn- require-task-branch [dir id]
  (let [branch (git dir "rev-parse" "--abbrev-ref" "HEAD")]
    (when (not= branch (record/branch id))
      (shell/die "worktree is on '" branch "', expected '" (record/branch id) "'"))))

(defn- add-worktree
  "The task worktree, added on the task branch when missing."
  [id]
  (let [dir (worktree-of id)]
    (when-not (fs/directory? dir)
      (git (shell/repo-root) "worktree" "add" "--quiet" dir (record/branch id)))
    dir))

(defn worktree!
  "The task worktree, added when missing; stops when it is on another branch."
  [id]
  (doto (add-worktree id) (require-task-branch id)))

(defn- check-worktree!
  "Stops unless the task's worktree, when it has one, is on the task branch
  with no uncommitted changes."
  [id]
  (let [dir (worktree-of id)]
    (when (fs/directory? dir)
      (when (seq (git dir "status" "--porcelain"))
        (shell/die "uncommitted changes in " dir "; commit first"))
      (require-task-branch dir id))))

(defn- role-head
  "The role's commit at the tip of the task branch, below any records. When
  the task has a worktree, it must be on the task branch and have no
  uncommitted changes."
  [id]
  (check-worktree! id)
  (flow/role-commit (git (shell/repo-root) "rev-parse" "--short" (store/ref-of id))
                    (last (store/records-at (store/ref-of id) id))))

(defn init [_]
  (shell/quietly config/check)
  (let [[name email] (plumbing/author)]
    (println (str "identity: " name " <" email ">")))
  (let [root (shell/repo-root)]
    (fs/create-dirs (str root "/" (hook/worktree-dir)))
    (hook/install root))
  (println (str "roles: " (str/join " " (config/pipeline))))
  (println (str "routes: " (str/join " " (map first (config/named-routes))))))

(defn new-task [[title & args]]
  (when (str/blank? title) (shell/die "new needs a title"))
  (let [{:keys [body route] :or {body title}}
        (options/parse "new" args {"-m" :body "-F" [:body options/read-file] "--route" :route} {})
        route (if route (config/resolve-route route) (config/default-route))
        title (str/trim title)
        id (store/new-id title)]
    (store/create! id
                   (record/task-file {:id id :title title :request body
                                      :date (str (java.time.LocalDate/now java.time.ZoneOffset/UTC))})
                   (record/message (assoc (flow/created title route) :task id)))
    (println id)))

(defn route [[id-arg spec]]
  (if (str/blank? spec)
    (println (:route (store/state (store/resolve-id id-arg))))
    (let [id (open-task id-arg)
          route (config/resolve-route spec)]
      (write! id "route" "operator"
              (fn [state] (refuse (flow/route-refusal state route)) {:route route}))
      (println (str "ROUTE: " id " " route)))))

(defn- open-ids []
  (sort (remove (set (store/done-ids)) (store/branch-ids))))

(defn ls [args]
  (let [{:keys [stage needs]} (options/parse "ls" args {"--stage" :stage} {"--needs" :needs})]
    (doseq [id (open-ids)
            :let [state (store/state id)]
            :when (and (or (nil? stage) (= stage (:stage state)))
                       (or (not needs) (flow/waiting? state)))]
      (println (str/join "\t" [id (:stage state) (if (flow/waiting? state) (:needs state) "")
                               (:route state) (:title (store/task-file id))])))))

(defn next-task [[role]]
  (when (str/blank? role) (shell/die "next needs a role"))
  (config/require-role role)
  (let [ready (filter #(let [state (store/state %)]
                         (and (= role (:stage state)) (not (flow/waiting? state))))
                      (open-ids))]
    (if (seq ready)
      (run! println ready)
      (println "NO_TASK"))))

(defn stage [[id-arg]]
  (some-> (:stage (store/state (store/resolve-id id-arg))) println))

(defn show [[id-arg]]
  (let [id (store/resolve-id id-arg)
        {:keys [file status state] :as view} (history/view id)]
    (println (str "TASK: " id))
    (println (str "TITLE: " (:title file)))
    (println (str "STATUS: " status))
    (println (str "STAGE: " (:stage state)))
    (println (str "ROUTE: " (:route state)))
    (history/print-body view)))

(defn start [[id-arg]]
  (let [id (open-task id-arg)
        dir (add-worktree id)]
    (hook/install (shell/repo-root))
    (metrics/load! dir)
    (let [{:keys [file state] :as view} (history/view id)]
      (println (str "TASK: " id))
      (println (str "TITLE: " (:title file)))
      (println (str "STAGE: " (:stage state)))
      (println (str "ROUTE: " (:route state)))
      (println (str "BRANCH: " (record/branch id)))
      (println (str "DIR: " dir))
      (history/print-brief id view))))

(defn- archive
  "The task file moved to done, with every record and the handoff rec."
  [id records rec]
  (record/archive (store/show-file (store/ref-of id) (store/open-path id))
                  (:route rec)
                  (map record/block (store/with-files (concat records [(assoc rec :task id)])))))

(defn handoff
  "Records HEAD and moves the task on. role overrides the acting role, and
  audit-passed? skips the audit gate."
  [[id-arg & args] {:keys [role audit-passed?]}]
  (let [id (open-task id-arg)
        {:keys [to summary]} (options/handoff args)
        state (store/state id)
        _ (refuse (flow/handoff-refusal state))
        from (or role (acting-role state))
        to (flow/next-stage state to)
        _ (when (not= "done" to) (config/require-role to))
        head (role-head id)]
    (refuse (flow/handoff-refusal state head))
    (when-not audit-passed? (audit/gate id from head))
    (store/write! id (fn [records]
                       (let [state (last records)
                             rec (flow/handoff state from to head summary)]
                         (refuse (flow/handoff-refusal state (role-head id)))
                         (cond-> rec (= "done" to) (assoc :archive (archive id records rec))))))
    (metrics/save! id (worktree-of id))
    (when (and (= "done" to) (fs/directory? (worktree-of id)))
      (git (worktree-of id) "reset" "--quiet" "--hard"))
    (println (str "HANDOFF: " id " " from " -> " to " at " head))))

(defn ask [[id-arg & args]]
  (let [id (open-task id-arg)
        text (options/free-text args "ask needs a question")]
    (write! id "question" acting-role (constantly {:text text :needs "operator"}))
    (println (str "ASKED: " id " waits for the operator"))))

(defn answer [[id-arg & args]]
  (let [id (open-task id-arg)
        text (options/free-text args "answer needs text")
        rec (write! id "answer" "operator" (constantly {:text text :needs "none"}))]
    (println (str "ANSWERED: " id " resumes at stage:" (:stage rec)))))

(defn review [[id-arg & args]]
  (let [id (open-task id-arg)
        summary (str/trim (options/message "specification ready for review" args))
        head (role-head id)
        role (acting-role (store/state id))]
    (audit/gate id role head)
    (write! id "review" role (constantly {:text summary :needs "approval"}))
    (println (str "REVIEW: " id " waits for operator approval"))))

(defn approve [[id-arg]]
  (let [id (open-task id-arg)
        state (store/state id)
        to (flow/next-stage state nil)]
    (refuse (flow/approval-refusal state))
    ;; The handoff's own checks run before the approval is recorded, so a
    ;; handoff that cannot happen leaves the task waiting for approval.
    (when (not= "done" to) (config/require-role to))
    (store/write! id
                  (fn [records]
                    (let [state (last records)]
                      (refuse (flow/approval-refusal state (flow/reviewed-commit records) (role-head id)))
                      (flow/follow state "approval" "operator" {:needs "none"}))))
    ;; The review that asked for approval passed the audit gate at this commit.
    ;; Thus the handoff must not run the gate again.
    (handoff [id] {:role (acting-role state) :audit-passed? true})))

(defn reject [[id-arg & args]]
  (let [id (open-task id-arg)
        text (options/free-text args "reject needs a reason")
        rec (write! id "changes-requested" "operator"
                    (fn [state] (refuse (flow/approval-refusal state)) {:text text :needs "none"}))]
    (println (str "REJECTED: " id " stays at stage:" (:stage rec)))))

(defn- finished-message [id state target]
  (record/message (assoc (flow/finished state target) :task id)))

(defn- verified!
  "Stops when the verification of the checkout at dir fails."
  [id target dir]
  (when-let [{:keys [command exit]} (verify/check! dir)]
    (shell/die "verification failed on the merge of " (record/branch id) " into " target ": "
               command " exited " exit)))

(defn- verify-candidate!
  "Verifies the merge of the task branch into target, built without a commit in
  the task's worktree detached at target, so target is the current side as in
  the real merge. Then returns the worktree to the branch tip. A candidate with
  conflicts is not verified; the merge into target reports them."
  [id target]
  (let [dir (add-worktree id)
        commit (or (plumbing/rev target) (shell/die "no branch " target))
        _ (git dir "checkout" "--quiet" "--detach" commit)
        merged (shell/run {:dir dir :env (plumbing/author-env)}
                          "git" "merge" "--no-ff" "--no-commit" "--quiet" (record/branch id))]
    (try
      (cond (shell/ok? merged) (verified! id target dir)
            (seq (shell/lines (git dir "diff" "--name-only" "--diff-filter=U"))) nil
            :else (shell/fail (:exit merged) (shell/chomp (:err merged))))
      (finally
        (git dir "reset" "--quiet" "--hard")
        (git dir "clean" "--quiet" "-d" "--force")
        (git dir "checkout" "--quiet" (record/branch id))))))

(defn- merge-task!
  "Merges the task into target, or stops with the conflicted files."
  [id state target]
  (git (shell/repo-root) "checkout" "--quiet" target)
  (when-let [files (store/merge! id (finished-message id state target))]
    (shell/die "merge of " (record/branch id) " into " target " stopped on conflicts: " (str/join " " files)
               "; resolve them, commit, then run: sf finish " id)))

(defn- resume!
  "Verifies the merge the operator committed into target and gives it the
  finished record when it lacks it, or stops when the task is finished."
  [id state target]
  (let [tip (store/tip id)
        action (flow/resume-action (some? (flow/finished-record (store/records-at "HEAD" id)))
                                   tip (store/head-parents) (boolean (and tip (store/in-head? tip))))]
    (case action
      (:keep :reword) (verified! id target (shell/repo-root))
      :unmerged (shell/die (record/branch id) " has commits that HEAD does not contain; merge them, then run: sf finish " id)
      (shell/die "task " id " is finished"))
    (when (= :reword action)
      (store/reword-head! (finished-message id state target)))))

(defn finish
  "Verifies the merge of a done task into its target, merges it, and removes
  its worktree and branch. Refuses a worktree with uncommitted changes. Run
  again after the operator commits a conflicting merge, it completes it."
  [[id-arg & args]]
  (let [id (store/resolve-id id-arg)
        {:keys [target]} (options/parse "finish" args {"--into" :target} {})
        _ (when (store/merging?) (shell/die "a merge is in progress; commit the merge first"))
        state (store/state id)
        _ (when (not= "done" (:stage state)) (shell/die "task is at stage:" (:stage state) ", not done"))
        _ (check-worktree! id)
        root (shell/repo-root)
        dir (worktree-of id)
        current (git root "rev-parse" "--abbrev-ref" "HEAD")
        resuming? (store/finished? id)
        target (if resuming? current (or (not-empty target) current))]
    (if resuming?
      (resume! id state target)
      (do (verify-candidate! id target)
          (merge-task! id state target)))
    (when (fs/directory? dir) (git root "worktree" "remove" dir))
    (git root "branch" "-D" (record/branch id))
    (println (str "FINISHED: " id " merged into " target))))
