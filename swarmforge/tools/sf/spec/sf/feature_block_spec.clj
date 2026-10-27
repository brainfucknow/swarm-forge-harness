(ns sf.feature-block-spec
  (:require [speclj.core :refer [describe it should= should-be-nil]]
            [sf.feature-block :as block]))

(def manifest-lines ["# acceptance-mutation-manifest-begin"
                     "# {\"scenarios\":[]}"
                     "# acceptance-mutation-manifest-end"])

(def stamped-lines (into ["# mutation-stamp: sha256=ab"] manifest-lines))

(defn- text [& lines] (apply str (map #(str % "\n") lines)))

(describe "block-lines"
  (it "reads a manifest block at the top of a feature"
    (should= manifest-lines (block/block-lines (apply text (concat manifest-lines ["" "Feature: x"])))))

  (it "includes the stamp line before the manifest"
    (should= stamped-lines (block/block-lines (apply text (concat stamped-lines ["" "Feature: x"])))))

  (it "finds no block in a plain feature"
    (should-be-nil (block/block-lines (text "Feature: x"))))

  (it "finds no block when the manifest is not at the top"
    (should-be-nil (block/block-lines (apply text "Feature: x" manifest-lines))))

  (it "finds no block without an end line"
    (should-be-nil (block/block-lines (text "# acceptance-mutation-manifest-begin" "# {}" "Feature: x"))))

  (it "does not take a stamp alone for a block"
    (should-be-nil (block/block-lines (text "# mutation-stamp: sha256=ab" "Feature: x")))))

(describe "without-block"
  (it "removes the block and the blank lines after it"
    (should= (text "Feature: x" "" "  Scenario: y")
             (block/without-block (apply text (concat stamped-lines ["" "" "Feature: x" "" "  Scenario: y"])))))

  (it "keeps a feature without a block as it is"
    (should= (text "" "Feature: x") (block/without-block (text "" "Feature: x")))))

(describe "with-block"
  (it "puts the block lines and a blank line before the feature"
    (should= (apply text (concat manifest-lines ["" "Feature: x"]))
             (block/with-block (apply text manifest-lines) (text "Feature: x"))))

  (it "is undone by without-block"
    (let [feature (text "Feature: x")]
      (should= feature (block/without-block (block/with-block (apply text stamped-lines) feature))))))

(describe "restored"
  (it "puts the saved block at the top of a feature without one"
    (should= (apply text (concat manifest-lines ["" "Feature: x"]))
             (block/restored (apply text manifest-lines) (text "Feature: x"))))

  (it "is nil when the feature has a block"
    (should-be-nil (block/restored "# saved\n" (apply text (concat manifest-lines ["" "Feature: x"]))))))

(describe "extracted"
  (it "splits a feature into its block text and the rest"
    (should= {:block (apply text stamped-lines) :body (text "Feature: x")}
             (block/extracted (apply text (concat stamped-lines ["" "Feature: x"])))))

  (it "is nil for a feature without a block"
    (should-be-nil (block/extracted (text "Feature: x")))))

(describe "manifest-json"
  (it "joins the manifest lines without their comment marks"
    (should= "{\"a\":1,\"b\":2}"
             (block/manifest-json ["# mutation-stamp: sha256=ab"
                                   "# acceptance-mutation-manifest-begin"
                                   "# {\"a\":1,"
                                   "#  \"b\":2}"
                                   "# acceptance-mutation-manifest-end"])))

  (it "is nil when the lines are not a block"
    (should-be-nil (block/manifest-json ["# {}"]))))
