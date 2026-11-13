(ns sf.task
  "The commands that create, move, and close tasks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.audit :as audit]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.git :as plumbing]
            [sf.harvest :as harvest]
            [sf.history :as history]
            [sf.hook :as hook]
            [sf.lore :as lore]
            [sf.metrics :as metrics]
            [sf.options :as options]
            [sf.record :as record]
            [sf.rounds :as rounds]
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

(defn- operator-only!
  "Stops when a role or gate that sf dispatch runs tries the operator's command."
  [command]
  (refuse (flow/operator-refusal command (shell/env "SWARMFORGE_DISPATCHED" nil))))

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
  (operator-only! "new")
  (when (str/blank? title) (shell/die "new needs a title"))
  (let [{:keys [body route] :or {body title}}
        (options/parse "new" args {"-m" :body "-F" [:body options/read-file] "--route" :route} {})
        route (if route (config/resolve-route route) (config/default-route))
        title (record/one-line title)
        id (store/new-id title)]
    (store/create! id (record/message (assoc (flow/created title body route) :task id)))
    (println id)))

(defn route [[id-arg spec]]
  (if (str/blank? spec)
    (println (:route (store/state (store/resolve-id id-arg))))
    (let [_ (operator-only! "route")
          id (open-task id-arg)
          route (config/resolve-route spec)]
      (write! id "route" "operator"
              (fn [state] (refuse (flow/route-refusal state route)) {:route route}))
      (println (str "ROUTE: " id " " route)))))

(defn ls [args]
  (let [{:keys [stage needs]} (options/parse "ls" args {"--stage" :stage} {"--needs" :needs})]
    (doseq [id (store/open-ids)
            :let [records (store/open-records id)
                  state (last records)]
            :when (and (or (nil? stage) (= stage (:stage state)))
                       (or (not needs) (flow/waiting? state)))]
      (println (str/join "\t" [id (:stage state) (if (flow/waiting? state) (:needs state) "")
                               (:route state) (:title (store/title-and-request records))])))))

(defn next-task [[role]]
  (when (str/blank? role) (shell/die "next needs a role"))
  (config/require-role role)
  (let [ready (filter #(let [state (store/state %)]
                         (and (= role (:stage state)) (not (flow/waiting? state))))
                      (store/open-ids))]
    (if (seq ready)
      (run! println ready)
      (println "NO_TASK"))))

(defn stage [[id-arg]]
  (some-> (:stage (store/state (store/resolve-id id-arg))) println))

(defn show [[id-arg]]
  (let [id (store/resolve-id id-arg)
        {:keys [title status state] :as view} (history/view id)]
    (println (str "TASK: " id))
    (println (str "TITLE: " title))
    (println (str "STATUS: " status))
    (println (str "STAGE: " (:stage state)))
    (println (str "ROUTE: " (:route state)))
    (history/print-body view)))

(defn start [[id-arg]]
  (let [id (open-task id-arg)
        dir (add-worktree id)]
    (hook/install (shell/repo-root))
    (metrics/load! dir)
    (let [{:keys [title state] :as view} (history/view id)]
      (println (str "TASK: " id))
      (println (str "TITLE: " title))
      (println (str "STAGE: " (:stage state)))
      (println (str "ROUTE: " (:route state)))
      (println (str "BRANCH: " (record/branch id)))
      (println (str "DIR: " dir))
      (history/print-brief id view))))

(defn handoff
  "Records HEAD and moves the task on. role overrides the acting role, and
  audit-passed? skips the audit gate."
  [[id-arg & args] {:keys [role audit-passed?]}]
  (let [id (open-task id-arg)
        {:keys [to summary]} (options/handoff args)
        state (store/state id)
        _ (refuse (flow/handoff-refusal state))
        _ (refuse (flow/to-refusal state to))
        from (or role (acting-role state))
        to (flow/next-stage state to)
        _ (when (not= "done" to) (config/require-role to))
        head (role-head id)]
    (refuse (flow/handoff-refusal state head))
    (let [refusals (when-not audit-passed? (audit/gate id from head))]
      (store/write! id (fn [records]
                         (let [state (last records)
                               rec (flow/handoff state from to head summary)]
                           (refuse (flow/handoff-refusal state (role-head id)))
                           (cond-> rec refusals (assoc :audit-refusals refusals)))))
      (when refusals (audit/clear! id from)))
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
  (operator-only! "answer")
  (let [id (open-task id-arg)
        text (options/free-text args "answer needs text")
        rec (write! id "answer" "operator" (constantly {:text text :needs "none"}))]
    (println (str "ANSWERED: " id " resumes at stage:" (:stage rec)))))

(defn review [[id-arg & args]]
  (let [id (open-task id-arg)
        summary (str/trim (options/message "specification ready for review" args))
        head (role-head id)
        role (acting-role (store/state id))]
    (let [refusals (audit/gate id role head)]
      (write! id "review" role (constantly (cond-> {:text summary :needs "approval"
                                                    :reviewed (git (shell/repo-root) "rev-parse" head)}
                                             refusals (assoc :audit-refusals refusals))))
      (when refusals (audit/clear! id role)))
    (println (str "REVIEW: " id " waits for operator approval"))))

(defn- approval-fields
  "The approval record's fields: with a Lore-id and the rejected values when
  the operator approved over any findings."
  [rejected]
  (cond-> {:needs "none"}
    (seq rejected) (assoc :lore-id (lore/new-id) :rejected rejected)))

(defn approve [[id-arg & args]]
  (operator-only! "approve")
  (let [id (open-task id-arg)
        rejected (options/rejections args)
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
                      (flow/follow state "approval" "operator" (approval-fields rejected)))))
    ;; The review that asked for approval passed the audit gate at this commit.
    ;; Thus the handoff must not run the gate again.
    (handoff [id] {:role (acting-role state) :audit-passed? true})))

(defn reject [[id-arg & args]]
  (operator-only! "reject")
  (let [id (open-task id-arg)
        text (options/free-text args "reject needs a reason")
        rec (write! id "changes-requested" "operator"
                    (fn [state] (refuse (flow/approval-refusal state)) {:text text :needs "none"}))]
    (println (str "REJECTED: " id " stays at stage:" (:stage rec)))))

(defn verdict
  "Records the judge's verdict on the newest round. Continue is an automatic
  send-back that sf refuses when a limit holds."
  [[id-arg verdict & args]]
  (operator-only! "verdict")
  (let [id (open-task id-arg)
        text (record/free-text args)
        judge (config/judge)]
    (store/write! id
                  (fn [records]
                    (let [state (last records)]
                      (refuse (rounds/verdict-refusal {:state state :rounds (rounds/rounds records)
                                                       :verdict verdict :text text :judge judge}))
                      (if (= "continue" verdict)
                        (do (refuse (first (:limits (history/gate-rounds records))))
                            (flow/follow state "changes-requested" judge {:verdict verdict :text text :needs "none"}))
                        (flow/follow state "verdict" judge {:verdict verdict :text text})))))
    (println (str "VERDICT: " id " " verdict))))

(defn rounds
  "Prints the rounds of the task's specification gate, with the record texts
  under --full. Reads only."
  [[id-arg & flags]]
  (when-not (contains? #{[] ["--full"]} (vec flags))
    (shell/die "rounds takes <id> [--full]"))
  (let [{:keys [records]} (history/view (store/resolve-id id-arg))
        {found :rounds limits :limits} (history/gate-rounds records)]
    (run! println (rounds/report found (concat limits (rounds/notes found)) (= ["--full"] flags)))))

(defn- finished-message [id state target]
  (record/message (assoc (flow/finished state target) :task id)))

(defn- verified!
  "Stops when the verification of the checkout at dir fails."
  [id target dir]
  (when-let [{:keys [command exit]} (verify/check! dir)]
    (shell/die "verification failed on the merge of " (record/branch id) " into " target ": "
               command " exited " exit)))

(defn- detached!
  "Calls (f dir) with the task's worktree at dir detached at commit. Then
  returns the worktree to the branch tip with no uncommitted change, discarding
  what f created or changed there."
  [id commit f]
  (let [dir (add-worktree id)]
    (git dir "checkout" "--quiet" "--detach" commit)
    (try
      (f dir)
      (finally
        (git dir "reset" "--quiet" "--hard")
        (git dir "clean" "--quiet" "-d" "--force")
        (git dir "checkout" "--quiet" (record/branch id))))))

(defn- verify-candidate!
  "Verifies the merge of the task branch into target, built without a commit in
  the task's worktree detached at target, so target is the current side as in
  the real merge. A candidate with conflicts is not verified; the merge into
  target reports them."
  [id target]
  (detached! id (or (plumbing/rev target) (shell/die "no branch " target))
             (fn [dir]
               (let [merged (shell/run {:dir dir :env (plumbing/author-env)}
                                       "git" "merge" "--no-ff" "--no-commit" "--quiet" (record/branch id))]
                 (cond (shell/ok? merged) (verified! id target dir)
                       (seq (shell/lines (git dir "diff" "--name-only" "--diff-filter=U"))) nil
                       :else (shell/fail (:exit merged) (shell/chomp (:err merged))))))))

(defn- verify-head!
  "Verifies the main checkout's HEAD as committed, in the task's worktree
  detached there, so uncommitted changes in the main checkout count for
  nothing."
  [id target]
  (detached! id (git (shell/repo-root) "rev-parse" "HEAD") #(verified! id target %)))

(defn- merge-task!
  "Merges the task into target, or stops with the conflicted files."
  [id state target]
  (git (shell/repo-root) "checkout" "--quiet" target)
  (when-let [files (store/merge! id (finished-message id state target))]
    (shell/die "merge of " (record/branch id) " into " target " stopped on conflicts: " (str/join " " files)
               "; resolve them, commit, then run: sf finish " id)))

(defn- finish-by!
  "Does what flow/finish-action decided for the task and target: merges the
  branch, or verifies the merge already in HEAD and records the finished merge
  on top of it when HEAD lacks one, or stops."
  [id state target action]
  (case action
    :merge (do (verify-candidate! id target)
               (merge-task! id state target))
    :keep (verify-head! id target)
    :record (do (verify-head! id target)
                (store/record-merge! id (finished-message id state target)))
    :unmerged (shell/die (record/branch id) " has commits that HEAD does not contain; merge them, then run: sf finish " id)
    (shell/die "task " id " is finished")))

(defn finish
  "Verifies the merge of a done task into its target, merges it, and removes
  its worktree and branch. Refuses a worktree with uncommitted changes. Run
  again after the operator commits a conflicting merge, it completes it."
  [[id-arg & args]]
  (operator-only! "finish")
  (let [id (store/resolve-id id-arg)
        {:keys [target]} (options/parse "finish" args {"--into" :target} {})
        _ (when (store/merging?) (shell/die "a merge is in progress; commit the merge first"))
        state (store/state id)
        _ (when (not= "done" (:stage state)) (shell/die "task is at stage:" (:stage state) ", not done"))
        _ (check-worktree! id)
        root (shell/repo-root)
        dir (worktree-of id)
        current (git root "rev-parse" "--abbrev-ref" "HEAD")
        tip (store/tip id)
        action (flow/finish-action (store/finished? id) tip (boolean (and tip (store/in-head? tip))))
        target (if (= :merge action) (or (not-empty target) current) current)]
    (finish-by! id state target action)
    (when (fs/directory? dir) (git root "worktree" "remove" dir))
    (git root "branch" "-D" (record/branch id))
    (println (str "FINISHED: " id " merged into " target))))

(defn harvest
  "Prints each task sf knows as one JSON line: the finished ones first. Reads
  only; writes nothing."
  [args]
  (when (seq args) (shell/die "harvest takes no arguments"))
  (run! (comp println harvest/line) (history/harvest)))
