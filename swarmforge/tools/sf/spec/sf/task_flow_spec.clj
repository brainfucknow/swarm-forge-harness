(ns sf.task-flow-spec
  "The task commands end to end in a throwaway repository."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [around describe it should should-not should= should-not= should-contain should-not-contain]]
            [sf.repo-support :refer [*dir* captured git with-repo write-conf!]]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.task :as task]))

(defn- out [f & args]
  (str/trim (with-out-str (apply f args))))

(defn- new-id [& args]
  (out task/new-task (vec args)))

(defn- commit-in-worktree [id file]
  (let [dir (str *dir* "/.worktrees/" id)]
    (spit (str dir "/" file) "x")
    (git dir "add" file)
    (git dir "commit" "-q" "--no-verify" "-m" (str "Add " file))))

(defn- fails-with [text f]
  (try (f) nil
       (catch clojure.lang.ExceptionInfo e (should-contain text (ex-message e)))))

(defn- conflicting-task
  "A task at done whose files a and b conflict with main."
  []
  (let [id (new-id "Quick" "--route" "coder")
        dir (str *dir* "/.worktrees/" id)]
    (out task/start [id])
    (doseq [[at text] [[dir "ours"] [*dir* "theirs"]]]
      (doseq [file ["a" "b"]] (spit (str at "/" file) text))
      (git at "add" "a" "b")
      (git at "commit" "-q" "--no-verify" "-m" text))
    (out task/handoff [id] {:audit-passed? true})
    id))

(defn- resolve-conflicts []
  (doseq [file ["a" "b"]] (spit (str *dir* "/" file) "resolved"))
  (git *dir* "add" "a" "b"))

(describe "a task through the pipeline"
  (around [it] (with-repo it))

  (it "is created at the first stage of its route and listed"
    (let [id (new-id "Add login" "-m" "Users sign in")]
      (should (re-matches #"add-login-[0-9a-f]{7}" id))
      (should= "specifier" (out task/stage [id]))
      (should= "specifier,coder,cleaner" (out task/route [id]))
      (should= [id] (str/split-lines (out task/next-task ["specifier"])))
      (should= "NO_TASK" (out task/next-task ["coder"]))
      (should= (str id "\tspecifier\t\tspecifier,coder,cleaner\tAdd login") (out task/ls []))
      (should= "" (out task/ls ["--stage" "coder"]))
      (should= "" (out task/ls ["--needs"]))))

  (it "takes a route by name or by list, and a body from a file"
    (let [body (str *dir* "/body.txt")
          _ (spit body "From a file\n")
          id (new-id "Quick fix" "--route" "quick" "-F" body)]
      (should= "coder" (out task/stage [id]))
      (should-contain "From a file" (out task/show [id]))
      (should= "cleaner" (out task/stage [(new-id "Tidy" "--route" "cleaner")]))))

  (it "stops at the review and moves on at the approval"
    (let [id (new-id "Add login")]
      (out task/review [id "spec" "ready"])
      (should= (str id "\tspecifier\tapproval\tspecifier,coder,cleaner\tAdd login") (out task/ls ["--needs"]))
      (should= "NO_TASK" (out task/next-task ["specifier"]))
      (fails-with "waiting" #(task/handoff [id] {}))
      (should-contain "HANDOFF" (out task/approve [id]))
      (should= "coder" (out task/stage [id]))))

  (it "does not audit the commit a second time when the approval hands it on"
    (write-conf! *dir* "roles.conf" "specifier frontier subagent audit\ncoder strong subagent audit\ncleaner fast subagent\n")
    (let [id (new-id "Add login")]
      (should-contain "AUDIT_REQUIRED" (out (fn [] (try (task/review [id]) (catch Exception _ nil)))))
      (should-contain "REVIEW" (out task/review [id]))
      (should-contain (str "HANDOFF: " id " specifier -> coder") (out task/approve [id]))))

  (it "keeps the task with the specifier on a rejection"
    (let [id (new-id "Add login")]
      (fails-with "not waiting for approval" #(task/reject [id "no"]))
      (out task/review [id])
      (should-contain "stays at stage:specifier" (out task/reject [id "Too vague"]))
      (should-contain "Too vague" (out task/show [id]))))

  (it "keeps the task waiting for approval when the handoff would fail"
    (let [id (new-id "Add login")]
      (out task/review [id])
      (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncleaner fast subagent\n")
      (fails-with "unknown role 'coder'" #(task/approve [id]))
      (should= (str id "\tspecifier\tapproval\tspecifier,coder,cleaner\tAdd login") (out task/ls ["--needs"]))))

  (it "does not approve a task that waits on nothing"
    (let [id (new-id "Add login")]
      (fails-with "not waiting for approval" #(task/approve [id]))))

  (it "refuses a handoff from a dirty worktree and one on another branch"
    (let [id (new-id "Quick" "--route" "coder")
          dir (str *dir* "/.worktrees/" id)]
      (out task/start [id])
      (spit (str dir "/dirty") "x")
      (fails-with "uncommitted changes" #(task/handoff [id] {:audit-passed? true}))
      (git dir "add" "dirty")
      (git dir "commit" "-q" "--no-verify" "-m" "Dirty")
      (git dir "checkout" "-q" "-b" "elsewhere")
      (fails-with "expected" #(task/handoff [id] {:audit-passed? true}))))

  (it "asks the audit once per commit before it records a handoff"
    (let [id (new-id "Quick" "--route" "coder,cleaner")]
      (out task/start [id])
      (commit-in-worktree id "a")
      (should-contain "AUDIT_REQUIRED" (out (fn [] (try (task/handoff [id] {}) (catch Exception _ nil)))))
      (should= "coder" (out task/stage [id]))
      (should-contain "HANDOFF" (out task/handoff [id "-m" "done" "coding"] {}))
      (should= "cleaner" (out task/stage [id]))))

  (it "answers a question before a handoff goes on"
    (let [id (new-id "Quick" "--route" "cleaner")]
      (should-contain "ASKED" (out task/ask [id "Which" "one?"]))
      (fails-with "waiting on the operator" #(task/handoff [id] {}))
      (should-contain "resumes at stage:cleaner" (out task/answer [id "the" "first"]))
      (should-contain "HANDOFF" (out task/handoff [id "-m" "x"] {}))))

  (it "runs from start to finish and shows the finished task"
    (let [id (new-id "Quick" "--route" "coder")
          dir (str *dir* "/.worktrees/" id)]
      (should-contain (str "DIR: " dir) (out task/start [id]))
      (commit-in-worktree id "a")
      (task/handoff [id] {:audit-passed? true})
      (should= "done" (out task/stage [id]))
      (should= "" (git dir "status" "--porcelain"))
      (should-contain "FINISHED" (out task/finish [id]))
      (should (store/finished? id))
      (should (not (fs/exists? dir)))
      (should-contain "STATUS: finished" (out task/show [id]))
      (fails-with "is finished" #(task/ask [id "q"]))
      (should= "" (out task/ls []))))

  (it "stops a conflicting finish with the files and resumes it once the merge is committed"
    (let [id (conflicting-task)
          dir (str *dir* "/.worktrees/" id)
          tip (git *dir* "rev-parse" (str "swarm/" id))]
      (should= {:status 1 :err (str "merge of swarm/" id " into main stopped on conflicts: a b;"
                                    " resolve them, commit, then run: sf finish " id)}
               (select-keys (captured #(task/finish [id])) [:status :err]))
      (should= tip (git *dir* "rev-parse" "MERGE_HEAD"))
      (should (fs/exists? dir))
      (resolve-conflicts)
      (should= {:status 1 :err "a merge is in progress; commit the merge first"}
               (select-keys (captured #(task/finish [id])) [:status :err]))
      (should= tip (git *dir* "rev-parse" "MERGE_HEAD"))
      (git *dir* "commit" "-q" "--no-verify" "-m" "Resolve")
      (let [resolved (git *dir* "log" "-1" "--format=%P %T")]
        (should= (str "FINISHED: " id " merged into main") (out task/finish [id]))
        (should= resolved (git *dir* "log" "-1" "--format=%P %T")))
      (should= (str "sf finished: " id) (git *dir* "log" "-1" "--format=%s"))
      (should-contain "SwarmForge-Merged-Into: main" (git *dir* "log" "-1" "--format=%B"))
      (should-not (fs/exists? dir))
      (should= "" (git *dir* "branch" "--list" (str "swarm/" id)))
      (should-contain "STATUS: finished" (out task/show [id]))
      (fails-with "is finished" #(task/finish [id]))))

  (it "keeps a resolved merge that has the finished record"
    (let [id (conflicting-task)]
      (captured #(task/finish [id]))
      (resolve-conflicts)
      (git *dir* "commit" "-q" "--no-verify" "--no-edit")
      (let [merge (git *dir* "rev-parse" "HEAD")]
        (should= (str "FINISHED: " id " merged into main") (out task/finish [id]))
        (should= merge (git *dir* "rev-parse" "HEAD")))
      (should= "" (git *dir* "branch" "--list" (str "swarm/" id)))))

  (it "refuses a finished task whose branch tip HEAD does not merge"
    (let [id (new-id "Quick" "--route" "coder")]
      (task/handoff [id "--to" "done"] {:audit-passed? true})
      (git *dir* "merge" "-q" "--squash" (str "swarm/" id))
      (git *dir* "commit" "-q" "--no-verify" "-m" "Squash")
      (should= {:status 1 :err (str "task " id " is finished")}
               (select-keys (captured #(task/finish [id])) [:status :err]))))

  (it "hands a task to done from any stage and refuses to finish an unfinished one"
    (let [id (new-id "Add login")]
      (fails-with "not done" #(task/finish [id]))
      (task/handoff [id "--to" "done" "-F" (str *dir* "/swarmforge/roles.conf")] {:audit-passed? true})
      (should= "done" (out task/stage [id]))))

  (it "moves a task onto a route that holds its stage and refuses one that does not"
    (let [id (new-id "Add login")]
      (should-contain "ROUTE" (out task/route [id "specifier,cleaner"]))
      (should= "specifier,cleaner" (out task/route [id]))
      (fails-with "does not contain" #(task/route [id "coder"]))))

  (it "tells a bad command line from a good one"
    (fails-with "new needs a title" #(task/new-task []))
    (fails-with "unknown new option" #(task/new-task ["Title" "--bogus"]))
    (fails-with "needs a value" #(task/new-task ["Title" "-m"]))
    (fails-with "needs a file" #(task/new-task ["Title" "-F"]))
    (fails-with "next needs a role" #(task/next-task []))
    (fails-with "unknown role" #(task/next-task ["nobody"]))
    (fails-with "no task" #(task/stage ["nothing"]))
    (fails-with "task id required" #(task/stage [""]))
    (let [id (new-id "Add login")]
      (fails-with "--to needs a value" #(task/handoff [id "--to"] {}))
      (fails-with "-m needs a value" #(task/review [id "-m"]))
      (fails-with "-F needs a file" #(task/review [id "-F"]))
      (fails-with "ask needs a question" #(task/ask [id]))
      (fails-with "answer needs text" #(task/answer [id]))
      (fails-with "reject needs a reason" #(task/reject [id]))
      (fails-with "unknown ls option" #(task/ls ["--bogus"]))))

  (it "saves the manifests on a handoff it records and loads them on start"
    (spit (str *dir* "/.git/info/exclude") ".metrics/\n")
    (let [a (new-id "Quick" "--route" "coder,cleaner")
          manifest #(str *dir* "/.worktrees/" % "/.metrics/mutate/a.edn")
          cached #(:exit (shell/run {:dir *dir*} "git" "cat-file" "-e" "swarmforge/metrics:mutate/a.edn"))]
      (out task/start [a])
      (fs/create-dirs (fs/parent (manifest a)))
      (spit (manifest a) "v1\n")
      (out task/ask [a "Which?"])
      (fails-with "waiting on the operator" #(task/handoff [a] {:audit-passed? true}))
      (should-not= 0 (cached))
      (out task/answer [a "This"])
      (should-contain (str "HANDOFF: " a " coder -> cleaner") (out task/handoff [a] {:audit-passed? true}))
      (should= 0 (cached))
      (let [b (new-id "Other" "--route" "coder")]
        (should-not-contain "metrics" (out task/start [b]))
        (should= "v1\n" (slurp (manifest b))))))

  (it "installs the hook and names the roles on init"
    (let [text (out task/init [])]
      (should-contain "roles: specifier coder cleaner" text)
      (should-contain "routes: feature quick" text)
      (should (fs/exists? (fs/path *dir* ".git" "hooks" "commit-msg"))))))
