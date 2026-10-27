(ns sf.task
  "The commands that create, move, and close tasks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.hook :as hook]
            [sf.lore :as lore]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]))

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" args)))

(defn- worktree-of [id]
  (str (shell/repo-root) "/" (hook/worktree-dir) "/" id))

(defn- acting-role [state]
  (or (shell/env "SWARMFORGE_ROLE" nil) (:stage state)))

(defn- open-task
  "The task that arg names; stops when it is finished."
  [arg]
  (let [id (store/resolve-id arg)]
    (when (store/finished? id) (shell/die "task " id " is finished"))
    id))

(defn- parse-options
  "Reads flags into a map. values maps a flag that takes a value to its key, or
  to [key transform]; switches maps a flag without a value to its key."
  [command args values switches]
  (loop [opts {} [flag value :as args] args]
    (cond
      (empty? args) opts
      (switches flag) (recur (assoc opts (switches flag) true) (rest args))
      (values flag) (let [[key transform] (if (vector? (values flag)) (values flag) [(values flag) identity])]
                      (when (< (count args) 2)
                        (shell/die flag " needs " (if (= flag "-F") "a file" "a value")))
                      (recur (assoc opts key (transform value)) (drop 2 args)))
      :else (shell/die "unknown " command " option '" flag "'"))))

(defn- read-file [path]
  (shell/chomp (slurp path)))

(defn- free-text
  "The arguments as one text; stops with complaint when there are none."
  [args complaint]
  (let [text (record/free-text args)]
    (when (str/blank? text) (shell/die complaint))
    text))

(defn- write!
  "Appends a record to the task branch: the snapshot of the current state,
  with the record type, from (a value, or a function of the state), and the
  fields that (change state) returns."
  [id type from change]
  (store/write! id (fn [records]
                     (let [state (last records)]
                       (merge (select-keys state [:stage :route :needs])
                              {:record type :from (if (fn? from) (from state) from)}
                              (change state))))))

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
    (let [tip (git (shell/repo-root) "rev-parse" "--short" (store/ref-of id))
          newest (last (store/records-at (store/ref-of id) id))]
      (if (= tip (:sha newest)) (:commit newest) tip))))

(defn- last-recorded-commit [id]
  (:commit (last (filter #(#{"created" "review" "handoff"} (:record %))
                         (store/records-at (store/ref-of id) id)))))

(defn- audit-gate
  "The `audit` flag in roles.conf. Stops with exit 3 when it refuses the attempt."
  [id role head]
  (when (config/role-audits? role)
    (let [file (fs/path (shell/repo-root) ".swarmforge" "audit" id)]
      (if (and (fs/exists? file) (= head (read-file (str file))))
        (fs/delete file)
        (do (fs/create-dirs (fs/parent file))
            (spit (str file) (str head "\n"))
            (println (str "AUDIT_REQUIRED: " id " at " head "; nothing was recorded
Before running this command again:
- Re-read the task as sf start prints it: the request, every answer, and the record you received.
- Check every requirement and constraint against the committed work, including boundaries, failure cases, and what must not happen.
- For each requirement, name the test or check that covers it, or call it a gap. Passing tools alone do not make the task complete.
- Read the whole diff since the previous record (git diff " (last-recorded-commit id) "..HEAD), the tests, and anything generated or left in the working tree.
- Fix every finding, commit, rerun the checks, then run this command again. It records the handoff when HEAD is still " head "; a new commit starts a new audit."))
            (shell/fail 3 nil))))))

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
        (parse-options "new" args {"-m" :body "-F" [:body read-file] "--route" :route} {})
        route (if route (config/resolve-route route) (config/default-route))
        title (str/trim title)
        id (store/new-id title)]
    (store/create! id
                   (record/task-file {:id id :title title :request body
                                      :date (str (java.time.LocalDate/now java.time.ZoneOffset/UTC))})
                   (record/message {:task id :record "created" :from "operator" :text title
                                    :stage (first (config/route-list route)) :route route :needs "none"}))
    (println id)))

(defn route [[id-arg spec]]
  (if (str/blank? spec)
    (println (:route (store/state (store/resolve-id id-arg))))
    (let [id (open-task id-arg)
          route (config/resolve-route spec)]
      (write! id "route" "operator"
              (fn [{:keys [stage]}]
                (when-not (or (= "done" stage) (some #{stage} (config/route-list route)))
                  (shell/die "route '" route "' does not contain the current stage '" stage "'"))
                {:route route}))
      (println (str "ROUTE: " id " " route)))))

(defn- open-ids []
  (sort (remove (set (store/done-ids)) (store/branch-ids))))

(defn- waiting? [{:keys [needs]}]
  (not= "none" needs))

(defn ls [args]
  (let [{:keys [stage needs]} (parse-options "ls" args {"--stage" :stage} {"--needs" :needs})]
    (doseq [id (open-ids)
            :let [state (store/state id)]
            :when (and (or (nil? stage) (= stage (:stage state)))
                       (or (not needs) (waiting? state)))]
      (println (str/join "\t" [id (:stage state) (if (waiting? state) (:needs state) "")
                               (:route state) (:title (store/task-file id))])))))

(defn next-task [[role]]
  (when (str/blank? role) (shell/die "next needs a role"))
  (config/require-role role)
  (let [ready (filter #(let [state (store/state %)]
                         (and (= role (:stage state)) (not (waiting? state))))
                      (open-ids))]
    (if (seq ready)
      (run! println ready)
      (println "NO_TASK"))))

(defn stage [[id-arg]]
  (some-> (:stage (store/state (store/resolve-id id-arg))) println))

(defn- lore-lines
  "The LORE: lines of the first-parent commits of rev after the created record."
  [rev records]
  (when-let [base (:commit (first (filter #(= "created" (:record %)) records)))]
    (lore/lines (lore/read-log (shell/must {:dir (shell/repo-root)} "git" "log" "--reverse" "--first-parent"
                                           (str "--format=" lore/log-format) (str base ".." rev))))))

(defn- branch-history [rev id]
  (let [records (store/records-at rev id)]
    {:records (store/with-files records) :lore (lore-lines rev records)}))

(defn- finished-history
  "From the merge of sf finish when HEAD has it, else from the archive, with
  the LORE: lines of the branch while it exists."
  [id file]
  (if-let [finished (last (filter #(= "finished" (:record %)) (store/records-at "HEAD" id)))]
    (update (branch-history (str (:sha finished) "^2") id) :records concat [finished])
    {:blocks (:blocks file)
     :lore (when (store/has-branch? id) (:lore (branch-history (store/ref-of id) id)))}))

(defn- task-view
  "Everything sf start and sf show print about the task."
  [id]
  (let [file (store/task-file id)]
    (if (store/finished? id)
      (merge {:file file :status "finished" :state {:stage (get-in file [:front "stage"])
                                                    :route (get-in file [:front "route"])}}
             (finished-history id file))
      (merge {:file file :status "open" :state (store/state id)}
             (branch-history (store/ref-of id) id)))))

(defn- print-view [{:keys [file records blocks lore]}]
  (println "---")
  (println (str/join "\n\n" (remove str/blank? [(:request file)
                                                (str/join "\n\n" (or blocks (map record/block records)))
                                                (str/join "\n" lore)]))))

(defn show [[id-arg]]
  (let [id (store/resolve-id id-arg)
        {:keys [file status state] :as view} (task-view id)]
    (println (str "TASK: " id))
    (println (str "TITLE: " (:title file)))
    (println (str "STATUS: " status))
    (println (str "STAGE: " (:stage state)))
    (println (str "ROUTE: " (:route state)))
    (print-view view)))

(defn start [[id-arg]]
  (let [id (open-task id-arg)
        dir (worktree-of id)
        root (shell/repo-root)]
    (when-not (fs/directory? dir)
      (git root "worktree" "add" "--quiet" dir (record/branch id)))
    (hook/install root)
    (let [{:keys [file state] :as view} (task-view id)]
      (println (str "TASK: " id))
      (println (str "TITLE: " (:title file)))
      (println (str "STAGE: " (:stage state)))
      (println (str "ROUTE: " (:route state)))
      (println (str "BRANCH: " (record/branch id)))
      (println (str "DIR: " dir))
      (print-view view))))

(defn- read-message
  "-m and the words after it, -F and a file, or the words; default when none."
  [default [flag value :as args]]
  (case flag
    (nil "") default
    "-m" (if (< (count args) 2) (shell/die "-m needs a value") (str/join " " (rest args)))
    "-F" (if (< (count args) 2) (shell/die "-F needs a file") (read-file value))
    (str/join " " args)))

(defn- handoff-options
  "--to and its role anywhere in args; the rest is the summary."
  [args]
  (let [at (.indexOf (vec args) "--to")]
    (if (neg? at)
      {:summary (read-message nil args)}
      (do (when (= at (dec (count args))) (shell/die "--to needs a value"))
          {:to (nth args (inc at))
           :summary (read-message nil (concat (take at args) (drop (+ at 2) args)))}))))

(defn- next-in-route [route role]
  (second (drop-while #(not= role %) (config/route-list route))))

(defn- refuse-waiting [state]
  (when (waiting? state)
    (shell/die "task is waiting on the operator (needs:" (:needs state) "); handoff refused")))

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
        {:keys [to summary]} (handoff-options args)
        state (store/state id)
        _ (refuse-waiting state)
        from (or role (acting-role state))
        to (or (not-empty to) (next-in-route (:route state) (:stage state)) "done")
        _ (when (not= "done" to) (config/require-role to))
        head (role-head id)]
    (when-not audit-passed? (audit-gate id from head))
    (store/write! id (fn [records]
                       (let [state (last records)
                             rec {:record "handoff" :from from :stage to :route (:route state) :needs "none"
                                  :commit head :text (or (not-empty (str/trim (str summary))) (str from " work complete"))}]
                         (refuse-waiting state)
                         (cond-> rec (= "done" to) (assoc :archive (archive id records rec))))))
    (when (and (= "done" to) (fs/directory? (worktree-of id)))
      (git (worktree-of id) "reset" "--quiet" "--hard"))
    (println (str "HANDOFF: " id " " from " -> " to " at " head))))

(defn ask [[id-arg & args]]
  (let [id (open-task id-arg)
        text (free-text args "ask needs a question")]
    (write! id "question" acting-role (constantly {:text text :needs "operator"}))
    (println (str "ASKED: " id " waits for the operator"))))

(defn answer [[id-arg & args]]
  (let [id (open-task id-arg)
        text (free-text args "answer needs text")
        rec (write! id "answer" "operator" (constantly {:text text :needs "none"}))]
    (println (str "ANSWERED: " id " resumes at stage:" (:stage rec)))))

(defn review [[id-arg & args]]
  (let [id (open-task id-arg)
        summary (str/trim (read-message "specification ready for review" args))
        head (role-head id)
        role (acting-role (store/state id))]
    (audit-gate id role head)
    (write! id "review" role (constantly {:text summary :needs "approval"}))
    (println (str "REVIEW: " id " waits for operator approval"))))

(defn- require-approval [state]
  (when (not= "approval" (:needs state))
    (shell/die "task is not waiting for approval")))

(defn approve [[id-arg]]
  (let [id (open-task id-arg)
        state (store/state id)]
    (require-approval state)
    (role-head id)
    (write! id "approval" "operator" (fn [state] (require-approval state) {:needs "none"}))
    ;; The review that asked for approval passed the audit gate at this commit.
    ;; Thus the handoff must not run the gate again.
    (handoff [id] {:role (acting-role state) :audit-passed? true})))

(defn reject [[id-arg & args]]
  (let [id (open-task id-arg)
        text (free-text args "reject needs a reason")
        rec (write! id "changes-requested" "operator"
                    (fn [state] (require-approval state) {:text text :needs "none"}))]
    (println (str "REJECTED: " id " stays at stage:" (:stage rec)))))

(defn finish [[id-arg & args]]
  (let [id (open-task id-arg)
        {:keys [target]} (parse-options "finish" args {"--into" :target} {})
        state (store/state id)
        _ (when (not= "done" (:stage state)) (shell/die "task is at stage:" (:stage state) ", not done"))
        root (shell/repo-root)
        dir (worktree-of id)
        target (or (not-empty target) (git root "rev-parse" "--abbrev-ref" "HEAD"))]
    (git root "checkout" "--quiet" target)
    (store/merge! id (record/message {:task id :record "finished" :from "operator" :stage "done"
                                      :route (:route state) :needs "none" :merged-into target}))
    (when (fs/directory? dir) (git root "worktree" "remove" "--force" dir))
    (git root "branch" "-D" (record/branch id))
    (println (str "FINISHED: " id " merged into " target))))
