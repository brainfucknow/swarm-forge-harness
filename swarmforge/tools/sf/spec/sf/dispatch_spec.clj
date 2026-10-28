(ns sf.dispatch-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [speclj.core :refer [around describe it should-contain should=]]
            [sf.dispatch :as dispatch]
            [sf.repo-support :as support :refer [with-repo]]
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
      (should= ["copilot" "-p" "prompt" "--model" "gpt-5" "--allow-all-tools" "--z"]
               (vec (command-for "copilot" "coder" "prompt")))))

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

(describe "dispatch"
  (around [it] (with-repo it))

  (it "runs the backend command in the repository"
    (let [ran (atom nil)
          id (new-id "Add login")]
      (with-redefs [p/shell (backend-shell (fn [opts] (reset! ran opts) {:exit 0}))]
        (dispatch/dispatch ["coder" id "claude"]))
      (should= ["claude" "-p" "--permission-mode" "acceptEdits" "--model" "opus"]
               (take 6 (:cmd @ran)))
      (should-contain (str "do your role's work for task " id) (last (:cmd @ran)))))

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
