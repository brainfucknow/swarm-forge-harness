(ns sfjs.reports-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [sfcore.crap :as crap]
            [sfcore.jscpd :as jscpd]
            [sfcore.stryker :as stryker]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(deftest strykerjs-report
  (let [mutants (stryker/parse-mutants (json/parse-string (slurp (str (fs/path fixtures "mutation.json")))))]
    (is (= ["Survived   src/ledger.ts:14  EqualityOperator  amount >= total"
            "NoCoverage src/ledger.ts:21  BooleanLiteral  true"]
           (map stryker/format-mutant (stryker/survivors mutants))))
    (is (= "mutation score 50.0% (CompileError 1, Ignored 1, Killed 1, NoCoverage 1, RuntimeError 1, Survived 1, Timeout 1)"
           (stryker/format-summary (stryker/summarize mutants))))))

(deftest empty-strykerjs-report
  (is (= "mutation score 100.0% (no mutants)" (stryker/format-summary (stryker/summarize [])))))

(deftest reviews-print-findings-then-the-summary
  (is (= {:lines ["mutation score 100.0% (no mutants)"] :status 0} (stryker/review [])))
  (is (= 1 (:status (stryker/review [{:file "a.ts" :line 1 :status "Survived" :mutator "M"}]))))
  (let [clone {:tokens 60 :lines 5 :sides [{:file "a.ts" :start 1 :end 5} {:file "b.ts" :start 3 :end 7}]}]
    (is (= {:lines ["0 of 1 clone(s) touch changed lines"] :status 0}
           (jscpd/review [clone] {"c.ts" #{1}} "/repo")))
    (is (= {:lines ["60 tokens  a.ts:1-5 (changed)  ==  b.ts:3-7" "1 of 1 clone(s) touch changed lines"] :status 1}
           (jscpd/review [clone] {"a.ts" #{2}} "/repo")))
    (is (= 1 (:status (jscpd/review [clone] nil nil)))))
  (let [unit {:crap 12.0 :complexity 4 :coverage 0.0 :file "a.ts" :line 3 :name "f"}]
    (is (= {:lines ["x" "1 function(s), 1 over CRAP 10"] :status 1}
           (crap/review [unit] (constantly "x") "function" nil)))
    (is (= 0 (:status (crap/review [(assoc unit :crap 4.0)] (constantly "x") "function" {}))))))
