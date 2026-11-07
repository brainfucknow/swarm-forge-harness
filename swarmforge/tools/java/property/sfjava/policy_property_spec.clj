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
                       (and (every? present selected)
                            (every? #(clojure.string/starts-with? % "src/main/java/") selected))))]
      (should (:pass? (tc/quick-check 100 property)))))
  (it "treats the two sides of a duplicate symmetrically"
    (let [property (prop/for-all [line (gen/choose 1 30)]
                     (let [left {:file "src/main/java/A.java" :start-line 4 :end-line 10}
                           right {:file "src/main/java/B.java" :start-line 12 :end-line 20}
                           changed {"src/main/java/A.java" #{line}}]
                       (= (count (reports/relevant-duplicates (pr-str {:candidates [{:score 1 :left left :right right}]}) changed nil))
                          (count (reports/relevant-duplicates (pr-str {:candidates [{:score 1 :left right :right left}]}) changed nil)))))]
      (should (:pass? (tc/quick-check 100 property))))))
