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

(deftest nested-methods-own-their-lines
  (let [by-name (scored)]
    (is (= 0.0 (:coverage (by-name "outer"))))
    (is (= 0.0 (:coverage (by-name "inner"))))))

(deftest a-method-without-body-lines-is-covered-when-called
  (let [by-name (scored)]
    (is (= 1.0 (:coverage (by-name "empty?"))))
    (is (= 0.0 (:coverage (by-name "size"))))))

(deftest a-method-outside-the-coverage-report-is-uncovered
  (let [[method] (crap/score [{:file "lib/other.rb" :line 1 :last-line 5 :name "go" :complexity 3}]
                             (fixture "coverage.json") "/work")]
    (is (= 0.0 (:coverage method)))
    (is (= 12.0 (:crap method)))))

(deftest absolute-coverage-paths-match-relative-methods
  (let [report {"coverage" {"/work/lib/a.rb" {"lines" [nil 1 0] "methods" []}}}
        [method] (crap/score [{:file "lib/a.rb" :line 1 :last-line 3 :name "go" :complexity 1}] report "/work")]
    (is (= 0.5 (:coverage method)))))
