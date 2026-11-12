(ns sfjava.reports-spec
  (:require [speclj.core :refer :all]
            [sfjava.reports :as reports]))

(describe "Java tool reports"
  (it "rejects missing and unknown CRAP coverage rather than declaring success"
    (should-throw Exception (reports/crap-status 0 "CRAP Report\nMethod Class CC Cov% CRAP\nf C 1 N/A N/A\n"))
    (should-throw Exception (reports/crap-status 0 "not a report")))
  (it "uses the pinned CRAP tool's ten-point gate and preserves operational failures"
    (should= 0 (reports/crap-status 0 "CRAP Report\nMethod Class CC Cov% CRAP\nf C 2 100.0% 10.0\n"))
    (should= 1 (reports/crap-status 2 "CRAP Report\nMethod Class CC Cov% CRAP\nf C 4 0.0% 10.1\n"))
    (should-throw Exception (reports/crap-status 1 "failure")))
  (it "selects duplicate declarations touching either changed side"
    (let [c {:score 0.9 :left {:file "src/main/java/A.java" :start-line 3 :end-line 6}
             :right {:file "src/main/java/B.java" :start-line 8 :end-line 12}}
          text (pr-str {:candidates [c]})]
      (should= [c] (reports/relevant-duplicates text {"src/main/java/A.java" #{4}} nil))
      (should= [c] (reports/relevant-duplicates text {"src/main/java/B.java" #{10}} nil))
      (should= [] (reports/relevant-duplicates text {"src/main/java/A.java" #{20}} nil))
      (should-throw Exception (reports/relevant-duplicates "{}" {} nil))))
  (it "rejects mutation process failures and requires a recognizable result"
    (should= 0 (reports/mutation-status 0 "Summary: 3 killed, 0 survived, 3 total."))
    (should= 1 (reports/mutation-status 3 "Summary: 2 killed, 1 survived, 3 total."))
    (should-throw Exception (reports/mutation-status 0 ""))
    (should-throw Exception (reports/mutation-status 2 "Baseline tests failed."))))
