(ns sf.lore-comment-spec
  (:require [speclj.core :refer [describe it should=]]
            [sf.lore :as lore]))

(describe "sign-commit"
  (it "returns a message made only of comment lines unchanged, so git can abort the empty commit"
    (let [message "# first\n# second\n"]
      (should= message (lore/sign-commit message "#" "qa" ["Tested"] (constantly "0badc0de"))))))
