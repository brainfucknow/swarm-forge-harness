(ns sf.task
  "The commands that create, move, and close tasks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.audit :as audit]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.history :as history]
            [sf.hook :as hook]
            [sf.metrics :as metrics]
            [sf.options :as options]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]))

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

(defn- role-head
  "The role's commit at the tip of the task branch, below any records. When
  the task has a worktree, it must be on the task branch and have no
  uncommitted changes."
  [id]
  (let [dir (worktree-of id)]
    (when (fs/directory? dir)
      (when (seq (git dir "status" "--porcelain"))
        (shell/die "uncommitted changes in " dir "; commit first"))
      (let [branch (git dir "rev-parse" "--abbrev-ref" "HEAD")]
        (when (not= branch (record/branch id))
          (shell/die "worktree is on '" branch "', expected '" (record/branch id) "'"))))
    (flow/role-commit (git (shell/repo-root) "rev-parse" "--short" (store/ref-of id))
                      (last (store/records-at (store/ref-of id) id)))))

(defn init [_]
  (shell/quietly config/check)
  (let [[name email] (store/author)]
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
        dir (worktree-of id)
        root (shell/repo-root)]
    (when-not (fs/directory? dir)
      (git root "worktree" "add" "--quiet" dir (record/branch id)))
    (hook/install root)
    (metrics/load! dir)
    (let [{:keys [file state] :as view} (history/view id)]
      (println (str "TASK: " id))
      (println (str "TITLE: " (:title file)))
      (println (str "STAGE: " (:stage state)))
      (println (str "ROUTE: " (:route state)))
      (println (str "BRANCH: " (record/branch id)))
      (println (str "DIR: " dir))
      (history/print-body view))))

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
    (when-not audit-passed? (audit/gate id from head))
    (store/write! id (fn [records]
                       (let [state (last records)
                             rec (flow/handoff state from to head summary)]
                         (refuse (flow/handoff-refusal state))
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
    (role-head id)
    (write! id "approval" "operator" (fn [state] (refuse (flow/approval-refusal state)) {:needs "none"}))
    ;; The review that asked for approval passed the audit gate at this commit.
    ;; Thus the handoff must not run the gate again.
    (handoff [id] {:role (acting-role state) :audit-passed? true})))

(defn reject [[id-arg & args]]
  (let [id (open-task id-arg)
        text (options/free-text args "reject needs a reason")
        rec (write! id "changes-requested" "operator"
                    (fn [state] (refuse (flow/approval-refusal state)) {:text text :needs "none"}))]
    (println (str "REJECTED: " id " stays at stage:" (:stage rec)))))

(defn finish [[id-arg & args]]
  (let [id (open-task id-arg)
        {:keys [target]} (options/parse "finish" args {"--into" :target} {})
        state (store/state id)
        _ (when (not= "done" (:stage state)) (shell/die "task is at stage:" (:stage state) ", not done"))
        root (shell/repo-root)
        dir (worktree-of id)
        target (or (not-empty target) (git root "rev-parse" "--abbrev-ref" "HEAD"))]
    (git root "checkout" "--quiet" target)
    (store/merge! id (record/message (assoc (flow/finished state target) :task id)))
    (when (fs/directory? dir) (git root "worktree" "remove" "--force" dir))
    (git root "branch" "-D" (record/branch id))
    (println (str "FINISHED: " id " merged into " target))))
