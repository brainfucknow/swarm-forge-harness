(ns sf.runtime-versions-spec
  (:require [speclj.core :refer [describe it should should-not should= should-be-nil]]
            [sf.runtime :as runtime]))

(describe "too-old?"
  (it "compares major, minor and patch numerically"
    (should (runtime/too-old? "1.12.199"))
    (should (runtime/too-old? "1.11.999"))
    (should (runtime/too-old? "0.99.999"))
    (should-not (runtime/too-old? "1.12.200"))
    (should-not (runtime/too-old? "1.12.201"))
    (should-not (runtime/too-old? "1.13.0"))
    (should-not (runtime/too-old? "2.0.0")))

  (it "reads a missing part as 0, and a longer version by its first three parts"
    (should (runtime/too-old? "1.12"))
    (should (runtime/too-old? "1"))
    (should-not (runtime/too-old? "1.12.200.9"))
    (should-not (runtime/too-old? "1.12.200-SNAPSHOT"))
    (should-not (runtime/too-old? "1.13"))))

(describe "refusal"
  (it "names the version and the minimum for an old Babashka"
    (should= (str "sf: Babashka 1.2.3 is too old; sf needs " runtime/minimum " or newer: https://babashka.org")
             (runtime/refusal "1.2.3")))

  (it "is nil for a Babashka sf runs on"
    (should-be-nil (runtime/refusal runtime/minimum))))
