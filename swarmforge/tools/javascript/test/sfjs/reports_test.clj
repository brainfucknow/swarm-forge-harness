(ns sfjs.reports-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
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
