(ns sfjava.policy-spec
  (:require [speclj.core :refer :all]
            [sfjava.policy :as policy]))

(describe "Java wrapper policy"
  (it "accepts only documented command options"
    (should= {:command "crap" :base "topic"} (policy/options ["crap" "--base" "topic"]))
    (should-throw Exception (policy/options ["mutate" "--mutate-all"]))
    (should-throw Exception (policy/options ["test" "--base" "main"]))
    (should-throw Exception (policy/options ["crap" "--base"])))
  (it "requires a Java 17 runtime or later"
    (should (policy/supported-java? "openjdk version \"17.0.12\""))
    (should (policy/supported-java? "java version \"21.0.2\""))
    (should (policy/supported-java? "openjdk 17.0.12 2024-07-16"))
    (should (policy/supported-java? "java 21.0.2 2024-01-16 LTS"))
    (should-not (policy/supported-java? "openjdk 16.0.2"))
    (should-not (policy/supported-java? "java version \"1.8.0\""))
    (should-not (policy/supported-java? "unparseable")))
  (it "keeps ordinary and property test selection disjoint"
    (should= ["--exclude-tag" "property"] (policy/tag-args "test"))
    (should= ["--include-tag" "property"] (policy/tag-args "property"))
    (should= ["--exclude-tag" "property"] (policy/tag-args "coverage")))
  (it "selects only surviving production files"
    (should= ["src/main/java/A.java"]
             (policy/production-files ["src/main/java/A.java" "src/test/java/B.java" "README.md" "src/main/java/Gone.java"]
                                      #{"src/main/java/A.java" "src/test/java/B.java" "README.md"}))))
