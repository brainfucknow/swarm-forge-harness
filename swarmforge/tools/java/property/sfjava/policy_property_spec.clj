(ns sfjava.policy-property-spec
  (:require [speclj.core :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sfjava.policy :as policy]
            [sfjava.reports :as reports]))

(describe "Java selection properties"
  (it "never selects tests or absent files as production targets"
    (let [property (prop/for-all [ids (gen/vector gen/nat)]
                     (let [production (map #(str "src/main/java/Demo" % ".java") ids)
                           tests (map #(str "src/test/java/Demo" % "Test.java") ids)
                           present (set (take-nth 2 production))
                           selected (policy/production-files (concat tests production) present)]
                       (= (vec (sort (filter present production))) selected
                          (policy/production-files (reverse selected) present))))]
      (should (:pass? (tc/quick-check 100 property)))))
  (it "applies the same Java baseline to both runtime output formats"
    (let [property (prop/for-all [major (gen/choose 1 500)
                                minor gen/nat
                                vendor (gen/elements ["java" "openjdk"])]
                     (let [version (str major "." minor ".0")
                           quoted (str vendor " version \"" version "\"")
                           stdout (str vendor " " version " 2026-01-01")]
                       (= (>= major 17)
                          (policy/supported-java? quoted)
                          (policy/supported-java? stdout))))]
      (should (:pass? (tc/quick-check 200 property)))))
  (it "selects a duplicate exactly when changed lines intersect either interval"
    (let [property (prop/for-all [start (gen/choose 1 50)
                                width (gen/choose 0 20)
                                changed-lines (gen/set (gen/choose 1 80))]
                     (let [end (+ start width)
                           candidate {:score 0.9
                                      :left {:file "src/main/java/A.java" :start-line start :end-line end}
                                      :right {:file "src/main/java/B.java" :start-line 1 :end-line 2}}
                           findings (reports/relevant-duplicates
                                     (pr-str {:candidates [candidate]})
                                     {"src/main/java/A.java" changed-lines} nil)
                           overlap? (boolean (some #(<= start % end) changed-lines))]
                       (= (if overlap? [candidate] []) findings)))]
      (should (:pass? (tc/quick-check 200 property)))))
  (it "treats the two sides of a duplicate symmetrically"
    (let [property (prop/for-all [line (gen/choose 1 30)]
                     (let [left {:file "src/main/java/A.java" :start-line 4 :end-line 10}
                           right {:file "src/main/java/B.java" :start-line 12 :end-line 20}
                           changed {"src/main/java/A.java" #{line}}]
                       (= (count (reports/relevant-duplicates (pr-str {:candidates [{:score 1 :left left :right right}]}) changed nil))
                          (count (reports/relevant-duplicates (pr-str {:candidates [{:score 1 :left right :right left}]}) changed nil)))))]
      (should (:pass? (tc/quick-check 100 property))))))
