(ns sfrb.crap-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [sfrb.crap :as crap]
            [sfrb.rubocop :as rubocop]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(defn- fixture [name]
  (json/parse-string (slurp (str (fs/path fixtures name)))))

(defn- scored []
  (let [methods (rubocop/parse-report (fixture "rubocop-cyclomatic.json") "Metrics/CyclomaticComplexity" "/work")]
    (into {} (map (juxt :name identity) (crap/score methods (fixture "coverage.json") "/work")))))

(deftest coverage-counts-the-relevant-lines-after-the-definition
  (let [by-name (scored)]
    (testing "five of balance's seven relevant lines ran"
      (is (= (/ 5.0 7) (:coverage (by-name "balance")))))
    (testing "two of label's five; CRAP follows the formula"
      (is (= 0.4 (:coverage (by-name "label"))))
      (is (< (Math/abs (- 7.456 (:crap (by-name "label")))) 1e-9)))))

(defn- coverages [& names]
  (update-vals (select-keys (scored) names) :coverage))

(deftest nested-methods-own-their-lines
  (is (= {"outer" 0.0 "inner" 0.0} (coverages "outer" "inner"))))

(deftest a-method-without-body-lines-is-covered-when-called
  (is (= {"empty?" 1.0 "size" 0.0} (coverages "empty?" "size"))))

(deftest a-method-outside-the-coverage-report-is-uncovered
  (let [[method] (crap/score [{:file "lib/other.rb" :line 1 :last-line 5 :name "go" :complexity 3}]
                             (fixture "coverage.json") "/work")]
    (is (= 0.0 (:coverage method)))
    (is (= 12.0 (:crap method)))))

(deftest absolute-coverage-paths-match-relative-methods
  (let [report {"coverage" {"/work/lib/a.rb" {"lines" [nil 1 0] "methods" []}}}
        [method] (crap/score [{:file "lib/a.rb" :line 1 :last-line 3 :name "go" :complexity 1}] report "/work")]
    (is (= 0.5 (:coverage method)))))

(defn- coverage-of [methods lines & [simplecov-methods]]
  (let [report {"coverage" {"/work/lib/a.rb" {"lines" lines "methods" (or simplecov-methods [])}}}]
    (into {} (map (juxt :name :coverage)
                  (crap/score (map #(merge {:file "lib/a.rb" :complexity 1} %) methods) report "/work")))))

(deftest a-method-sharing-the-last-line-of-its-outer-method-is-nested
  (is (= {"outer" 1.0 "inner" 0.0}
         (coverage-of [{:name "outer" :line 1 :last-line 5} {:name "inner" :line 3 :last-line 5}]
                      [nil 1 0 0 0]))))

(deftest a-method-sharing-the-first-line-of-its-outer-method-is-nested
  (is (= {"outer" 0.0 "inner" 1.0}
         (coverage-of [{:name "outer" :line 1 :last-line 5} {:name "inner" :line 1 :last-line 3}]
                      [nil 1 1 0 0]))))

(deftest a-nested-one-line-method-takes-its-line-from-its-outer-method
  (is (= {"outer" 0.0 "inner" 0.0}
         (coverage-of [{:name "outer" :line 1 :last-line 4} {:name "inner" :line 3 :last-line 3}]
                      [nil 0 1 0]))))

(deftest a-method-count-without-coverage-is-not-called
  (is (= {"go" 0.0}
         (coverage-of [{:name "go" :line 1 :last-line 1}] [nil] [{"start_line" 1}]))))
