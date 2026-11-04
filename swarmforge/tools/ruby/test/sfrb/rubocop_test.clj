(ns sfrb.rubocop-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [sfrb.rubocop :as rubocop]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(defn- fixture [name]
  (json/parse-string (slurp (str (fs/path fixtures name)))))

(deftest the-config-enables-only-the-chosen-cop-at-zero
  (let [config (rubocop/config "Metrics/PerceivedComplexity")]
    (is (str/includes? config "DisabledByDefault: true"))
    (is (str/includes? config "Metrics/PerceivedComplexity:\n  Enabled: true\n  Max: 0\n"))
    (is (not (str/includes? config "Cyclomatic")))))

(deftest the-command-ignores-disable-comments-and-the-cache
  (is (= ["exec" "rubocop" "--config" "tmp/rubocop/cyclomatic.yml" "--ignore-disable-comments" "--cache" "false"
          "--format" "json" "--" "lib/a.rb"]
         (rubocop/args (rubocop/config-path "cyclomatic") ["lib/a.rb"]))))

(deftest every-method-with-its-span-and-complexity
  (let [methods (rubocop/parse-report (fixture "rubocop-cyclomatic.json") "Metrics/CyclomaticComplexity" "/work")]
    (is (= [["initialize" 2 4 1] ["balance" 6 16 5] ["label" 18 25 4] ["outer" 27 30 1]
            ["inner" 28 28 1] ["size" 32 32 1] ["empty?" 34 34 1]]
           (map (juxt :name :line :last-line :complexity) methods)))
    (is (= #{"lib/ledger.rb"} (set (map :file methods))))))

(deftest perceived-complexity-reads-its-own-cop
  (let [report (fixture "rubocop-perceived.json")]
    (is (= [6 3] (map :complexity (filter #(#{"balance" "label"} (:name %))
                                          (rubocop/parse-report report "Metrics/PerceivedComplexity" "/work")))))
    (is (= [] (rubocop/parse-report report "Metrics/CyclomaticComplexity" "/work")))))

(deftest syntax-errors-are-analysis-errors
  (let [report (fixture "rubocop-syntax-error.json")]
    (is (= ["rubocop could not parse lib/broken.rb:1: expected an `end` to close the `class` statement"
            "rubocop could not parse lib/broken.rb:3: unexpected 'end'; expected a `)` to close the parameters"]
           (take 2 (rubocop/analysis-errors report "/work"))))
    (is (= [] (rubocop/analysis-errors (fixture "rubocop-cyclomatic.json") "/work")))))

(deftest absolute-paths-become-repository-relative
  (let [report {"files" [{"path" "/work/lib/a.rb"
                          "offenses" [{"cop_name" "Metrics/CyclomaticComplexity"
                                       "message" "Cyclomatic complexity for `go` is too high. [3/0]"
                                       "location" {"start_line" 1 "last_line" 4}}]}]}]
    (is (= ["lib/a.rb"] (map :file (rubocop/parse-report report "Metrics/CyclomaticComplexity" "/work"))))))

(deftest a-fatal-offense-from-any-cop-is-an-analysis-error
  (let [offense (fn [severity cop] {"severity" severity "cop_name" cop "message" "bad"
                                    "location" {"start_line" 3 "last_line" 3}})
        report (fn [& offenses] {"files" [{"path" "/work/lib/a.rb" "offenses" (vec offenses)}]})]
    (is (= ["rubocop could not parse lib/a.rb:3: bad"]
           (rubocop/analysis-errors (report (offense "fatal" "Lint/Other")) "/work")))
    (is (= ["rubocop could not parse lib/a.rb:3: bad"]
           (rubocop/analysis-errors (report (offense "error" "Lint/Syntax")) "/work")))
    (is (= [] (rubocop/analysis-errors (report (offense "error" "Lint/Other")) "/work")))))
