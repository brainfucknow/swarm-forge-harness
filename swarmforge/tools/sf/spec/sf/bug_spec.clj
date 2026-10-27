(ns sf.bug-spec
  (:require [speclj.core :refer [describe it should= should-be-nil]]
            [sf.bug :as bug]))

(def shown "df7e1a7 [open] Title

  ddff77e #0 operator <o@x>

  The body

  dffb78e #1 operator <o@x>

  type: created
from: operator
branch: swarm/df7e1a7
commit: 7518db6
route: specifier,coder

  d6f677e #2 operator <o@x>

  type: review
from: specifier
commit: 7eba524
")

(describe "record-commit"
  (it "is the commit of the last record of the given types"
    (should= "7eba524" (bug/record-commit shown #{"created" "review" "handoff"})))

  (it "reads only the given types"
    (should= "7518db6" (bug/record-commit shown #{"created"})))

  (it "ignores a commit line before any record"
    (should-be-nil (bug/record-commit "commit: aaaaaaa\n\n  ddff77e #0 operator <o@x>\n\n  The body\n" #{"created"})))

  (it "is nil when there is no such record"
    (should-be-nil (bug/record-commit shown #{"handoff"}))))
