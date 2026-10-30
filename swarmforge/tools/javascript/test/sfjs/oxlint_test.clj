(ns sfjs.oxlint-test
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfjs.oxlint :as oxlint]))

(defn- message-of [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-message e))))

(defn- diagnostic [code message file line column]
  {"message" message "code" code "severity" "warning" "filename" file
   "labels" [{"span" {"offset" 7 "length" 71 "line" line "column" column}}]})

(deftest function-names-from-messages
  (is (= "withdraw" (oxlint/function-name "function `withdraw` has a complexity of 4. Maximum allowed is 0.")))
  (is (= "withdraw" (oxlint/function-name "method `withdraw` has a complexity of 4. Maximum allowed is 0.")))
  (is (= "withdraw" (oxlint/function-name "getter `withdraw` has a complexity of 4. Maximum allowed is 0.")))
  (is (= "#priv" (oxlint/function-name "private method `priv` has a complexity of 2. Maximum allowed is 0.")))
  (is (= "(anonymous)" (oxlint/function-name "function has a complexity of 2. Maximum allowed is 0.")))
  (is (= "(anonymous)" (oxlint/function-name "class static block has a complexity of 2. Maximum allowed is 0."))))

(deftest complexity-diagnostics
  (let [report {"diagnostics" [(diagnostic "eslint(complexity)" "function `withdraw` has a complexity of 12. Maximum allowed is 0."
                                           "src/ledger.ts" 10 8)
                               (diagnostic "eslint(no-debugger)" "`debugger` statement is not allowed" "src/ledger.ts" 12 1)
                               (diagnostic "eslint(complexity)" "function has a complexity of 2. Maximum allowed is 0."
                                           "/w/src/other.js" 3 18)]
                "number_of_rules" 1}]
    (is (= [{:file "src/ledger.ts" :line 10 :column 8 :end-line 10 :name "withdraw" :complexity 12}
            {:file "src/other.js" :line 3 :column 18 :end-line 3 :name "(anonymous)" :complexity 2}]
           (oxlint/parse-report report "/w")))))

(deftest a-diagnostic-without-a-column-starts-in-column-1
  (let [report {"diagnostics" [{"message" "function `f` has a complexity of 2. Maximum allowed is 0."
                                "code" "eslint(complexity)" "filename" "/w/src/f.js"
                                "labels" [{"span" {"offset" 0 "length" 9 "line" 4}}]}]}]
    (is (= [{:file "src/f.js" :line 4 :column 1 :end-line 4 :name "f" :complexity 2}]
           (oxlint/parse-report report "/w")))))

(deftest diagnostics-without-a-code-are-analysis-errors
  (let [report {"diagnostics" [{"message" "Expected `,` or `}` but found `decimal`" "severity" "error"
                                "filename" "/w/src/bad.ts"
                                "labels" [{"label" "`,` or `}` expected" "span" {"offset" 36 "length" 1 "line" 2 "column" 14}}
                                          {"label" "Opened here" "span" {"offset" 32 "length" 1 "line" 9 "column" 10}}]}
                               (diagnostic "eslint(complexity)" "function `h` has a complexity of 2. Maximum allowed is 0."
                                           "/w/src/ok.ts" 1 8)
                               {"message" "Identifier `a` has already been declared" "severity" "error"
                                "filename" "src/dup.ts" "labels" [{"span" {"line" 1 "column" 5}}]}
                               {"message" "Failed to open file /w/src/x.ts with error \"stream did not contain valid UTF-8\""
                                "severity" "error" "labels" []}]}]
    (is (= ["oxlint could not analyze src/bad.ts:2: Expected `,` or `}` but found `decimal`"
            "oxlint could not analyze src/dup.ts:1: Identifier `a` has already been declared"
            "oxlint could not analyze: Failed to open file /w/src/x.ts with error \"stream did not contain valid UTF-8\""]
           (oxlint/analysis-errors report "/w"))))
  (testing "a report with only coded diagnostics"
    (is (= [] (oxlint/analysis-errors {"diagnostics" [(diagnostic "eslint(no-debugger)" "m" "/w/a.ts" 1 1)]} "/w")))
    (is (= [] (oxlint/analysis-errors {"diagnostics" []} "/w")))))

(deftest recognizing-a-report
  (is (= {"diagnostics" []} (oxlint/report "{\"diagnostics\": []}")))
  (is (nil? (oxlint/report "{\"errors\": []}")))
  (is (nil? (oxlint/report "Failed to parse oxlint configuration file.")))
  (is (nil? (oxlint/report ""))))

(defn- validated [status text]
  (oxlint/validate-report status (oxlint/report text)))

(deftest reading-the-report
  (testing "the JSON oxlint prints on stdout"
    (is (= {"diagnostics" [] "number_of_rules" 1}
           (validated 0 (json/generate-string {"diagnostics" [] "number_of_rules" 1}))))
    (is (= [] (get (validated 1 "{\"diagnostics\": [], \"number_of_rules\": 1}") "diagnostics"))
        "an exit status other than 0 still gives the report"))
  (testing "no report"
    (is (= "oxlint exited 1 without a report; sf-js needs oxlint 1.62.0 or later"
           (message-of #(validated 1 "Failed to parse oxlint configuration file.\n"))))
    (is (= "oxlint exited 127 without a report; sf-js needs oxlint 1.62.0 or later"
           (message-of #(validated 127 ""))))
    (is (= "oxlint exited 0 without a report; sf-js needs oxlint 1.62.0 or later"
           (message-of #(validated 0 "[1, 2]")))))
  (testing "a report from an oxlint without the complexity rule"
    (is (= "oxlint ran no complexity rule; sf-js needs oxlint 1.62.0 or later"
           (message-of #(validated 0 "{\"diagnostics\": [], \"number_of_rules\": 0}"))))
    (is (= "oxlint ran no complexity rule; sf-js needs oxlint 1.62.0 or later"
           (message-of #(validated 0 "{\"diagnostics\": []}"))))))

(defn- source [& lines]
  (apply str (map #(str % "\n") lines)))

(deftest directives-that-can-hide-complexity
  (is (= [2 3 4 5 6 7 8]
         (oxlint/hiding-lines
          (source "export const a = 1;"
                  "// oxlint-disable-next-line complexity"
                  "// oxlint-disable-next-line eslint/complexity"
                  "/* oxlint-disable */"
                  "return 0; // oxlint-disable-line"
                  "/* oxlint-disable no-debugger, complexity */"
                  "// oxlint-disable-line no-debugger complexity -- legacy code"
                  "//oxlint-disable")))))

(deftest directives-that-cannot-hide-complexity
  (is (= []
         (oxlint/hiding-lines
          (source "// oxlint-disable-next-line no-debugger"
                  "// eslint-disable-next-line complexity"
                  "// oxlint-enable complexity"
                  "// oxlint-disable-next-line no-complexity-check"
                  "/* oxlint-disable no-debugger */ const x = 1; // complexity"
                  "// oxlint-disable-next-line no-debugger -- complexity is fine"
                  "const note = \"oxlint-disable\";")))))

(deftest oxlint-runs-only-complexity-with-its-own-config
  (let [config (json/parse-string (oxlint/config))]
    (is (= {"eslint/complexity" ["warn" 0]} (get config "rules")))
    (is (= {"correctness" "off"} (get config "categories")))
    (is (= {"respectEslintDisableDirectives" false} (get config "options")))))

(deftest oxlint-command-line
  (let [args (oxlint/args "tmp/oxlint/oxlintrc.json" ["src/a.ts" "src/b.js"])
        line (str/join " " args)]
    (is (= ["oxlint" "--config" "tmp/oxlint/oxlintrc.json" "--disable-nested-config"
            "--ignore-path" "tmp/oxlint/oxlintignore" "--format" "json"]
           (take 8 args)))
    (doseq [ignored ["**/node_modules/**" "**/dist/**" "**/tmp/**" "**/*.min.js" "**/*.test.*" "**/__tests__/**"]]
      (is (str/includes? line (str "--ignore-pattern " ignored)) ignored))
    (is (= ["src/a.ts" "src/b.js"] (take-last 2 args)))))
