(ns sf.feature-block
  "The block of comment lines that gherkin-mutator keeps at the top of a
  feature: an optional mutation stamp, then the scenario manifest."
  (:require [clojure.string :as str]))

(def ^:private stamp-prefix "# mutation-stamp:")
(def ^:private begin-line "# acceptance-mutation-manifest-begin")
(def ^:private end-line "# acceptance-mutation-manifest-end")

(defn- marker? [marker line]
  (= marker (str/trim line)))

(defn- block-end
  "The index of the end line of a block that starts at the top of lines."
  [lines]
  (let [begin (if (str/starts-with? (str/trim (first lines)) stamp-prefix) 1 0)]
    (when (marker? begin-line (get lines begin ""))
      (some #(when (marker? end-line (lines %)) %) (range begin (count lines))))))

(defn- lines-of [text]
  (str/split text #"\n" -1))

(defn block-lines
  "The lines of the block at the top of the feature text, or nil."
  [text]
  (let [lines (lines-of text)]
    (when-let [end (block-end lines)]
      (subvec lines 0 (inc end)))))

(defn without-block
  "The feature text without its block and the blank lines after it."
  [text]
  (let [lines (lines-of text)]
    (if-let [end (block-end lines)]
      (str/join "\n" (drop-while str/blank? (drop (inc end) lines)))
      text)))

(defn with-block
  "The feature text with the lines of block-text and a blank line before it."
  [block-text text]
  (str (str/join "\n" (str/split-lines block-text)) "\n\n" text))

(defn manifest-json
  "The manifest JSON of block lines, without the comment marks, or nil."
  [lines]
  (let [lines (vec lines)]
    (when-let [end (block-end lines)]
      (->> (subvec lines 0 end)
           (drop-while #(not (marker? begin-line %)))
           rest
           (map #(str/trim (str/replace-first (str/trim %) #"^#" "")))
           (str/join "")))))
