(ns sf.runtime-spec
  (:require [speclj.core :refer [describe it should should-not should= should-be-nil]]
            [sf.runtime :as runtime]))

(describe "too-old?"
  (it "refuses a Babashka without java.text.Normalizer"
    (should (runtime/too-old? "1.12.204"))
    (should (runtime/too-old? "1.12.200"))
    (should (runtime/too-old? "1.3.191")))

  (it "accepts the minimum and every later version"
    (should-not (runtime/too-old? "1.12.205"))
    (should-not (runtime/too-old? "1.12.206-SNAPSHOT"))
    (should-not (runtime/too-old? "1.13.229"))
    (should-not (runtime/too-old? "2.0.0")))

  (it "compares the numbers, not the text"
    (should-not (runtime/too-old? "1.100.0"))
    (should (runtime/too-old? "0.999.999")))

  (it "reads a missing part as 0"
    (should (runtime/too-old? "1.12"))
    (should-not (runtime/too-old? "1.13"))))

(describe "refusal"
  (it "names the version and the minimum"
    (should= "sf: Babashka 1.12.200 is too old; sf needs 1.12.205 or newer: https://babashka.org"
             (runtime/refusal "1.12.200")))

  (it "is nil for a version sf runs on"
    (should-be-nil (runtime/refusal runtime/minimum))))
