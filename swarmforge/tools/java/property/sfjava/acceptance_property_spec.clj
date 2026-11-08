(ns sfjava.acceptance-property-spec
  (:require [speclj.core :refer :all]
            [sfjs.acceptance.runtime :as runtime]))

(describe "Mutation selection invariants"
  (it "locates each isolated change regardless of scenario count or position"
    (doseq [size (range 1 7)
            index (range size)
            :let [original {"name" "probe" "scenarios" (mapv #(hash-map "value" %) (range size))}
                  changed (assoc-in original ["scenarios" index "value"] (+ size index))]]
      (should= index (runtime/changed-scenario original changed))))
  (it "requires full verification when changes extend beyond one scenario"
    (doseq [size (range 2 7)
            :let [original {"name" "probe" "scenarios" (mapv #(hash-map "value" %) (range size))}
                  first-change (assoc-in original ["scenarios" 0 "value"] size)]]
      (should-be-nil (runtime/changed-scenario original (assoc first-change "name" "different")))
      (should-be-nil (runtime/changed-scenario original (assoc-in first-change ["scenarios" 1 "value"] size)))
      (should-be-nil (runtime/changed-scenario original (update first-change "scenarios" conj {"value" size}))))))
