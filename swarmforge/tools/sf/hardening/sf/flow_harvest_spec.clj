(ns sf.flow-harvest-spec
  (:require [speclj.core :refer :all]
            [sf.flow :as flow]))

(describe "handoff-refusal at the specifier stage"
  (it "needs an approval record written by the operator"
    (let [state {:stage "specifier" :record "approval" :from "operator" :commit "c1" :needs "none"}]
      (should-be-nil (flow/handoff-refusal state))
      (should-contain "requires operator approval"
                      (flow/handoff-refusal (assoc state :from "specifier")))
      (should-contain "requires operator approval"
                      (flow/handoff-refusal (assoc state :record "review"))))))
