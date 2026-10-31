(ns sf.dispatch-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [speclj.core :refer [around describe it should-contain should=]]
            [sf.dispatch :as dispatch]
            [sf.repo-support :as support :refer [*dir* git with-repo]]
            [sf.shell :as shell]
            [sf.task :as task]))

(def ^:private command-for @#'sf.dispatch/command-for)
(def ^:private detect-backend @#'sf.dispatch/detect-backend)

(defn- env-of [variables]
  (fn [name default] (get variables name default)))

(defn- backend-shell
  "A p/shell that hands git to the real one and every other command to handler."
  [handler]
  (let [real p/shell]
    (fn [opts] (if (= "git" (first (:cmd opts))) (real opts) (handler opts)))))

(defn- new-id [& args]
  (shell/chomp (with-out-str (task/new-task (vec args)))))

(describe "command-for"
  (around [it] (with-repo it))

  (it "runs claude on the model of the role's tier, none for inherit"
    (should= ["claude" "-p" "--permission-mode" "acceptEdits" "--model" "opus" "prompt"]
             (vec (command-for "claude" "coder" "prompt")))
    (should= ["claude" "-p" "--permission-mode" "acceptEdits" "prompt"]
             (vec (command-for "claude" "specifier" "prompt"))))

  (it "adds the extra arguments of the backend"
    (with-redefs [shell/env (env-of {"SWARMFORGE_CLAUDE_ARGS" " --verbose  --x " "SWARMFORGE_CODEX_ARGS" "--y"
                                     "SWARMFORGE_COPILOT_ARGS" "--z"})]
      (should= ["claude" "-p" "--permission-mode" "acceptEdits" "--model" "opus" "--verbose" "--x" "prompt"]
               (vec (command-for "claude" "coder" "prompt")))
      (should= ["codex" "exec" "--full-auto" "-c" "model_reasoning_effort=\"medium\"" "--y" "prompt"]
               (vec (command-for "codex" "coder" "prompt")))
      (should= ["copilot" "-p" "prompt" "--model" "gpt-5" "--z"]
               (vec (command-for "copilot" "coder" "prompt")))))

  (it "grants copilot no tool permissions beyond SWARMFORGE_COPILOT_ARGS"
    (should= ["copilot" "-p" "prompt" "--model" "gpt-5"]
             (vec (command-for "copilot" "coder" "prompt")))
    (with-redefs [shell/env (env-of {"SWARMFORGE_COPILOT_ARGS" "--allow-all-tools"})]
      (should= ["--model" "gpt-5" "--allow-all-tools"]
               (take-last 3 (command-for "copilot" "coder" "prompt"))))
    (with-redefs [shell/env (env-of {"SWARMFORGE_COPILOT_ARGS" "--allow-tool shell(git:*)"})]
      (should= ["--model" "gpt-5" "--allow-tool" "shell(git:*)"]
               (take-last 4 (command-for "copilot" "coder" "prompt")))))

  (it "refuses an unknown backend"
    (should-contain "unknown backend 'nope'" (:err (support/captured #(command-for "nope" "coder" "p"))))))

(describe "detect-backend"
  (it "takes SWARMFORGE_BACKEND first"
    (with-redefs [shell/env (env-of {"SWARMFORGE_BACKEND" "codex"})]
      (should= "codex" (detect-backend))))

  (it "takes the first backend found on the path"
    (with-redefs [fs/which (fn [name] (when (= "codex" name) "/bin/codex"))]
      (should= "codex" (detect-backend))))

  (it "stops when there is none"
    (with-redefs [fs/which (constantly nil)]
      (should-contain "no backend found" (:err (support/captured detect-backend))))))

(defn- dispatched
  "The options of each backend run that dispatching role on id with backend starts."
  [role id backend]
  (let [ran (atom [])]
    (with-redefs [p/shell (backend-shell (fn [opts] (swap! ran conj opts) {:exit 0}))]
      (dispatch/dispatch [role id backend]))
    @ran))

(defn- branch-of [dir]
  (git dir "rev-parse" "--abbrev-ref" "HEAD"))

(describe "dispatch"
  (around [it] (with-repo it))

  (it "runs the backend command"
    (let [id (new-id "Add login")
          [{:keys [cmd]}] (dispatched "coder" id "claude")]
      (should= ["claude" "-p" "--permission-mode" "acceptEdits" "--model" "opus"]
               (take 6 cmd))
      (should-contain (str "do your role's work for task " id) (last cmd))))

  (it "runs every backend in the task worktree, created on the task branch when missing"
    (doseq [backend dispatch/backends]
      (let [id (new-id (str "Add " backend))
            dir (str *dir* "/.worktrees/" id)]
        (should= [dir] (map :dir (dispatched "coder" id backend)))
        (should= (str "swarm/" id) (branch-of dir)))))

  (it "dispatches an explicitly selected optional reviewer with the existing strong tier on every backend"
    (support/write-conf! *dir* "roles.conf" "coder strong subagent\nsecurity-reviewer strong subagent optional\ncleaner fast subagent\n")
    (doseq [backend dispatch/backends]
      (let [id (new-id (str "Review " backend) "--route" "coder,security-reviewer,cleaner")
            [{:keys [cmd dir]}] (dispatched "security-reviewer" id backend)
            expected (vec (command-for backend "coder" (if (= "copilot" backend) (nth cmd 2) (last cmd))))]
        (should= expected cmd)
        (should= (str *dir* "/.worktrees/" id) dir))))

  (it "reuses the task worktree with uncommitted changes in it"
    (let [id (new-id "Add login")
          dir (str *dir* "/.worktrees/" id)]
      (dispatched "specifier" id "codex")
      (spit (str dir "/work-in-progress") "x")
      (should= [dir] (map :dir (dispatched "coder" id "codex")))
      (should= "x" (slurp (str dir "/work-in-progress")))))

  (it "refuses a task worktree on another branch without starting the backend"
    (let [id (new-id "Add login")
          dir (str *dir* "/.worktrees/" id)]
      (dispatched "coder" id "claude")
      (git dir "checkout" "-q" "-b" "other")
      (let [ran (atom [])
            result (with-redefs [p/shell (backend-shell (fn [opts] (swap! ran conj opts) {:exit 0}))]
                     (support/captured #(dispatch/dispatch ["coder" id "claude"])))]
        (should= 1 (:status result))
        (should= (str "worktree is on 'other', expected 'swarm/" id "'") (:err result))
        (should= [] @ran))))

  (it "fails with the exit status of the backend"
    (let [id (new-id "Add login")]
      (with-redefs [p/shell (backend-shell (constantly {:exit 7}))]
        (should= 7 (:status (support/captured #(dispatch/dispatch ["coder" id "claude"])))))))

  (it "names a backend that cannot start"
    (let [id (new-id "Add login")]
      (with-redefs [p/shell (backend-shell (fn [_] (throw (java.io.IOException. "no such file"))))]
        (should= {:out "" :err "claude: no such file" :status 1}
                (support/captured #(dispatch/dispatch ["coder" id "claude"]))))))

  (it "needs a known role"
    (should-contain "dispatch needs a role" (:err (support/captured #(dispatch/dispatch []))))
    (should-contain "unknown role" (:err (support/captured #(dispatch/dispatch ["nobody" "x"]))))))

(describe "run"
  (around [it] (with-repo it))

  (it "dispatches stage after stage until the task is done"
    (let [id (new-id "Add login" "--route" "coder,cleaner")
          stages (atom [])]
      (with-redefs [dispatch/dispatch (fn [[stage id _]]
                                        (swap! stages conj stage)
                                        (task/handoff [id] {:audit-passed? true}))]
        (should-contain (str "DONE: " id) (:out (support/captured #(dispatch/run [id])))))
      (should= ["coder" "cleaner"] @stages)))

  (it "stops with status 2 when the operator is needed"
    (let [id (new-id "Add login")]
      (task/ask [id "Which?"])
      (let [result (support/captured #(dispatch/run [id]))]
        (should= 2 (:status result))
        (should-contain "BLOCKED" (:out result)))))

  (it "stops when a stage makes no progress"
    (let [id (new-id "Add login")]
      (with-redefs [dispatch/dispatch (fn [_])]
        (should-contain "made no progress" (:err (support/captured #(dispatch/run [id]))))))))
