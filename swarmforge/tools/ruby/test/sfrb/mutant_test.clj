(ns sfrb.mutant-test
  (:require [clojure.test :refer [deftest is]]
            [sfrb.mutant :as mutant]))

(def rspec ["--integration-argument" "--fail-fast" "--integration-argument" "--exclude-pattern"
            "--integration-argument" "**/*_property_spec.rb" "--integration-argument" "spec"])

(deftest mutant-runs-four-jobs-since-the-merge-base-without-property-specs
  (is (= (into ["exec" "mutant" "run" "--jobs" "4" "--since" "abc123"] rspec)
         (mutant/args "abc123" [])))
  (is (= (into ["exec" "mutant" "run" "--jobs" "4"] (conj rspec "Ledger*"))
         (mutant/args nil ["Ledger*"]))))

(deftest the-callers-jobs-are-overridden
  (doseq [extra [["--jobs" "9"] ["-j" "9"] ["--jobs=9"] ["-j9"]]]
    (is (= (into ["exec" "mutant" "run" "--jobs" "4"] (conj rspec "--fail-fast"))
           (mutant/args nil (conj extra "--fail-fast"))))))

(deftest the-usage-is-the-projects-choice
  (is (mutant/sets-usage? ["--usage" "opensource"]))
  (is (mutant/sets-usage? ["--usage=commercial"]))
  (is (not (mutant/sets-usage? ["--fail-fast"]))))

(deftest a-configuration-declares-a-usage-with-the-top-level-key
  (is (mutant/declares-usage? "usage: opensource\nintegration:\n  name: rspec\n"))
  (is (not (mutant/declares-usage? "integration:\n  name: rspec\n")))
  (is (not (mutant/declares-usage? "matcher:\n  usage: opensource\n")))
  (is (not (mutant/declares-usage? "")))
  (is (not (mutant/declares-usage? "- usage"))))
