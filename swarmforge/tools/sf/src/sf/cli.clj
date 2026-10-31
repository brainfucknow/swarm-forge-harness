(ns sf.cli
  "sf: SwarmForge task helper. Each task is a git branch, swarm/<id>. A role
  hands work to the next role: it commits on the task branch and sf records a
  handoff commit."
  (:require [sf.adapters :as adapters]
            [sf.config :as config]
            [sf.dispatch :as dispatch]
            [sf.hook :as hook]
            [sf.shell :as shell]
            [sf.task :as task]
            [sf.tool :as tool]))

(def usage "usage: sf <command> [args]

<id> is a task's full id (slug-hex), its 7 hex digits, or a unique prefix.

Operator commands
  init                           install the commit-msg hook; print the identity of record commits
  new <title> [-m text | -F file] [--route NAME|a,b,c]
                                  create a task on a route; prints its id
  route <id> [NAME|a,b,c]         print or change the task's route
  ls [--stage ROLE] [--needs]     list open tasks
  show <id>                       show a task with all records, also once finished
  answer <id> <text>              answer a role's question and clear needs operator
  approve <id>                    accept the specification and hand it to the next role
  reject <id> <text>              send the specification back to the specifier
  finish <id> [--into BRANCH]     refuse uncommitted work in the worktree, run swarmforge/verify.conf
                                  on the merge, then merge the task branch into BRANCH
                                  (default: current) and remove it

Role commands
  next <role>                     print ids of tasks waiting at stage <role>, or NO_TASK
  start <id>                      create or enter the task worktree; print the task
  handoff <id> [--to ROLE] [-m text]
                                  record HEAD and move the task to the next stage
  ask <id> <text>                 record a question and stop: needs operator
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

Hook commands
  commit-msg <role> <file>        add the byline and a Lore-id to a commit message (the commit-msg hook runs this)

Environment
  SWARMFORGE_ROUTE     default route for `new` (default: feature, else every role)
  SWARMFORGE_ROLE      role name used for `from:` (default: the task's stage)
  SWARMFORGE_BACKEND   default backend for dispatch/run
  SWARMFORGE_USER, SWARMFORGE_EMAIL
                       author of record commits when git has no identity
                       (default: operator, <user>@swarmforge.local)
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
   "check" (fn [_] (config/check))
   "commit-msg" hook/commit-msg})

(defn main [[command & args]]
  (cond
    (contains? #{nil "" "-h" "--help" "help"} command) (println usage)
    (= "tool" command) (let [status (tool/main args)]
                         (when-not (zero? status) (shell/fail status nil)))
    (commands command) ((commands command) (vec args))
    :else (shell/die "unknown command '" command "' (try sf --help)")))

(defn -main [args]
  (System/exit (shell/main-with "sf: " #(main args))))
