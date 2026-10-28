(ns sf.feature-block
  "The block of comment lines that gherkin-mutator keeps at the top of a
  feature: an optional mutation stamp, then the scenario manifest."
  (:require [clojure.string :as str]))

(defn- line-is? [text line]
  (= text (str/trim line)))

(defn- block-end
  "The index of the last line of the block on top of lines, or nil."
  [lines]
  (let [begin (if (str/starts-with? (str/trim (first lines)) "# mutation-stamp:") 1 0)]
    (when (line-is? "# acceptance-mutation-manifest-begin" (get lines begin ""))
      (some #(when (line-is? "# acceptance-mutation-manifest-end" (lines %)) %)
            (range begin (count lines))))))

(defn split
  "The block on top of the feature text, as lines of text, and the feature text
  without it and the blank lines after it; nil when there is no block."
  [text]
  (let [lines (str/split text #"\n" -1)]
    (when-let [end (block-end lines)]
      {:block (str (str/join "\n" (subvec lines 0 (inc end))) "\n")
       :body (str/join "\n" (drop-while str/blank? (subvec lines (inc end))))})))

(defn block?
  "Whether text is a block and nothing else."
  [text]
  (str/blank? (:body (split text) "x")))

(defn with-block
  "The feature text with the lines of block and a blank line on top."
  [block text]
  (str (str/trim-newline block) "\n\n" text))
