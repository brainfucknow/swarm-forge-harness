(ns sfjava.reports-hardening-spec
  (:require [speclj.core :refer :all]
            [sfjava.reports :as reports]))

(describe "Java report validation hardening"
  (it "accepts single-line declarations and validates every duplicate field"
    (let [location {:file "src/main/java/A.java" :start-line 3 :end-line 3}
          valid {:score 0.9 :left location :right location}
          render #(pr-str {:candidates %})]
      (should= [valid] (reports/relevant-duplicates (render [valid]) {"src/main/java/A.java" #{3}} nil))
      (doseq [invalid [(assoc valid :score "unknown")
                       (assoc-in valid [:left :file] nil)
                       (assoc-in valid [:left :start-line] 0)
                       (assoc-in valid [:right :end-line] 0)
                       (assoc-in valid [:left :start-line] 4)]]
        (should-throw Exception "analysis failure: malformed dry4java report"
                      (reports/relevant-duplicates (render [valid invalid]) {} nil)))))
)
