(ns sf.dispatch
  "Headless role runs on the claude, codex, or copilot CLI."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.task :as task]))

(def backends ["claude" "codex" "copilot"])

(defn- role-prompt [role short]
  (str "You are the SwarmForge '" role "' role. Read swarmforge/constitution.md and every file it names, then read swarmforge/roles/" role ".md, then do your role's work for task " short ". Start with: swarmforge/bin/sf start " short))

(defn- detect-backend []
  (or (shell/env "SWARMFORGE_BACKEND" nil)
      (some #(when (fs/which %) %) backends)
      (shell/die "no backend found (claude, codex, copilot); set SWARMFORGE_BACKEND")))

(defn- extra-args [variable]
  (some-> (shell/env variable nil) str/trim (str/split #"\s+")))

(defn- command-for [backend role prompt]
  (let [tier (config/role-field role 2)
        model (config/tier-field tier ({"claude" 2 "codex" 3 "copilot" 4} backend 2))]
    (case backend
      "claude" (concat ["claude" "-p" "--permission-mode" "acceptEdits"]
                       (when (and model (not= "inherit" model)) ["--model" model])
                       (extra-args "SWARMFORGE_CLAUDE_ARGS")
                       [prompt])
      "codex" (concat ["codex" "exec" "--full-auto" "-c" (str "model_reasoning_effort=\"" model "\"")]
                      (extra-args "SWARMFORGE_CODEX_ARGS")
                      [prompt])
      "copilot" (concat ["copilot" "-p" prompt "--model" (str model)]
                        (extra-args "SWARMFORGE_COPILOT_ARGS"))
      (shell/die "unknown backend '" backend "'"))))

(defn dispatch [[role id-arg backend]]
  (when (str/blank? role) (shell/die "dispatch needs a role"))
  (config/require-role role)
  (let [id (store/resolve-id id-arg)
        backend (or (not-empty backend) (detect-backend))
        command (vec (command-for backend role (role-prompt role id)))
        dir (task/worktree! id)
        _ (flush)
        {:keys [exit]} (try (p/shell {:cmd command :dir dir :continue true})
                            (catch java.io.IOException e
                              (shell/die backend ": " (ex-message e))))]
    (when-not (zero? exit) (shell/fail exit nil))))

(defn run
  "Dispatches stage after stage until the task is done (exit 0) or waits on the
  operator (exit 2)."
  [[id-arg backend]]
  (let [id (store/resolve-id id-arg)]
    (loop []
      (let [{:keys [stage needs] :as state} (store/state id)]
        (cond
          (= "done" stage)
          (println (str "DONE: " id "; run: sf finish " id))

          (flow/waiting? state)
          (do (println (str "BLOCKED: " id " needs:" needs))
              (shell/fail 2 nil))

          :else
          (do (dispatch [stage id backend])
              (when (let [after (store/state id)]
                      (and (= stage (:stage after)) (not (flow/waiting? after))))
                (shell/die "stage:" stage " made no progress"))
              (recur)))))))
