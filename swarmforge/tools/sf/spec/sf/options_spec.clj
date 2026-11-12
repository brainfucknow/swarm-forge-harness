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
