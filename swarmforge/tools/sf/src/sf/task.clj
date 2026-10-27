(ns sf.task
  "The commands that create, move, and close tasks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.bug :as bug]
            [sf.config :as config]
            [sf.hook :as hook]
            [sf.lore :as lore]
            [sf.shell :as shell]))

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" args)))

(defn- branch-of [id]
  (str (shell/env "SWARMFORGE_BRANCH_PREFIX" "swarm/") (bug/short-id id)))

(defn- worktree-of [id]
  (str (shell/repo-root) "/" (hook/worktree-dir) "/" (bug/short-id id)))

(defn- acting-role [id]
  (or (shell/env "SWARMFORGE_ROLE" nil) (bug/stage id)))

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
  "The arguments as one header line; stops with complaint when there are none."
  [args complaint]
  (let [text (bug/one-line (str/join " " args))]
    (when (str/blank? text) (shell/die complaint))
    text))

(defn- committed-head
  "HEAD of the task worktree, which must exist and have no uncommitted changes."
  [id dir]
  (when-not (fs/directory? dir)
    (shell/die "task worktree missing; run: sf start " (bug/short-id id)))
  (when (seq (git dir "status" "--porcelain"))
    (shell/die "uncommitted changes in " dir "; commit first"))
  (git dir "rev-parse" "--short" "HEAD"))

(defn- files-since-last-record
  "Files changed on the task branch since the last recorded commit, as one
  comma-separated header value, or - when there are none."
  [id dir]
  (let [prev (bug/last-recorded-commit id)
        files (when (and prev (shell/ok? (shell/run {:dir dir} "git" "rev-parse" "--verify" "--quiet" (str prev "^{commit}"))))
                (vec (shell/lines (git dir "diff" "--name-only" (str prev "..HEAD")))))
        limit 40]
    (cond (empty? files) "-"
          (> (count files) limit) (str (str/join "," (take limit files)) ",+" (- (count files) limit) " more")
          :else (str/join "," files))))

(defn- audit-gate
  "The `audit` flag in roles.conf. Stops with exit 3 when it refuses the attempt."
  [id role head]
  (when (config/role-audits? role)
    (let [short (bug/short-id id)
          file (fs/path (shell/repo-root) ".swarmforge" "audit" short)]
      (if (and (fs/exists? file) (= head (read-file (str file))))
        (fs/delete file)
        (do (fs/create-dirs (fs/parent file))
            (spit (str file) (str head "\n"))
            (println (str "AUDIT_REQUIRED: " short " at " head "; nothing was recorded
Before running this command again:
- Re-read the task as sf start prints it: the request, every answer, and the record you received.
- Check every requirement and constraint against the committed work, including boundaries, failure cases, and what must not happen.
- For each requirement, name the test or check that covers it, or call it a gap. Passing tools alone do not make the task complete.
- Read the whole diff since the previous record (git diff " (bug/last-recorded-commit id) "..HEAD), the tests, and anything generated or left in the working tree.
- Fix every finding, commit, rerun the checks, then run this command again. It records the handoff when HEAD is still " head "; a new commit starts a new audit."))
            (shell/fail 3 nil))))))

(defn init [_]
  (when-not (shell/executable? (bug/git-bug))
    (shell/die "git-bug not found on PATH (https://github.com/git-bug/git-bug)"))
  (shell/quietly config/check)
  (if (shell/ok? (shell/run {} (bug/git-bug) "user" "show"))
    (println (str "identity: " (shell/chomp (shell/must {} (bug/git-bug) "user" "show" "--field" "name"))))
    (let [name (shell/env "SWARMFORGE_USER" "operator")]
      (shell/must {} (bug/git-bug) "user" "new" "--non-interactive" "-n" name
                  "-e" (shell/env "SWARMFORGE_EMAIL" (str name "@swarmforge.local")))
      (println (str "identity: " name " (created)"))))
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
        created (bug/run "new" "--non-interactive" "-t" title "-m" body)
        short (some #(when (str/includes? % "created") (first (str/split (str/trim %) #"\s+")))
                    (str/split-lines (:out created)))]
    (when-not (shell/ok? created) (shell/fail (:exit created) nil))
    (when-not short (shell/die "git-bug did not create the task: " (shell/chomp (:out created))))
    (let [id (bug/bug-id short)
          root (shell/repo-root)
          branch (branch-of id)]
      (bug/set-route id route)
      (bug/set-stage id (first (config/route-list route)))
      (git root "branch" branch)
      (bug/record id "type" "created" "from" "operator" "branch" branch
                  "commit" (git root "rev-parse" "--short" branch) "route" route)
      (println short))))

(defn route [[id-arg spec]]
  (let [id (bug/bug-id id-arg)]
    (if (str/blank? spec)
      (println (bug/route id))
      (let [_ (when (not= "open" (bug/status id)) (shell/die "task is closed"))
            stage (bug/stage id)
            route (config/resolve-route spec)]
        (when-not (or (= "done" stage) (some #{stage} (config/route-list route)))
          (shell/die "route '" route "' does not contain the current stage '" stage "'"))
        (bug/record id "type" "route" "from" "operator" "route" route)
        (bug/set-route id route)
        (println (str "ROUTE: " (bug/short-id id) " " route))))))

(defn ls [args]
  (let [{:keys [stage needs]} (parse-options "ls" args {"--stage" :stage} {"--needs" :needs})]
    (doseq [id (bug/open-ids)
            :let [s (bug/stage id)
                  n (str/join " " (bug/needs id))]
            :when (and (or (nil? stage) (= stage s))
                       (or (not needs) (seq n)))]
      (println (str/join "\t" [(bug/short-id id) (or s "?") n (bug/route id) (bug/title id)])))))

(defn- lore-lines
  "The LORE: lines of the task branch since the commit of the created record.
  None when the record or the branch is gone."
  [id shown]
  (let [root (shell/repo-root)
        base (bug/record-commit shown #{"created"})
        branch (str "refs/heads/" (branch-of id))]
    (when (and base (shell/ok? (shell/run {:dir root} "git" "rev-parse" "--verify" "--quiet" branch)))
      (lore/lines (lore/read-log (shell/must {:dir root} "git" "log" "--reverse"
                                             (str "--format=" lore/log-format) (str base ".." branch)))))))

(defn- print-task
  "The task with all comments, then its LORE: lines."
  [id]
  (let [shown (bug/show id)]
    (print shown)
    (run! println (lore-lines id shown))))

(defn show [[id-arg]]
  (print-task (bug/bug-id id-arg)))

(defn next-task [[role]]
  (when (str/blank? role) (shell/die "next needs a role"))
  (config/require-role role)
  (let [ready (filter #(empty? (bug/needs %)) (bug/open-ids "-l" (str "stage:" role)))]
    (if (seq ready)
      (doseq [id ready] (println (bug/short-id id)))
      (println "NO_TASK"))))

(defn stage [[id-arg]]
  (some-> (bug/stage (bug/bug-id id-arg)) println))

(defn start [[id-arg]]
  (let [id (bug/bug-id id-arg)
        _ (when (not= "open" (bug/status id)) (shell/die "task is closed"))
        branch (branch-of id)
        dir (worktree-of id)
        root (shell/repo-root)]
    (when-not (fs/directory? dir)
      (git root "worktree" "add" "--quiet" dir branch))
    (hook/install root)
    (println (str "TASK: " (bug/short-id id)))
    (println (str "TITLE: " (bug/title id)))
    (println (str "STAGE: " (bug/stage id)))
    (println (str "ROUTE: " (bug/route id)))
    (println (str "BRANCH: " branch))
    (println (str "DIR: " dir))
    (println "---")
    (print-task id)))

(defn- next-in-route [route role]
  (second (drop-while #(not= role %) (config/route-list route))))

(defn handoff
  "Records HEAD and moves the task on. role overrides the acting role, and
  audit-passed? skips the audit gate."
  [[id-arg & args] {:keys [role audit-passed?]}]
  (let [id (bug/bug-id id-arg)
        {:keys [to summary]} (parse-options "handoff" args {"--to" :to "-m" :summary} {})
        needs (bug/needs id)
        _ (when (seq needs)
            (shell/die "task is waiting on the operator (needs:" (str/join "\n" needs) "); handoff refused"))
        from (or role (acting-role id))
        to (or (not-empty to) (next-in-route (bug/route id) (bug/stage id)) "done")
        _ (when (not= "done" to) (config/require-role to))
        dir (worktree-of id)
        head (committed-head id dir)
        branch (git dir "rev-parse" "--abbrev-ref" "HEAD")]
    (when (not= branch (branch-of id))
      (shell/die "worktree is on '" branch "', expected '" (branch-of id) "'"))
    (when-not audit-passed? (audit-gate id from head))
    (bug/record id "type" "handoff" "from" from "to" to "branch" branch "commit" head
                "files" (files-since-last-record id dir)
                "summary" (bug/one-line (or (not-empty summary) (str from " work complete"))))
    (bug/set-stage id to)
    (println (str "HANDOFF: " (bug/short-id id) " " from " -> " to " at " head))))

(defn ask [[id-arg & args]]
  (let [id (bug/bug-id id-arg)
        text (free-text args "ask needs a question")]
    (bug/record id "type" "question" "from" (acting-role id) "question" text)
    (bug/add-label id "needs:operator")
    (println (str "ASKED: " (bug/short-id id) " waits for the operator"))))

(defn answer [[id-arg & args]]
  (let [id (bug/bug-id id-arg)
        text (free-text args "answer needs text")]
    (bug/record id "type" "answer" "from" "operator" "answer" text)
    (bug/rm-label id "needs:operator")
    (println (str "ANSWERED: " (bug/short-id id) " resumes at stage:" (bug/stage id)))))

(defn- read-message [default [flag value :as args]]
  (case flag
    (nil "") default
    "-m" (if (< (count args) 2) (shell/die "-m needs a value") value)
    "-F" (if (< (count args) 2) (shell/die "-F needs a file") (read-file value))
    (str/join " " args)))

(defn review [[id-arg & args]]
  (let [id (bug/bug-id id-arg)
        summary (bug/one-line (read-message "specification ready for review" args))
        dir (worktree-of id)
        head (committed-head id dir)
        role (acting-role id)]
    (audit-gate id role head)
    (bug/record id "type" "review" "from" role "commit" head
                "files" (files-since-last-record id dir) "summary" summary)
    (bug/add-label id "needs:approval")
    (println (str "REVIEW: " (bug/short-id id) " waits for operator approval"))))

(defn- require-approval [id]
  (when-not (bug/require-label id "needs:approval")
    (shell/die "task is not waiting for approval")))

(defn approve [[id-arg]]
  (let [id (bug/bug-id id-arg)]
    (require-approval id)
    (bug/record id "type" "approval" "from" "operator")
    (bug/rm-label id "needs:approval")
    ;; The review that asked for approval passed the audit gate at this commit.
    ;; Thus the handoff must not run the gate again.
    (try
      (handoff [id] {:role (acting-role id) :audit-passed? true})
      (catch clojure.lang.ExceptionInfo e
        (when-not (str/blank? (ex-message e)) (shell/warn (ex-message e)))
        (bug/add-label id "needs:approval")
        (shell/die "approval recorded but the handoff failed; the task still waits for approval")))))

(defn reject [[id-arg & args]]
  (let [id (bug/bug-id id-arg)
        text (free-text args "reject needs a reason")]
    (require-approval id)
    (bug/record id "type" "changes-requested" "from" "operator" "reason" text)
    (bug/rm-label id "needs:approval")
    (println (str "REJECTED: " (bug/short-id id) " stays at stage:" (bug/stage id)))))

(defn finish [[id-arg & args]]
  (let [id (bug/bug-id id-arg)
        {:keys [target]} (parse-options "finish" args {"--into" :target} {})
        stage (bug/stage id)
        _ (when (not= "done" stage) (shell/die "task is at stage:" stage ", not done"))
        root (shell/repo-root)
        branch (branch-of id)
        dir (worktree-of id)
        target (or (not-empty target) (git root "rev-parse" "--abbrev-ref" "HEAD"))]
    (git root "checkout" "--quiet" target)
    (git root "merge" "--no-ff" "--no-edit" branch)
    (when (fs/directory? dir) (git root "worktree" "remove" "--force" dir))
    (git root "branch" "-D" branch)
    (bug/record id "type" "finished" "from" "operator" "merged-into" target)
    (shell/must {} (bug/git-bug) "bug" "status" "close" id)
    (println (str "FINISHED: " (bug/short-id id) " merged into " target))))
