(ns sf.manifest-property
  "Properties of the manifest policy and the gherkin-mutator block, over
  generated notes, files and feature texts."
  (:require [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.feature-block :as block]
            [sf.manifest :as manifest]))

(def ^:private trials 200)

(defn- holds? [property]
  (let [result (tc/quick-check trials property)]
    (when-not (:pass? result) (prn (:shrunk result)))
    (:pass? result)))

(def ^:private text (gen/fmap str/join (gen/vector (gen/elements ["a" "\"" "\\" "\n" " " "#" "é" "{" "}"]))))
(def ^:private files (gen/map gen/string-alphanumeric text {:max-elements 6}))
(def ^:private notes (gen/vector files 0 5))

(def ^:private feature-line
  (gen/elements ["Feature: f" "  Scenario: s" "    Given x" "# comment" "" "  " "Examples:"]))

(def ^:private block-text
  (gen/let [stamp? gen/boolean
            body (gen/vector (gen/elements ["# {\"scenarios\":[]}" "# {\"a\":" "#  1}"]) 1 3)]
    (str (str/join "\n" (concat (when stamp? ["# mutation-stamp: sha256=ab"])
                                ["# acceptance-mutation-manifest-begin"]
                                body
                                ["# acceptance-mutation-manifest-end"]))
         "\n")))

(def ^:private feature-text
  "Feature texts that start with neither a block nor a blank line."
  (gen/let [first-line (gen/elements ["Feature: f" "# language: en" "@tag"])
            more (gen/vector feature-line 0 8)
            newline? gen/boolean]
    (str (str/join "\n" (cons first-line more)) (when newline? "\n"))))

(describe "note encoding"
  (it "decodes what it encodes"
    (should (holds? (prop/for-all [f files]
                      (= f (manifest/decode-note (manifest/encode-note f)))))))

  (it "encodes equal file maps to the same text"
    (should (holds? (prop/for-all [f files]
                      (= (manifest/encode-note f) (manifest/encode-note (into {} (reverse (seq f))))))))))

(describe "copies"
  (it "take each path from the first note that holds it"
    (should (holds? (prop/for-all [ns notes]
                      (let [copies (manifest/newest-copies ns)]
                        (and (= (set (mapcat keys ns)) (set (keys copies)))
                             (every? (fn [[path content]]
                                       (= content (some #(get % path) ns)))
                                     copies)))))))

  (it "load only paths that the worktree does not have"
    (should (holds? (prop/for-all [copies files existing (gen/vector gen/string-alphanumeric)]
                      (let [loaded (manifest/to-load copies existing)]
                        (and (not-any? (set existing) (keys loaded))
                             (= loaded (select-keys copies (keys loaded)))))))))

  (it "save nothing once the files are the copies"
    (should (holds? (prop/for-all [f files copies files]
                      (and (empty? (manifest/to-save f f))
                           (empty? (manifest/to-save f (merge copies (manifest/to-save f copies))))))))))

(describe "gherkin-mutator block"
  (it "comes back out of a feature as it went in"
    (should (holds? (prop/for-all [b block-text f feature-text]
                      (= {:block b :body f} (block/extracted (block/restored b f)))))))

  (it "is not put into a feature that has one"
    (should (holds? (prop/for-all [b block-text other block-text f feature-text]
                      (nil? (block/restored other (block/restored b f)))))))

  (it "leaves a feature without one as it is"
    (should (holds? (prop/for-all [f feature-text]
                      (and (nil? (block/extracted f)) (= f (block/without-block f))))))))
