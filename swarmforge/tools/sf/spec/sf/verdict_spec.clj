(ns sf.verdict-spec
  "sf verdict and sf rounds end to end in a throwaway repository."
  (:require [clojure.string :as str]
            [speclj.core :refer [around describe it should should= should-contain should-not-contain]]
            [sf.repo-support :refer [*dir* captured git with-repo write-conf!]]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.task :as task]))

(def ^:private judged
  {"roles.conf" "specifier frontier subagent\njudge strong subagent judge\ncoder strong subagent\ncleaner fast subagent\n"})

(defn- out [f & args]
  (str/trim (with-out-str (apply f args))))

(defn- worktree [id]
  (str *dir* "/.worktrees/" id))

(defn- commit!
  "Commits message in the task worktree, adding n lines to file when n is given."
  [id message & [file n]]
  (when file
    (spit (str (worktree id) "/" file) (str/join (repeat n "line\n")) :append true)
    (git (worktree id) "add" file))
  (git (worktree id) "commit" "-q" "--no-verify" "--allow-empty" "-m" message))

(defn- reviewed-task
  "A task at specifier whose first review has size 10: a one-line message and
  nine tracked lines."
  []
  (let [id (out task/new-task ["Judged" "-m" "x" "--route" "specifier,coder"])]
    (out task/start [id])
    (commit! id "Spec" "spec.md" 9)
    (out task/review [id])
    id))

(defn- rework! [id n]
  (commit! id "Rework" "spec.md" n)
  (out task/review [id]))

(defn- tip [id]
  (git *dir* "rev-parse" (str "swarm/" id)))

(defn- refused [id & args]
  (let [before (tip id)
        result (captured #(task/verdict (vec (cons id args))))]
    (should= 1 (:status result))
    (should= before (tip id))
    (:err result)))

(defn- rounds-of [id]
  (out task/rounds [id]))

(describe "sf verdict"
  (around [it] (with-repo judged it))

  (it "records continue as the judge's send-back, which keeps the stage and clears needs"
    (let [id (reviewed-task)]
      (should= (str "VERDICT: " id " continue") (out task/verdict [id "continue" "new" "findings"]))
      (let [state (store/state id)]
        (should= ["changes-requested" "judge" "continue" "new findings" "specifier" "none"]
                 ((juxt :record :from :verdict :text :stage :needs) state)))
      (should-contain "from: judge\nverdict: continue\nreason: new findings" (out task/start [id]))))

  (it "records the other verdicts and leaves the task waiting for approval"
    (doseq [v ["converged" "needs-operator" "stuck"]]
      (let [id (reviewed-task)]
        (should= (str "VERDICT: " id " " v) (out task/verdict [id v "why"]))
        (should= ["verdict" "judge" v "why" "specifier" "approval"]
                 ((juxt :record :from :verdict :text :stage :needs) (store/state id)))
        (should-contain "type: verdict\nfrom: judge\nverdict: " (out task/show [id]))
        (should-contain "HANDOFF:" (out task/approve [id])))))

  (it "lets the operator reject after a verdict"
    (let [id (reviewed-task)]
      (out task/verdict [id "stuck" "cap"])
      (should-contain "stays at stage:specifier" (out task/reject [id "no"]))))

  (it "refuses a second verdict in the round, and every verdict once the task moved on"
    (let [id (reviewed-task)]
      (out task/verdict [id "stuck" "x"])
      (should-contain "round 1 already has the verdict stuck" (refused id "converged" "y"))
      (out task/approve [id])
      (should-contain "task is at stage:coder" (refused id "converged" "y"))))

  (it "refuses a verdict it does not know, a blank text, and a task not waiting for approval"
    (let [id (reviewed-task)]
      (should-contain "not 'approve'" (refused id "approve" "x"))
      (should-contain "verdict needs a reason" (refused id "stuck"))
      (should-contain "verdict needs a reason" (refused id "stuck" "  "))
      (out task/verdict [id "continue" "x"])
      (should-contain "task is not waiting for approval" (refused id "stuck" "x"))))

  (it "is the operator's command"
    (let [id (reviewed-task)]
      (with-redefs [shell/env (fn [name default] (if (= "SWARMFORGE_DISPATCHED" name) "specifier" default))]
        (should-contain "verdict is the operator's; the dispatched specifier cannot run it" (refused id "stuck" "x")))))

  (it "refuses continue once the task has two automatic send-backs, and counts no operator send-back"
    (let [id (reviewed-task)]
      (out task/reject [id "operator"])
      (rework! id 0)
      (out task/verdict [id "continue" "r1"])
      (rework! id 0)
      (out task/verdict [id "continue" "r2"])
      (rework! id 0)
      (should-contain "LIMIT: cap 2 of 2 automatic send-backs used" (refused id "continue" "r3"))
      (should-contain (str "VERDICT: " id " stuck") (out task/verdict [id "stuck" "cap"]))))

  (it "refuses continue on a round more than half over its baseline, until the operator sends it back"
    (let [id (reviewed-task)]
      (out task/verdict [id "continue" "r1"])
      (rework! id 4)
      (should-contain "size:15 growth:50% verdict:-" (rounds-of id))
      (should-contain "VERDICT:" (out task/verdict [id "continue" "r2"]))
      (rework! id 0)
      (should-contain "LIMIT: growth size 16 is more than half over 10" (rounds-of id))
      (out task/verdict [id "stuck" "x"])
      (out task/reject [id "operator"])
      (rework! id 0)
      (should-contain "size:17 growth:0% verdict:-" (rounds-of id))
      (should-not-contain "LIMIT: growth" (rounds-of id))))

  (it "refuses continue with the growth line when the round is over the limit"
    (let [id (reviewed-task)]
      (out task/verdict [id "continue" "r1"])
      (rework! id 5)
      (should-contain "LIMIT: growth size 16 is more than half over 10" (refused id "continue" "r2"))))

  (it "refuses every verdict without a judge in roles.conf"
    (let [id (reviewed-task)]
      (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder strong subagent\ncleaner fast subagent\n")
      (should-contain "roles.conf has no judge" (refused id "stuck" "x")))))

(describe "sf rounds"
  (around [it] (with-repo judged it))

  (it "prints NO_ROUNDS for a task without a review"
    (let [id (out task/new-task ["Fresh" "-m" "x" "--route" "specifier,coder"])]
      (should= "NO_ROUNDS" (rounds-of id))))

  (it "prints each round with its size, growth, verdict and records, then the limits"
    (let [id (reviewed-task)
          first-sha (git *dir* "rev-parse" "--short" (str "swarm/" id "^"))]
      (out task/verdict [id "continue" "r1 first\nsecond"])
      (rework! id 0)
      (out task/verdict [id "continue" "r2"])
      (rework! id 6)
      (let [lines (str/split-lines (rounds-of id))]
        (should= (str "ROUND 1 " first-sha " size:10 growth:0% verdict:continue") (first lines))
        (should= ["  judge: r1 first" "    second"] (subvec lines 1 3))
        (should= "  judge: r2" (nth lines 4))
        (should-contain "size:11 growth:10% verdict:continue" (nth lines 3))
        (should-contain "ROUND 3 " (nth lines 5))
        (should-contain "size:18 growth:80% verdict:-" (nth lines 5))
        (should= ["LIMIT: cap 2 of 2 automatic send-backs used" "LIMIT: growth size 18 is more than half over 10"]
                 (subvec lines 6)))))

  (it "counts a superseded message only by its tracked lines, and trailers and blank lines not at all"
    (let [id (out task/new-task ["Superseded" "-m" "x" "--route" "specifier,coder"])]
      (out task/start [id])
      (commit! id "Spec\nmore\n\nBy specifier.\n\nLore-id: aaaa1111\nConstraint: x" "spec.md" 2)
      (commit! id "Rework\n\nBy specifier.\n\nLore-id: bbbb2222\nSupersedes: aaaa1111")
      (out task/review [id])
      (should-contain "size:4 growth:0%" (rounds-of id))))

  (it "leaves a merge of the base branch out of the size"
    (let [id (reviewed-task)]
      (spit (str *dir* "/base.md") (str/join (repeat 50 "base\n")))
      (git *dir* "add" "base.md")
      (git *dir* "commit" "-q" "--no-verify" "-m" "Base work")
      (git (worktree id) "merge" "-q" "--no-verify" "--no-edit" "main")
      (out task/reject [id "operator"])
      (out task/review [id])
      (should-contain "ROUND 2 " (rounds-of id))
      (should-contain "size:10 growth:0%" (last (filter #(str/starts-with? % "ROUND") (str/split-lines (rounds-of id)))))))

  (it "reads, records nothing, and runs for a dispatched role"
    (let [id (reviewed-task)
          before (tip id)]
      (with-redefs [shell/env (fn [name default] (if (= "SWARMFORGE_DISPATCHED" name) "coder" default))]
        (should-contain "ROUND 1 " (rounds-of id)))
      (should= before (tip id))))

  (it "reads a finished task"
    (let [id (reviewed-task)]
      (out task/verdict [id "converged" "ok"])
      (out task/approve [id])
      (out task/handoff [id] {:audit-passed? true})
      (out task/finish [id])
      (should-contain "verdict:converged" (rounds-of id))
      (should-not-contain "LIMIT" (rounds-of id)))))
