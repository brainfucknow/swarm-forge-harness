(ns sf.tool-spec
  (:require [clojure.string :as str]
            [speclj.core :refer [describe it should should-contain should-not-contain should=]]
            [sf.tool :as tool]))

(def ^:private launch @#'tool/launch)

(describe "launch"
  (it "execs the command of a tool without a launch policy"
    (should= "exec tool --flag" (launch "-" "tool --flag"))
    (should= "exec tool --flag" (launch "mutate" "tool --flag")))

  (it "wraps the gherkin command with the manifest block commands of sf"
    (let [line (launch "gherkin" "tool --flag")
          lines (str/split-lines line)]
      (should-contain "tool --flag" lines)
      (should-not-contain "exec tool --flag" lines)
      (should (some #(re-find #"manifest block-in \"\$feature\"" %) lines))
      (should (some #(re-find #"manifest block-out \"\$feature\"" %) lines))
      (should (re-find #"swarmforge/bin/sf" line))
      (should-not-contain "{{sf}}" line)
      (should-not-contain "{{command}}" line))))
