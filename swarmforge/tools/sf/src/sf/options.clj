(ns sf.options
  "The usage, the flags and the free text of the sf commands."
  (:require [clojure.string :as str]
            [sf.record :as record]
            [sf.shell :as shell]))

(def usage "usage: sf <command> [args]

<id> is a task's full id (slug-hex), its 7 hex digits, or a unique prefix.
<command> --help or -h prints that command's own usage lines.

Operator commands
  init                           install the commit-msg hook; print the identity of record commits
  new <title> [-m text | -F file] [--route NAME|a,b,c] [--gates <gates>]
      [--fixes <id>|'#N' [--found-by pr-review|ci|operator]]
                                  create a task on a route; prints its id. <gates> is a
                                  comma list of gate roles, or none (the default)
  route <id> [NAME|a,b,c]         print or change the task's route
  gates <id> [<gates>]            print the task's gates, one per line, or change them
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
                                  (default: the current branch; refused when that
                                  is the repository's default branch) and remove it

Role commands
  next <role>                     print ids of tasks waiting at stage <role>, or NO_TASK
  start <id>                      create or enter the task worktree; print the task
  handoff <id> [--to ROLE] [--allow-missing-tool] [-m text]
                                  record HEAD and move the task to the next stage; refused
                                  (exit 4) when a Not-tested trailer names a missing tool,
                                  unless --allow-missing-tool and an answered question
                                  names each one; a send-back role may name an earlier
                                  role in --to, at most twice per task
  ask <id> <text>                 record a question and stop: needs operator
  review <id> [--allow-missing-tool] [-m text]
                                  ask the operator to approve the specification; checks
                                  missing tools as handoff does

Catalogue commands
  roles                           print the role catalogue in canonical order
  gates                           print the gate roles of roles.conf: the gates a task can choose
  routes                          print the named routes
  stage <id>                      print the task's current stage
  spec-revisions <id> [--full]    print each spec revision of the specification gate: size,
                                  growth, total growth over spec revision 1 and spec verdict,
                                  then the LIMIT lines, then the WARNING when the newest spec
                                  revision is more than half over spec revision 1 across
                                  operator send-backs, then the informational NOTE; --full adds
                                  what each spec revision sent back
  harvest                         print every task's history as one JSON line each, finished
                                  tasks first (handoff-protocol.md describes the keys)
  dispatch <role> <id> [backend]  run a role or gate headlessly (claude, codex, copilot, mistral)
  run <id> [backend]              dispatch stage after stage until done or blocked; runs the
                                  task's gates when the specification it dispatched waits for approval;
                                  with mistral one swarmforge-pipeline workflow execution per
                                  call runs the loop on the Mistral platform (swarmforge/workflows/)
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
                       new, approve, reject, spec-verdict, answer, route and gates changes and finish
                       refuse to run under it
  SWARMFORGE_BACKEND   default backend for dispatch/run; set by dispatch and run to the
                       backend they start; under dispatch, records carry it
                       (handoff-protocol.md, Backend). With no CLI backend on PATH,
                       mistral is detected when MISTRAL_API_KEY is set, and needs a
                       git remote to share the task branch with the workflow worker
                       (swarmforge/workflows/)
  CLAUDECODE, CODEX_THREAD_ID
                       outside dispatch, CLAUDECODE=1 alone records claude/session;
                       nonblank CODEX_THREAD_ID alone records codex/session; both
                       active or neither records nothing. Dispatch takes precedence.
                       Child processes can inherit these markers; they identify the
                       command environment, not the immediate parent harness.
  SWARMFORGE_USER, SWARMFORGE_EMAIL
                       author of record commits when git has no identity
                       (default: operator, <user>@swarmforge.local)
  SWARMFORGE_CLAUDE_ARGS, SWARMFORGE_CODEX_ARGS, SWARMFORGE_COPILOT_ARGS
                       extra arguments for the headless backend, for example
                       the permission flags an unattended run needs
  MISTRAL_API_KEY, MISTRAL_API_URL
                       credentials and API root for the mistral backend
  SWARMFORGE_MISTRAL_ROLE_WORKFLOW, SWARMFORGE_MISTRAL_PIPELINE_WORKFLOW
                       workflow names the mistral backend starts (defaults
                       swarmforge-role and swarmforge-pipeline)
  SWARMFORGE_REMOTE, SWARMFORGE_MISTRAL_TIMEOUT_SECONDS
                       remote the task branch is pushed to (default origin) and
                       how long sf-mistral waits for the execution (default 3600s)")

(defn read-file [path]
  (shell/chomp (slurp path)))

(defn- require-value
  "Stops when flag, the head of args, has nothing after it."
  [[flag :as args]]
  (when (< (count args) 2)
    (shell/die flag " needs " (if (= flag "-F") "a file" "a value"))))

(defn parse
  "Reads flags into a map. values maps a flag that takes a value to its key, or
  to [key transform]; switches maps a flag without a value to its key. A flag
  in once may not be given twice; any other keeps its last value."
  ([command args values switches] (parse command args values switches #{}))
  ([command args values switches once]
   (loop [opts {} seen #{} [flag value :as args] args]
     (cond
       (empty? args) opts
       (switches flag) (recur (assoc opts (switches flag) true) seen (rest args))
       (values flag) (let [[key transform] (if (vector? (values flag)) (values flag) [(values flag) identity])]
                       (require-value args)
                       (when (and (once flag) (seen flag)) (shell/die flag " given twice"))
                       (recur (assoc opts key (transform value)) (conj seen flag) (drop 2 args)))
       :else (shell/die "unknown " command " option '" flag "'")))))

(defn free-text
  "The arguments as one text; stops with complaint when there are none."
  [args complaint]
  (let [text (record/free-text args)]
    (when (str/blank? text) (shell/die complaint))
    text))

(defn usage-entry
  "The lines of the usage that document command: the line that starts with two
  spaces, the command and a space, plus every line right after it that starts
  with three or more spaces."
  [command]
  (let [lines (str/split-lines usage)
        at (some (fn [[i line]] (when (str/starts-with? line (str "  " command " ")) i))
                 (map-indexed vector lines))]
    (when at
      (into [(nth lines at)]
            (take-while #(str/starts-with? % "   ") (drop (inc at) lines))))))

(defn option-like
  "Guards a free text argument: exactly --help or -h prints command's entry of
  the usage and returns true (handled); any other '-'-prefixed text stops with
  complaint as the refusal message."
  [command text complaint]
  (cond
    (contains? #{"--help" "-h"} text) (do (run! println (usage-entry command)) true)
    (str/starts-with? text "-") (shell/die complaint " that does not start with '-'")
    :else false))

(defn message
  "-m and the words after it, -F and a file, or the words; default when none."
  [default [flag value :as args]]
  (case flag
    (nil "") default
    "-m" (do (require-value args) (str/join " " (rest args)))
    "-F" (do (require-value args) (read-file value))
    (str/join " " args)))

(defn allow-missing-tool
  "Whether --allow-missing-tool is anywhere in args, and the other args."
  [args]
  [(boolean (some #{"--allow-missing-tool"} args)) (remove #{"--allow-missing-tool"} args)])

(defn handoff
  "--to and its role and --allow-missing-tool anywhere in args; the rest is
  the summary."
  [args]
  (let [[allow? args] (allow-missing-tool args)
        at (.indexOf (vec args) "--to")]
    (assoc (if (neg? at)
             {:summary (message nil args)}
             (do (when (= at (dec (count args))) (shell/die "--to needs a value"))
                 {:to (nth args (inc at))
                  :summary (message nil (concat (take at args) (drop (+ at 2) args)))}))
           :allow? allow?)))

(defn rejections
  "The values of the `--rejected <finding> | <reason>` options that make up
  args, each trimmed, in order; stops on any other argument and on a value that
  record/rejected-refusal refuses."
  [args]
  (loop [values [] [flag value :as args] args]
    (cond
      (empty? args) values
      (not= "--rejected" flag) (shell/die "unknown approve argument '" flag "'")
      :else (let [value (do (require-value args) (str/trim value))]
              (some-> (record/rejected-refusal value) shell/die)
              (recur (conj values value) (drop 2 args))))))
