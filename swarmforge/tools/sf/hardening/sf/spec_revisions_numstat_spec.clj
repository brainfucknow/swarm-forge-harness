(ns sf.spec-revisions-numstat-spec
  (:require [speclj.core :refer [describe it should=]]
            [sf.spec-revisions :as spec-revisions]))

(describe "read-log"
  (it "keeps only tab-separated numstat rows, not a stray line"
    (let [output (str "\u001eabc\u001f\u001fmsg\u001f\u001f\n\n3\t1\ta.clj\nstray\n\n")]
      (should= [["3" "1"]] (:numstat (first (spec-revisions/read-log output)))))))
