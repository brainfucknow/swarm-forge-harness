(ns sfjs.eslint-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [sfjs.eslint :as eslint]))

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
              (eslint/parse-report (fixture "eslint-report.json") "/work")))))

(deftest function-names-from-messages
  (is (= "withdraw" (eslint/function-name "Function 'withdraw' has a complexity of 4. Maximum allowed is 0.")))
  (is (= "m" (eslint/function-name "Static method 'm' has a complexity of 2. Maximum allowed is 0.")))
  (is (= "#p" (eslint/function-name "Private method #p has a complexity of 1. Maximum allowed is 0.")))
  (is (= "(anonymous)" (eslint/function-name "Arrow function has a complexity of 1. Maximum allowed is 0.")))
  (is (= "(anonymous)" (eslint/function-name "Class field initializer has a complexity of 1. Maximum allowed is 0."))))

(deftest eslint-messages-fall-back-to-defaults
  (let [report [{"filePath" "/w/a.ts"
                 "messages" [{"ruleId" "complexity" "line" 2 "message" "Function 'f' has a complexity of 1. Maximum allowed is 0."}
                             {"ruleId" "complexity" "line" 4 "endLine" 6 "column" 3
                              "message" "Function 'g' has a complexity of 2. Maximum allowed is 0."}
                             {"ruleId" "semi" "line" 1 "message" "Missing semicolon."}]}]]
    (is (= [{:file "a.ts" :line 2 :column 1 :end-line 2 :name "f" :complexity 1}
            {:file "a.ts" :line 4 :column 3 :end-line 6 :name "g" :complexity 2}]
           (eslint/parse-report report "/w")))))

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
           (eslint/fatal-messages report "/w"))))
  (is (= [] (eslint/fatal-messages [{"filePath" "/w/a.ts" "messages" [{"fatal" false "line" 1 "message" "m"}]}] "/w"))))

(deftest eslint-runs-only-complexity-with-its-own-config
  (let [config (eslint/config)]
    (is (str/includes? config "rules: { complexity: [\"warn\", 0] }"))
    (is (str/includes? config "parser: tseslint.parser"))
    (doseq [ignored ["**/node_modules/**" "**/tmp/**" "**/*.test.*" "**/__tests__/**"]]
      (is (str/includes? config (pr-str ignored)) ignored))))

(deftest eslint-command-line
  (is (= ["eslint" "--config" "tmp/eslint/eslint.config.mjs" "--no-inline-config" "--format" "json"
          "--output-file" "tmp/eslint/eslint.json" "src/a.ts"]
         (eslint/args eslint/config-path eslint/report-path ["src/a.ts"]))))
