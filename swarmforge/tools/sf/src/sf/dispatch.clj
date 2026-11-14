(ns sf.dispatch
  "Headless role runs on a backend CLI, or on a Mistral Workflow when no CLI is
  on PATH and MISTRAL_API_KEY is set."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.task :as task]))

(def backends
  "The backends dispatch runs: the CLI backends a record can name, plus the
  Mistral Workflow backend."
  (conj record/backends "mistral"))

(def cli-backends
  "The backends that need their CLI on PATH; mistral is detected by
  MISTRAL_API_KEY instead."
  (vec record/backends))

(defn- sf-mistral-path []
  (str (fs/path (shell/repo-root) "swarmforge" "bin" "sf-mistral")))

(defn- bb-bin []
  (or (fs/which "bb") "bb"))

(defn- mistral-command
  "The command that runs role on task id as one Mistral Workflow execution:
  sf-mistral pushes the task branch, starts a swarmforge-role execution, waits
  for it, and fetches the branch back."
  [role id]
  [(bb-bin) (sf-mistral-path) "dispatch" role id])

(defn- role-prompt [role short]
  (if (config/gate? role)
    (str "You are the SwarmForge '" role "' gate. Read swarmforge/roles/" role ".md, then audit the specification of task " short " that waits for approval. Start with: swarmforge/bin/sf show " short ". Change nothing: no edits, no commits, no sf command that records. End with your report.")
    (str "You are the SwarmForge '" role "' role. Read swarmforge/constitution.md and follow its reading instructions, then read swarmforge/roles/" role ".md, then do your role's work for task " short ". Start with: swarmforge/bin/sf start " short)))

(defn- detect-backend []
  (or (shell/env "SWARMFORGE_BACKEND" nil)
      (some #(when (fs/which %) %) cli-backends)
      (when (shell/env "MISTRAL_API_KEY" nil) "mistral")
      (shell/die "no backend found (claude, codex, copilot, mistral); set SWARMFORGE_BACKEND or MISTRAL_API_KEY")))

(defn- extra-args [variable]
  (some-> (shell/env variable nil) str/trim (str/split #"\s+")))

(defn- command-for
  "The backend command that runs role on prompt. A gate runs without edit
  permissions: claude in its default permission mode with the editing tools
  disallowed, codex in its read-only sandbox, copilot with the write tool
  denied."
  [backend role prompt]
  (let [tier (config/role-field role 2)
        model (config/tier-field tier ({"claude" 2 "codex" 3 "copilot" 4} backend 2))
        gate? (config/gate? role)]
    (case backend
      "claude" (concat ["claude" "-p" "--permission-mode" (if gate? "default" "acceptEdits")]
                       (when gate? ["--disallowedTools=Edit" "--disallowedTools=Write" "--disallowedTools=NotebookEdit"])
                       (when (and model (not= "inherit" model)) ["--model" model])
                       (extra-args "SWARMFORGE_CLAUDE_ARGS")
                       [prompt])
      "codex" (concat ["codex" "exec"] (if gate? ["--sandbox" "read-only"] ["--full-auto"])
                      ["-c" (str "model_reasoning_effort=\"" model "\"")]
                      (extra-args "SWARMFORGE_CODEX_ARGS")
                      [prompt])
      "copilot" (concat ["copilot" "-p" prompt "--model" (str model)]
                        (when gate? ["--deny-tool=write"])
                        (extra-args "SWARMFORGE_COPILOT_ARGS"))
      (shell/die "unknown backend '" backend "'"))))

(defn- pipeline-command
  "The command that runs the whole task as one swarmforge-pipeline execution."
  [id]
  [(bb-bin) (sf-mistral-path) "run" id])

(defn dispatch [[role id-arg backend]]
  (when (str/blank? role) (shell/die "dispatch needs a role"))
  (config/require-agent role)
  (when (config/spec-judge? role)
    (shell/die "'" role "' is the spec-judge: it needs the spec revision's gate reports and runs only as a subagent of the session"))
  (let [id (store/resolve-id id-arg)
        _ (store/refuse-unclosed! id)
        backend (or (not-empty backend) (detect-backend))
        command (vec (if (= "mistral" backend)
                       (mistral-command role id)
                       (command-for backend role (role-prompt role id))))
        dir (task/worktree! id)
        _ (flush)
        {:keys [exit]} (try (p/shell {:cmd command :dir dir :continue true
                                      :extra-env {"SWARMFORGE_DISPATCHED" role "SWARMFORGE_BACKEND" backend}})
                            (catch java.io.IOException e
                              (shell/die backend ": " (ex-message e))))]
    (when-not (zero? exit) (shell/fail exit nil))))

(defn- gate-snapshot
  "What a gate must leave as it found it: the task's state, and the HEAD and
  status of the task worktree."
  [id]
  (let [dir (task/worktree! id)]
    [(store/state id)
     (shell/chomp (shell/must {:dir dir} "git" "rev-parse" "HEAD"))
     (shell/must {:dir dir} "git" "status" "--porcelain")]))

(defn- run-gates
  "Dispatches each gate on the specification of id that waits for approval.
  A gate reports; it must not record, commit or edit."
  [id backend]
  (let [before (gate-snapshot id)]
    (doseq [gate (config/gates)]
      (dispatch [gate id backend])
      (when (not= before (gate-snapshot id))
        (shell/die "gate " gate " changed task " id "; a gate only reports")))))

(defn- run-mistral
  "One swarmforge-pipeline execution drives the task as far as it can without
  the operator: sf-mistral run polls it, then fetches the task branch back.
  Exit 0 means done, 2 means the task waits on the operator."
  [id]
  (let [dir (task/worktree! id)
        _ (flush)
        {:keys [exit]} (try (p/shell {:cmd (pipeline-command id) :dir dir :continue true})
                            (catch java.io.IOException e
                              (shell/die "mistral: " (ex-message e))))]
    (cond
      (zero? exit) (println (str "DONE: " id "; run: sf finish " id))
      (= 2 exit) (do (println (str "BLOCKED: " id " needs:" (:needs (store/state id))))
                     (shell/fail 2 nil))
      :else (shell/fail (if (pos? exit) exit 1) nil))))

(defn- run-stages
  "Dispatches stage after stage until the task is done (exit 0) or waits on the
  operator (exit 2). When a stage it dispatched asked for approval, it first
  runs the gates on that specification. It stops when a stage it dispatched
  added a record of the operator's."
  [id backend]
  (loop [dispatched? false]
    (let [{:keys [stage needs] :as state} (store/state id)]
      (cond
        (= "done" stage)
        (println (str "DONE: " id "; run: sf finish " id))

        (flow/waiting? state)
        (do (when (and dispatched? (= "approval" needs))
              (run-gates id backend))
            (println (str "BLOCKED: " id " needs:" needs))
            (shell/fail 2 nil))

        :else
        (let [before (count (store/open-records id))]
          (dispatch [stage id backend])
          (when-let [{:keys [record]} (flow/operator-record (drop before (store/open-records id)))]
            (shell/die "stage:" stage " added the operator's " record " record; only the operator approves, rejects, answers and routes"))
          (when (let [after (store/state id)]
                  (and (= stage (:stage after)) (not (flow/waiting? after))))
            (shell/die "stage:" stage " made no progress"))
          (recur true))))))

(defn run
  "Dispatches stage after stage until the task is done (exit 0) or waits on the
  operator (exit 2). The backend is resolved before choosing the
  implementation, so an auto-detected mistral backend also takes the single
  pipeline execution."
  [[id-arg backend]]
  (let [id (store/resolve-id id-arg)
        _ (store/refuse-unclosed! id)
        backend (or (not-empty backend) (detect-backend))]
    (if (= "mistral" backend)
      (run-mistral id)
      (run-stages id backend))))
