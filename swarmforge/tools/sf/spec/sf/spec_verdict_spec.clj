(ns sf.spec-verdict-spec
  "sf spec-verdict and sf spec-revisions end to end in a throwaway repository."
  (:require [clojure.string :as str]
            [speclj.core :refer [around describe it should should= should-contain should-not-contain]]
            [sf.repo-support :refer [*dir* captured git with-repo write-conf!]]
            [sf.shell :as shell]
            [sf.store :as store]
            [sf.task :as task]))

(def ^:private spec-judged
  {"roles.conf" "specifier frontier subagent\nspec-judge strong subagent spec-judge\ncoder strong subagent\ncleaner fast subagent\n"})

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
        result (captured #(task/spec-verdict (vec (cons id args))))]
    (should= 1 (:status result))
    (should= before (tip id))
    (:err result)))

(defn- revisions-of [id & flags]
  (out task/spec-revisions (vec (cons id flags))))

(describe "sf spec-verdict"
  (around [it] (with-repo spec-judged it))

  (it "records continue as the spec-judge's send-back, which keeps the stage and clears needs"
    (let [id (reviewed-task)]
      (should= (str "SPEC-VERDICT: " id " continue") (out task/spec-verdict [id "continue" "new" "findings"]))
      (let [state (store/state id)]
        (should= ["changes-requested" "spec-judge" "continue" "new findings" "specifier" "none"]
                 ((juxt :record :from :spec-verdict :text :stage :needs) state)))
      (should-contain "from: spec-judge\nspec-verdict: continue\nreason: new findings" (out task/start [id]))))

  (it "records the other spec verdicts and leaves the task waiting for approval"
    (doseq [v ["converged" "needs-operator" "stuck"]]
      (let [id (reviewed-task)]
        (should= (str "SPEC-VERDICT: " id " " v) (out task/spec-verdict [id v "why"]))
        (should= ["spec-verdict" "spec-judge" v "why" "specifier" "approval"]
                 ((juxt :record :from :spec-verdict :text :stage :needs) (store/state id)))
        (should-contain "type: spec-verdict\nfrom: spec-judge\nspec-verdict: " (out task/show [id]))
        (should-contain "HANDOFF:" (out task/approve [id])))))

  (it "lets the operator reject after a spec verdict"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "stuck" "cap"])
      (should-contain "stays at stage:specifier" (out task/reject [id "no"]))))

  (it "refuses a second spec verdict in the spec revision, and every spec verdict once the task moved on"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "stuck" "x"])
      (should-contain "spec revision 1 already has the spec verdict stuck" (refused id "converged" "y"))
      (out task/approve [id])
      (should-contain "task is at stage:coder" (refused id "converged" "y"))))

  (it "refuses a spec verdict it does not know, a blank text, and a task not waiting for approval"
    (let [id (reviewed-task)]
      (should-contain "not 'approve'" (refused id "approve" "x"))
      (should-contain "spec verdict needs a reason" (refused id "stuck"))
      (should-contain "spec verdict needs a reason" (refused id "stuck" "  "))
      (out task/spec-verdict [id "continue" "x"])
      (should-contain "task is not waiting for approval" (refused id "stuck" "x"))))

  (it "is the operator's command"
    (let [id (reviewed-task)]
      (with-redefs [shell/env (fn [name default] (if (= "SWARMFORGE_DISPATCHED" name) "specifier" default))]
        (should-contain "spec-verdict is the operator's; the dispatched specifier cannot run it" (refused id "stuck" "x")))))

  (it "refuses continue once the task has two automatic send-backs, and counts no operator send-back"
    (let [id (reviewed-task)]
      (out task/reject [id "operator"])
      (rework! id 0)
      (out task/spec-verdict [id "continue" "r1"])
      (rework! id 0)
      (out task/spec-verdict [id "continue" "r2"])
      (rework! id 0)
      (should-contain "LIMIT: cap 2 of 2 automatic send-backs used" (refused id "continue" "r3"))
      (should-contain (str "SPEC-VERDICT: " id " stuck") (out task/spec-verdict [id "stuck" "cap"]))))

  (it "refuses continue on a spec revision more than half over its baseline, until the operator sends it back"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "continue" "r1"])
      (rework! id 4)
      (should-contain "size:15 growth:50% total:50% spec-verdict:-" (revisions-of id))
      (should-contain "SPEC-VERDICT:" (out task/spec-verdict [id "continue" "r2"]))
      (rework! id 0)
      (should-contain "LIMIT: growth size 16 is more than half over 10" (revisions-of id))
      (out task/spec-verdict [id "stuck" "x"])
      (out task/reject [id "operator"])
      (rework! id 0)
      (should-contain "size:17 growth:0% total:70% spec-verdict:-" (revisions-of id))
      (should-not-contain "LIMIT: growth" (revisions-of id))
      (should-contain "NOTE: total growth 70% over spec revision 1 (10); not a limit" (revisions-of id))))

  (it "refuses continue with the growth line when the spec revision is over the limit"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "continue" "r1"])
      (rework! id 5)
      (should-contain "LIMIT: growth size 16 is more than half over 10" (refused id "continue" "r2"))))

  (it "refuses every spec verdict without a spec-judge in roles.conf"
    (let [id (reviewed-task)]
      (write-conf! *dir* "roles.conf" "specifier frontier subagent\ncoder strong subagent\ncleaner fast subagent\n")
      (should-contain "roles.conf has no spec-judge" (refused id "stuck" "x")))))

(describe "sf spec-revisions"
  (around [it] (with-repo spec-judged it))

  (it "prints NO_SPEC_REVISIONS for a task without a review"
    (let [id (out task/new-task ["Fresh" "-m" "x" "--route" "specifier,coder"])]
      (should= "NO_SPEC_REVISIONS" (revisions-of id))))

  (it "prints each spec revision with its size, growth, total, spec verdict and, with --full, records, then the limits"
    (let [id (reviewed-task)
          first-sha (git *dir* "rev-parse" "--short" (str "swarm/" id "^"))]
      (out task/spec-verdict [id "continue" "r1 first\nsecond"])
      (rework! id 0)
      (out task/spec-verdict [id "continue" "r2"])
      (rework! id 6)
      (let [lines (str/split-lines (revisions-of id "--full"))]
        (should= (str "SPEC-REVISION 1 " first-sha " size:10 growth:0% total:0% spec-verdict:continue") (first lines))
        (should= ["  spec-judge: r1 first" "    second"] (subvec lines 1 3))
        (should= "  spec-judge: r2" (nth lines 4))
        (should-contain "size:11 growth:10% total:10% spec-verdict:continue" (nth lines 3))
        (should-contain "SPEC-REVISION 3 " (nth lines 5))
        (should-contain "size:18 growth:80% total:80% spec-verdict:-" (nth lines 5))
        (should= ["LIMIT: cap 2 of 2 automatic send-backs used" "LIMIT: growth size 18 is more than half over 10"]
                 (subvec lines 6)))
      (should= ["SPEC-REVISION 1" "SPEC-REVISION 2" "SPEC-REVISION 3"
                "LIMIT: cap 2 of 2 automatic send-backs used" "LIMIT: growth size 18 is more than half over 10"]
               (map #(str/replace % #"^(SPEC-REVISION \d+) .*" "$1") (str/split-lines (revisions-of id))))))

  (it "prints the total over spec revision 1 and a NOTE that is not a limit after an operator send-back"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "needs-operator" "decide"])
      (out task/reject [id "more"])
      (rework! id 5)
      (let [lines (str/split-lines (revisions-of id))]
        (should-contain "size:10 growth:0% total:0% spec-verdict:needs-operator" (first lines))
        (should-contain "size:16 growth:0% total:60% spec-verdict:-" (second lines))
        (should= ["NOTE: total growth 60% over spec revision 1 (10); not a limit"] (subvec lines 2)))
      (should= (str "SPEC-VERDICT: " id " continue") (out task/spec-verdict [id "continue" "again"]))
      (should-contain "size:16 growth:0% total:60% spec-verdict:continue" (revisions-of id))))

  (it "prints the spec verdict and send-back texts only with --full"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "needs-operator" "decide"])
      (out task/reject [id "more"])
      (should-not-contain "  spec-judge: decide" (revisions-of id))
      (should-not-contain "  operator: more" (revisions-of id))
      (should-contain "  spec-judge: decide\n  operator: more" (revisions-of id "--full"))))

  (it "refuses any argument after the id but --full"
    (let [id (reviewed-task)]
      (doseq [args [[id "--fuller"] [id "--full" "x"] ["--full" id]]]
        (let [result (captured #(task/spec-revisions args))]
          (should= 1 (:status result))
          (should-contain "spec-revisions takes <id> [--full]" (:err result))))))

  (it "counts a superseded message only by its tracked lines, and trailers and blank lines not at all"
    (let [id (out task/new-task ["Superseded" "-m" "x" "--route" "specifier,coder"])]
      (out task/start [id])
      (commit! id "Spec\nmore\n\nBy specifier.\n\nLore-id: aaaa1111\nConstraint: x" "spec.md" 2)
      (commit! id "Rework\n\nBy specifier.\n\nLore-id: bbbb2222\nSupersedes: aaaa1111")
      (out task/review [id])
      (should-contain "size:4 growth:0%" (revisions-of id))))

  (it "leaves a merge of the base branch out of the size"
    (let [id (reviewed-task)]
      (spit (str *dir* "/base.md") (str/join (repeat 50 "base\n")))
      (git *dir* "add" "base.md")
      (git *dir* "commit" "-q" "--no-verify" "-m" "Base work")
      (git (worktree id) "merge" "-q" "--no-verify" "--no-edit" "main")
      (out task/reject [id "operator"])
      (out task/review [id])
      (should-contain "SPEC-REVISION 2 " (revisions-of id))
      (should-contain "size:10 growth:0%" (last (filter #(str/starts-with? % "SPEC-REVISION") (str/split-lines (revisions-of id)))))))

  (it "reads, records nothing, and runs for a dispatched role"
    (let [id (reviewed-task)
          before (tip id)]
      (with-redefs [shell/env (fn [name default] (if (= "SWARMFORGE_DISPATCHED" name) "coder" default))]
        (should-contain "SPEC-REVISION 1 " (revisions-of id)))
      (should= before (tip id))))

  (it "reads a finished task"
    (let [id (reviewed-task)]
      (out task/spec-verdict [id "converged" "ok"])
      (out task/approve [id])
      (out task/handoff [id] {:reviewed? true})
      (out task/finish [id])
      (should-contain "spec-verdict:converged" (revisions-of id))
      (should-not-contain "LIMIT" (revisions-of id)))))
