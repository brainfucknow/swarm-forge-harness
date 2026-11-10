(ns sf.flow-spec
  (:require [speclj.core :refer [describe it should= should-be-nil should should-not]]
            [sf.flow :as flow]))

(def ^:private at-coder {:stage "coder" :route "specifier,coder,cleaner" :needs "none"})

(describe "route-list"
  (it "splits a route at its commas"
    (should= ["coder" "qa"] (flow/route-list "coder,qa")))

  (it "is empty for a blank route"
    (should= [] (flow/route-list ""))
    (should= [] (flow/route-list nil))))

(describe "waiting?"
  (it "is false when the task needs no one"
    (should-not (flow/waiting? at-coder)))

  (it "is true when the task needs the operator or an approval"
    (should (flow/waiting? (assoc at-coder :needs "operator")))
    (should (flow/waiting? (assoc at-coder :needs "approval")))))

(describe "next-stage"
  (it "is the role after the stage on the route"
    (should= "cleaner" (flow/next-stage at-coder nil)))

  (it "is done after the last role"
    (should= "done" (flow/next-stage (assoc at-coder :stage "cleaner") "")))

  (it "is the role that --to names"
    (should= "qa" (flow/next-stage at-coder "qa"))))

(describe "to-refusal"
  (it "lets --to name the stage or a later role on the route"
    (should-be-nil (flow/to-refusal at-coder nil))
    (should-be-nil (flow/to-refusal at-coder "coder"))
    (should-be-nil (flow/to-refusal at-coder "cleaner")))

  (it "lets --to name done only after the route's last role"
    (should-be-nil (flow/to-refusal (assoc at-coder :stage "cleaner") "done"))
    (should= "--to done skips the rest of route 'specifier,coder,cleaner' after stage:coder; hand off to the next role"
             (flow/to-refusal at-coder "done")))

  (it "refuses an earlier role and a role off the route"
    (should= "--to specifier is not on route 'specifier,coder,cleaner' at or after stage:coder"
             (flow/to-refusal at-coder "specifier"))
    (should= "--to qa is not on route 'specifier,coder,cleaner' at or after stage:coder"
             (flow/to-refusal at-coder "qa"))))

(describe "refusals"
  (it "refuses a handoff while the task waits on the operator"
    (should-be-nil (flow/handoff-refusal at-coder))
    (should= "task is waiting on the operator (needs:operator); handoff refused"
             (flow/handoff-refusal (assoc at-coder :needs "operator"))))

  (it "refuses an approval the task does not wait for"
    (should-be-nil (flow/approval-refusal (assoc at-coder :needs "approval")))
    (should= "task is not waiting for approval" (flow/approval-refusal at-coder)))

  (it "requires an operator approval to leave the specifier stage"
    (doseq [record ["created" "review" "changes-requested" "answer"]]
      (should= "specification requires operator approval; run sf review and sf approve"
               (flow/handoff-refusal {:stage "specifier" :needs "none" :record record})))
    (should-be-nil (flow/handoff-refusal {:stage "specifier" :needs "none"
                                        :record "approval" :from "operator" :commit "c1"} "c1")))

  (it "binds approval and handoff to the reviewed role commit"
    (let [pending {:stage "specifier" :needs "approval"}
          approved {:stage "specifier" :needs "none" :record "approval" :from "operator" :commit "c1"}]
      (should-be-nil (flow/approval-refusal pending "c1" "c1"))
      (should= "specification changed since review; run sf review again"
               (flow/approval-refusal pending "c1" "c2"))
      (should= "specification changed since approval; run sf review again"
               (flow/handoff-refusal approved "c2"))))

  (it "refuses a route without the current stage, unless the task is done"
    (should-be-nil (flow/route-refusal at-coder "coder,qa"))
    (should-be-nil (flow/route-refusal {:stage "done"} "qa"))
    (should= "route 'qa' does not contain the current stage 'coder'" (flow/route-refusal at-coder "qa"))))

(describe "records"
  (it "starts a task at the first role of its route"
    (should= {:record "created" :from "operator" :text "Add login\n\nUsers log in."
              :stage "coder" :route "coder,qa" :needs "none"}
             (flow/created "Add login" "Users log in." "coder,qa")))

  (it "carries the state into the next record under its own fields"
    (should= {:record "question" :from "coder" :stage "coder" :route "specifier,coder,cleaner"
              :needs "operator" :text "Which?"}
             (flow/follow (assoc at-coder :sha "abc" :text "old") "question" "coder"
                          {:text "Which?" :needs "operator"})))

  (it "hands the role's commit on with the summary"
    (should= {:record "handoff" :from "coder" :stage "cleaner" :route "specifier,coder,cleaner"
              :needs "none" :commit "abc1234" :text "built"}
             (flow/handoff at-coder "coder" "cleaner" "abc1234" " built ")))

  (it "says the work is complete when the summary is blank"
    (should= "coder work complete" (:text (flow/handoff at-coder "coder" "cleaner" "abc1234" nil))))

  (it "records the merge of a finished task"
    (should= {:record "finished" :from "operator" :stage "done" :route "coder" :needs "none"
              :merged-into "main"}
             (flow/finished {:route "coder"} "main"))))

(def ^:private records
  [{:record "created" :sha "c1" :commit "c1"}
   {:record "handoff" :sha "h1" :commit "r1"}
   {:record "question" :sha "q1" :commit "r1"}
   {:record "finished" :sha "f1" :commit "m1"}])

(describe "commits"
  (it "is the tip, or the role's commit under the newest record at the tip"
    (should= "r2" (flow/role-commit "r2" (records 2)))
    (should= "r1" (flow/role-commit "q1" (records 2))))

  (it "finds the commit the branch started from"
    (should= "c1" (flow/created-commit records))
    (should-be-nil (flow/created-commit (rest records))))

  (it "finds the commit of the newest created, review or handoff record"
    (should= "r1" (flow/last-milestone-commit records))))

(describe "what sf finish does with a done task"
  (it "merges a branch that HEAD neither contains nor has the finished record of"
    (should= :merge (flow/finish-action false "t1" false)))

  (it "keeps HEAD when HEAD's history has the finished record and the branch tip"
    (should= :keep (flow/finish-action true "t1" true)))

  (it "stops when the branch moved after the recorded merge"
    (should= :unmerged (flow/finish-action true "t1" false)))

  (it "records the finished merge on HEAD when HEAD contains the branch tip without the record"
    (should= :record (flow/finish-action false "t1" true)))

  (it "refuses a task without a branch"
    (should-be-nil (flow/finish-action true nil false))
    (should-be-nil (flow/finish-action false nil true))))

(describe "operator commands"
  (it "are refused to a dispatched role or gate"
    (should= "approve is the operator's; the dispatched specifier cannot run it"
             (flow/operator-refusal "approve" "specifier")))

  (it "run when sf dispatch started nothing"
    (should-be-nil (flow/operator-refusal "approve" nil))
    (should-be-nil (flow/operator-refusal "approve" ""))))

(describe "operator-record"
  (it "finds the first record the operator wrote"
    (should= {:record "approval" :from "operator"}
             (flow/operator-record [{:record "review" :from "specifier"}
                                    {:record "approval" :from "operator"}
                                    {:record "handoff" :from "specifier"}])))

  (it "is nil when roles wrote every record"
    (should-be-nil (flow/operator-record [{:record "review" :from "specifier"}]))
    (should-be-nil (flow/operator-record []))))

