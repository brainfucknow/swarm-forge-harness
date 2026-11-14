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
  approve <id> [--rejected \"<finding> | <reason>\"]...
                                  accept the specification and hand it to the next role;
                                  each --rejected records a finding approved over
  reject <id> <text>              send the specification back to the specifier
  spec-verdict <id> <converged|continue|needs-operator|stuck> <text>
                                  record the spec-judge's spec verdict on the newest spec revision; continue
                                  sends the specification back, refused after 2 automatic
                                  send-backs or growth of more than half over the baseline
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
  gates                           print the gate roles that audit a specification before approval
  routes                          print the named routes
  stage <id>                      print the task's current stage
  spec-revisions <id> [--full]    print each spec revision of the specification gate: size,
                                  growth, total growth over spec revision 1 and spec verdict,
                                  then the LIMIT lines and an informational NOTE; --full adds
                                  what each spec revision sent back
  harvest                         print every task's history as one JSON line each, finished
                                  tasks first (handoff-protocol.md describes the keys)
  dispatch <role> <id> [backend]  run a role or gate headlessly (claude, codex, copilot)
  run <id> [backend]              dispatch stage after stage until done or blocked; runs the
                                  gates when the specification it dispatched waits for approval
  adapters                        regenerate .claude/.codex/.github/.vibe agent files
  tool require|ensure|list|bin    install constitution tools project-locally (sf tool --help)
  check                           validate roles.conf, tiers.conf, routes.conf, tools.conf, role prompts

Hook commands
  commit-msg <role> <file>        add the byline and a Lore-id to a commit message (the commit-msg hook runs this)

Environment
  SWARMFORGE_ROUTE     default route for `new` (default: feature, else non-optional roles)
  SWARMFORGE_ROLE      role name used for `from:` (default: the task's stage)
  SWARMFORGE_DISPATCHED
                       set by dispatch and run to the role or gate they start;
                       new, approve, reject, spec-verdict, answer, route changes and finish
                       refuse to run under it
  SWARMFORGE_BACKEND   default backend for dispatch/run; set by dispatch and run to the
                       backend they start; under dispatch, records carry it
                       (handoff-protocol.md, Backend)
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
   "harvest" task/harvest
   "start" task/start
   "handoff" #(task/handoff % {})
   "ask" task/ask
   "answer" task/answer
   "review" task/review
   "approve" task/approve
   "reject" task/reject
   "spec-verdict" task/spec-verdict
   "spec-revisions" task/spec-revisions
   "finish" task/finish
   "roles" (config/in-checkout print-roles)
   "pipeline" (config/in-checkout print-roles)
   "gates" (config/in-checkout (fn [_] (run! println (config/gates))))
   "routes" (config/in-checkout (fn [_] (doseq [[name route] (config/named-routes)]
                                          (println (format "%-10s %s" name (str route))))))
   "dispatch" dispatch/dispatch
   "run" dispatch/run
   "adapters" (config/in-checkout adapters/adapters)
   "check" (config/in-checkout (fn [_] (config/check)))
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
