(ns sf.options-spec
  (:require [speclj.core :refer [describe it should=]]
            [sf.options :as options]))

(defn- refusal [args]
  (try (options/rejections args) nil (catch clojure.lang.ExceptionInfo e (ex-message e))))

(describe "rejections"
  (it "are none without arguments"
    (should= [] (options/rejections [])))

  (it "are the trimmed values of each --rejected, in order"
    (should= ["A | b" "C | x | d"] (options/rejections ["--rejected" "  A | b \n" "--rejected" "C | x | d"])))

  (it "checks the trimmed value"
    (should= "--rejected needs '<finding> | <reason>', got 'A |'" (refusal ["--rejected" "A | "])))

  (it "refuses a --rejected without a value"
    (should= "--rejected needs a value" (refusal ["--rejected" "A | b" "--rejected"])))

  (it "refuses any other argument, a mistyped option included"
    (should= "unknown approve argument '--rejectd'" (refusal ["--rejectd" "A | b"]))
    (should= "unknown approve argument 'extra'" (refusal ["--rejected" "A | b" "extra"])))

  (it "refuses a value that record/rejected-refusal refuses"
    (should= "--rejected takes one line" (refusal ["--rejected" "A | b\nc"]))
    (should= "--rejected needs '<finding> | <reason>', got ''" (refusal ["--rejected" "  "]))))

(defn- parse-refusal [args]
  (try (options/parse "new" args {"--fixes" :fixes "--route" :route} {} #{"--fixes"}) nil
       (catch clojure.lang.ExceptionInfo e (ex-message e))))

(describe "parse"
  (it "takes the word after an option as its value, also one that starts with --"
    (should= {:fixes "--route" :route "x"}
             (options/parse "new" ["--fixes" "--route" "--route" "x"] {"--fixes" :fixes "--route" :route} {} #{"--fixes"})))

  (it "keeps the last value of an option that may repeat"
    (should= {:route "b"} (options/parse "new" ["--route" "a" "--route" "b"] {"--route" :route} {})))

  (it "refuses an option that may not repeat when it is given twice"
    (should= "--fixes given twice" (parse-refusal ["--fixes" "#7" "--fixes" "#8"])))

  (it "refuses a missing value before a repeat"
    (should= "--fixes needs a value" (parse-refusal ["--fixes" "#7" "--fixes"]))))
