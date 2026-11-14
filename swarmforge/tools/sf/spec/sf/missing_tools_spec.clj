(ns sf.missing-tools-spec
  (:require [speclj.core :refer [describe it should=]]
            [sf.missing-tools :as missing-tools]))

(def names ["crap4clj" "dry4clj" "speclj" "speclj-structure-check" "clj-mutate"])

(describe "named"
  (it "gives the names in the text in the order they appear, each once"
    (should= ["dry4clj" "crap4clj"] (missing-tools/named names "dry4clj, crap4clj and dry4clj")))

  (it "ignores case"
    (should= ["dry4clj"] (missing-tools/named names "Dry4CLJ is not installed")))

  (it "needs a boundary other than a letter, a digit, - or _ on each side"
    (should= [] (missing-tools/named names "mydry4clj dry4clj_old dry4clj-next dry4clj2"))
    (should= ["speclj-structure-check"] (missing-tools/named names "speclj-structure-check failed"))
    (should= ["clj-mutate" "dry4clj"] (missing-tools/named names "(clj-mutate) dry4clj."))))

(describe "named-in-commits"
  (it "reads the Not-tested trailers of each commit, key in any case, oldest commit first"
    (should= ["dry4clj" "crap4clj" "speclj"]
             (missing-tools/named-in-commits
              names
              [["Not-tested: DRY: dry4clj" "Tested: speclj src -> ok"]
               ["Not-Tested: again dry4clj" "not-tested: CRAP: crap4clj" "Constraint: clj-mutate later"]
               ["Not-tested: resize: speclj cannot fake SIGWINCH"]]))))

(defn- question [text] {:record "question" :from "coder" :text text})

(def answer {:record "answer" :from "operator" :text "ok"})

(describe "accepted"
  (it "accepts a tool a question after the newest milestone names when an answer follows it"
    (should= #{"dry4clj"}
             (missing-tools/accepted ["dry4clj" "crap4clj"]
                                     [{:record "created"} (question "dry4clj?") answer])))

  (it "does not accept a question with no answer after it"
    (should= #{} (missing-tools/accepted ["dry4clj"] [{:record "created"} answer (question "dry4clj?")])))

  (it "does not count questions before the newest created, review or handoff record"
    (should= #{} (missing-tools/accepted ["dry4clj"]
                                         [{:record "created"} (question "dry4clj?") answer {:record "handoff"}]))))

(describe "refusal"
  (it "lists each missing tool with its install command, then the closing line"
    (should= ["MISSING_TOOL: T dry4clj is named in Not-tested but is not installed; nothing was recorded"
              "Run: sf tool ensure dry4clj"
              "Then run the check, or record Not-tested with the install or run error. If the install fails, run sf ask; after the operator answers, pass --allow-missing-tool."]
             (missing-tools/refusal "T" ["dry4clj"] [])))

  (it "puts an ALLOW_REFUSED line first for each tool not accepted"
    (should= ["ALLOW_REFUSED: T crap4clj has no answered question since the last record"
              "MISSING_TOOL: T dry4clj is named in Not-tested but is not installed; nothing was recorded"
              "Run: sf tool ensure dry4clj"
              "MISSING_TOOL: T crap4clj is named in Not-tested but is not installed; nothing was recorded"
              "Run: sf tool ensure crap4clj"]
             (butlast (missing-tools/refusal "T" ["dry4clj" "crap4clj"] ["crap4clj"])))))
