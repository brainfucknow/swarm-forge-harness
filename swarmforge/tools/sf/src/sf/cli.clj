(ns sf.cli
  "sf: SwarmForge task helper. Each task is a git-bug issue. A role hands work
  to the next role: it commits on the task branch and records a handoff comment."
  (:require [sf.adapters :as adapters]
            [sf.config :as config]
            [sf.dispatch :as dispatch]
            [sf.shell :as shell]
            [sf.task :as task]
            [sf.tool :as tool]))

(def usage "usage: sf <command> [args]

Operator commands
  init                           create the git-bug identity if none exists
  new <title> [-m text | -F file] [--route NAME|a,b,c]
                                  create a task on a route; prints its id
  route <id> [NAME|a,b,c]         print or change the task's route
  ls [--stage ROLE] [--needs]     list open tasks
  show <id>                       show a task with all comments
  answer <id> <text>              answer a role's question and clear needs:operator
  approve <id>                    accept the specification and hand it to the next role
  reject <id> <text>              send the specification back to the specifier
  finish <id> [--into BRANCH]     merge the task branch into BRANCH (default: current) and close

Role commands
  next <role>                     print ids of tasks waiting at stage:<role>, or NO_TASK
  start <id>                      create or enter the task worktree; print the task
  handoff <id> [--to ROLE] [-m text]
                                  record HEAD and move the task to the next stage
  ask <id> <text>                 record a question and stop: labels needs:operator
  review <id> [-m text]           ask the operator to approve the specification

Catalogue commands
  roles                           print the role catalogue in canonical order
  routes                          print the named routes
  stage <id>                      print the task's current stage
  dispatch <role> <id> [backend]  run a role headlessly (claude, codex, copilot)
  run <id> [backend]              dispatch stage after stage until done or blocked
  adapters                        regenerate .claude/.codex/.github agent files
  tool require|ensure|list|bin    install constitution tools project-locally (sf tool --help)
  check                           validate roles.conf, tiers.conf, routes.conf, tools.conf, role prompts

Environment
  SWARMFORGE_ROUTE     default route for `new` (default: feature, else every role)
  SWARMFORGE_ROLE      role name used for `from:` (default: the task's stage)
  SWARMFORGE_BACKEND   default backend for dispatch/run
  SWARMFORGE_GIT_BUG   git-bug executable (default: git-bug)
  SWARMFORGE_CLAUDE_ARGS, SWARMFORGE_CODEX_ARGS, SWARMFORGE_COPILOT_ARGS
                       extra arguments for the headless backend, for example
                       the permission flags an unattended run needs")

(defn- print-roles [_]
  (run! println (config/pipeline)))

(def ^:private commands
  {"init" task/init
   "new" task/new-task
   "route" task/route
   "ls" task/ls
   "show" task/show
   "next" task/next-task
   "stage" task/stage
   "start" task/start
   "handoff" #(task/handoff % {})
   "ask" task/ask
   "answer" task/answer
   "review" task/review
   "approve" task/approve
   "reject" task/reject
   "finish" task/finish
   "roles" print-roles
   "pipeline" print-roles
   "routes" (fn [_] (doseq [[name route] (config/named-routes)]
                      (println (format "%-10s %s" name (str route)))))
   "dispatch" dispatch/dispatch
   "run" dispatch/run
   "adapters" adapters/adapters
   "check" (fn [_] (config/check))})

(defn main [[command & args]]
  (cond
    (contains? #{nil "" "-h" "--help" "help"} command) (println usage)
    (= "tool" command) (let [status (tool/main args)]
                         (when-not (zero? status) (shell/fail status nil)))
    (commands command) ((commands command) (vec args))
    :else (shell/die "unknown command '" command "' (try sf --help)")))

(defn -main [args]
  (System/exit (shell/main-with "sf: " #(main args))))
