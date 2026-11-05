(ns sfrb.flog-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is]]
            [sfrb.flog :as flog]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(deftest scores-every-method-with-its-span
  (is (= [["Ledger#balance" 6 16 15.3] ["Ledger::define_method#empty?" 34 34 6.0] ["Ledger#label" 18 25 4.3]
          ["Ledger#outer" 27 30 2.5] ["Ledger#initialize" 2 4 1.0] ["Ledger#size" 32 32 1.0]
          ["Ledger#inner" 28 28 0.2]]
         (map (juxt :name :line :last-line :complexity)
              (flog/parse-report (slurp (str (fs/path fixtures "flog.txt"))) "/work")))))

(deftest totals-and-code-outside-methods-are-not-scored
  (is (= [] (flog/parse-report "    7.2: flog total\n     3.6: flog/method average\n\n     4.4: Q#none\n" "/work")))
  (is (= [] (flog/parse-report "     4.4: Q#none                lib/q.rb:1-9\n" "/work"))))

(deftest paths-may-hold-spaces-and-be-absolute
  (is (= [["my lib/a.rb" 3 9]]
         (map (juxt :file :line :last-line) (flog/parse-report "     2.0: A#go   /work/my lib/a.rb:3-9\n" "/work")))))

(deftest the-command-scores-every-method
  (is (= ["exec" "flog" "-a" "--" "lib/a.rb"] (flog/args ["lib/a.rb"]))))
