(ns sf.feature-block-spec
  (:require [speclj.core :refer [describe it should should= should-be-nil should-not]]
            [sf.feature-block :as block]))

(def manifest ["# acceptance-mutation-manifest-begin" "# {\"scenarios\":[]}" "# acceptance-mutation-manifest-end"])
(def stamped (into ["# mutation-stamp: sha256=ab"] manifest))

(defn- text [& lines] (apply str (map #(str % "\n") lines)))

(describe "split"
  (it "takes the manifest block and the blank lines after it off the top"
    (should= {:block (apply text manifest) :body (text "Feature: x" "" "  Scenario: y")}
             (block/split (apply text (concat manifest ["" "" "Feature: x" "" "  Scenario: y"])))))

  (it "takes the stamp line with the manifest"
    (should= {:block (apply text stamped) :body (text "Feature: x")}
             (block/split (apply text (concat stamped ["" "Feature: x"])))))

  (it "finds no block in a plain feature, below the top, without an end, or as a stamp alone"
    (should-be-nil (block/split (text "Feature: x")))
    (should-be-nil (block/split (apply text "Feature: x" manifest)))
    (should-be-nil (block/split (text "# acceptance-mutation-manifest-begin" "# {}" "Feature: x")))
    (should-be-nil (block/split (text "# mutation-stamp: sha256=ab" "Feature: x")))))

(describe "with-block"
  (it "puts the block lines and a blank line on top of the feature"
    (should= (apply text (concat manifest ["" "Feature: x"]))
             (block/with-block (apply text manifest) (text "Feature: x"))))

  (it "is undone by split"
    (should= {:block (apply text stamped) :body (text "Feature: x")}
             (block/split (block/with-block (apply text stamped) (text "Feature: x"))))))

(describe "block?"
  (it "is true for a block with nothing after it"
    (should (block/block? (apply text manifest)))
    (should (block/block? (apply text (concat stamped ["" ""])))))

  (it "is false for text that is not only a block"
    (should-not (block/block? (text "g1")))
    (should-not (block/block? ""))
    (should-not (block/block? (apply text (concat manifest ["" "Feature: x"]))))))
