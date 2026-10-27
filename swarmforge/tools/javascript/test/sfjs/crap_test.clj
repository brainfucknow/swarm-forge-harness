(ns sfjs.crap-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [sfjs.crap :as crap]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))
(defn- fixture [name] (json/parse-string (slurp (str (fs/path fixtures name)))))

(deftest eslint-complexity-messages
  (is (= [["src/ledger.ts" 3 "deposit" 1]
          ["src/ledger.ts" 10 "withdraw" 4]
          ["src/ledger.ts" 20 "settle" 2]
          ["src/ledger.ts" 22 "(anonymous)" 1]
          ["src/ledger.ts" 32 "noop" 1]
          ["src/ledger.ts" 34 "refund" 3]
          ["src/ledger.ts" 36 "#p" 1]]
         (map (juxt :file :line :name :complexity)
              (crap/parse-eslint (fixture "eslint-report.json") "/work")))))

(deftest function-names-from-messages
  (is (= "withdraw" (crap/function-name "Function 'withdraw' has a complexity of 4. Maximum allowed is 0.")))
  (is (= "m" (crap/function-name "Static method 'm' has a complexity of 2. Maximum allowed is 0.")))
  (is (= "#p" (crap/function-name "Private method #p has a complexity of 1. Maximum allowed is 0.")))
  (is (= "(anonymous)" (crap/function-name "Arrow function has a complexity of 1. Maximum allowed is 0.")))
  (is (= "(anonymous)" (crap/function-name "Class field initializer has a complexity of 1. Maximum allowed is 0."))))

(deftest own-statement-coverage
  (testing "nested functions' statements count only for the nested function"
    (let [entry (get (fixture "coverage-final.json") "/work/src/ledger.ts")]
      (is (= [["deposit" 1.0] ["withdraw" 0.25] ["settle" 0.5] ["(anonymous_3)" 0.0] ["noop" 1.0]]
             (map (juxt :name :coverage) (crap/function-coverage entry))))))
  (testing "a function with no statements is covered when it was called"
    (let [entry {"fnMap" {"0" {"name" "f" "loc" {"start" {"line" 1 "column" 0} "end" {"line" 1 "column" 9}}}}
                 "f" {"0" 0} "statementMap" {} "s" {}}]
      (is (= [0.0] (map :coverage (crap/function-coverage entry)))))))

(deftest scored-functions
  (let [scored (crap/score (crap/parse-eslint (fixture "eslint-report.json") "/work")
                           (fixture "coverage-final.json") "/work")]
    (is (= [["deposit" 3 8 1.0 1.0]
            ["withdraw" 10 17 0.25 10.75]
            ["settle" 20 30 0.5 2.5]
            ["(anonymous)" 22 25 0.0 2.0]
            ["noop" 32 32 1.0 1.0]
            ["refund" 34 34 0.0 12.0]
            ["#p" 36 36 0.0 2.0]]
           (map (juxt :name :line :last-line :coverage :crap) scored)))
    (is (every? #(= "src/ledger.ts" (:file %)) scored))))

(deftest a-function-whose-head-starts-before-its-coverage-range
  (testing "an ESLint head inside no range falls back to the innermost range on that line"
    (let [coverage {"/w/a.ts" {"fnMap" {"0" {"name" "f" "loc" {"start" {"line" 5 "column" 20} "end" {"line" 9 "column" 1}}}}
                               "f" {"0" 1} "statementMap" {"0" {"start" {"line" 6 "column" 2} "end" {"line" 6 "column" 9}}} "s" {"0" 1}}}
          eslint [{:file "a.ts" :line 5 :column 1 :end-line 5 :name "f" :complexity 1}]]
      (is (= [[1.0 5 9]] (map (juxt :coverage :line :last-line) (crap/score eslint coverage "/w")))))))

(def ^:private entry-for
  (fn [fn-map & {:keys [f statements s]}]
    (cond-> {"fnMap" fn-map "statementMap" (or statements {})}
      f (assoc "f" f)
      s (assoc "s" s))))

(defn- loc [start-line start-col end-line end-col]
  {"start" {"line" start-line "column" start-col} "end" {"line" end-line "column" end-col}})

(deftest eslint-messages-fall-back-to-defaults
  (let [report [{"filePath" "/w/a.ts"
                 "messages" [{"ruleId" "complexity" "line" 2 "message" "Function 'f' has a complexity of 1. Maximum allowed is 0."}
                             {"ruleId" "complexity" "line" 4 "endLine" 6 "column" 3
                              "message" "Function 'g' has a complexity of 2. Maximum allowed is 0."}
                             {"ruleId" "semi" "line" 1 "message" "Missing semicolon."}]}]]
    (is (= [{:file "a.ts" :line 2 :column 1 :end-line 2 :name "f" :complexity 1}
            {:file "a.ts" :line 4 :column 3 :end-line 6 :name "g" :complexity 2}]
           (crap/parse-eslint report "/w")))))

(deftest coverage-ranges-include-their-boundaries
  (testing "a statement starting where the function starts or ends counts"
    (let [entry (entry-for {"0" {"name" "f" "loc" (loc 1 0 3 5)}}
                           :f {"0" 1}
                           :statements {"0" {"start" {"line" 1 "column" 0} "end" {"line" 1 "column" 4}}
                                        "1" {"start" {"line" 3 "column" 5} "end" {"line" 3 "column" 6}}}
                           :s {"0" 1 "1" 0})]
      (is (= [0.5] (map :coverage (crap/function-coverage entry))))))
  (testing "a missing column opens the range at its start and runs to the end of its line"
    (let [entry (entry-for {"0" {"name" "f" "loc" {"start" {"line" 1 "column" nil} "end" {"line" 1 "column" nil}}}}
                           :f {"0" 1}
                           :statements {"0" {"start" {"line" 1 "column" 0} "end" {"line" 1 "column" 9}}
                                        "1" {"start" {"line" 1 "column" 99999} "end" {"line" 1 "column" 99999}}}
                           :s {"0" 1 "1" 0})]
      (is (= [0.5] (map :coverage (crap/function-coverage entry)))))))

(deftest counts-missing-from-the-report-are-zero
  (is (= [0.0] (map :coverage (crap/function-coverage
                               (entry-for {"0" {"name" "f" "loc" (loc 1 0 2 0)}})))))
  (is (= [0.0] (map :coverage (crap/function-coverage
                               (entry-for {"0" {"name" "f" "loc" (loc 1 0 2 0)}}
                                          :f {"0" 1}
                                          :statements {"0" {"start" {"line" 1 "column" 1} "end" {"line" 1 "column" 2}}}))))))

(deftest a-function-reaches-back-to-the-start-of-its-coverage-range
  (let [coverage {"/w/a.ts" (entry-for {"0" {"name" "f" "loc" (loc 5 0 9 1)}} :f {"0" 1})}
        eslint [{:file "a.ts" :line 6 :column 3 :end-line 7 :name "f" :complexity 1}]]
    (is (= [[5 9]] (map (juxt :line :last-line) (crap/score eslint coverage "/w"))))))

(deftest which-function-an-eslint-head-belongs-to
  (let [coverage {"/w/a.ts" (entry-for {"0" {"name" "outer" "loc" (loc 3 0 3 40)}
                                         "1" {"name" "inner" "loc" (loc 3 5 3 20)}}
                                        :f {"0" 1 "1" 0})}
        covered-by (fn [column]
                     (:coverage (first (crap/score [{:file "a.ts" :line 3 :column column :end-line 3 :name "f" :complexity 1}]
                                                   coverage "/w"))))]
    (is (= 1.0 (covered-by 5)) "column 5 is offset 4: before the inner function, so the outer one")
    (is (= 0.0 (covered-by 6)) "column 6 is offset 5: the inner function starts there")))

(deftest fatal-eslint-messages-are-analysis-errors
  (let [report [{"filePath" "/w/src/a.ts"
                 "messages" [{"fatal" true "severity" 2 "line" 3 "message" "Parsing error: Unexpected token )"}
                             {"severity" 1 "line" 1 "message" "File ignored because of a matching ignore pattern."}
                             {"ruleId" "complexity" "severity" 2 "line" 5
                              "message" "Function 'f' has a complexity of 1. Maximum allowed is 0."}]}
                {"filePath" "/w/src/b.ts"
                 "messages" [{"fatal" true "severity" 2 "line" 21 "message" "Parsing error: Declaration expected."}]}]]
    (is (= ["src/a.ts:3: Parsing error: Unexpected token )"
            "src/b.ts:21: Parsing error: Declaration expected."]
           (crap/fatal-messages report "/w"))))
  (is (= [] (crap/fatal-messages [{"filePath" "/w/a.ts" "messages" [{"fatal" false "line" 1 "message" "m"}]}] "/w"))))
