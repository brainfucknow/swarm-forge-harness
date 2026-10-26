(ns sfcore.core-property
  "Properties of the report readers shared by sf-cs and sf-js."
  (:require [clojure.string :as str]
            [property-support :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sfcore.changes :as changes]
            [sfcore.crap :as crap]
            [sfcore.jscpd :as jscpd]
            [sfcore.stryker :as stryker]))

(def trials 200)

(def segment (gen/fmap str/join (gen/vector gen/char-alphanumeric 1 6)))
(def relative (gen/fmap #(str/join "/" %) (gen/vector segment 1 3)))
(def absolute (gen/fmap #(str "/" (str/join "/" %)) (gen/vector segment 1 3)))

;; Unified diffs

(def diff-text
  "A diff line body, including ones that look like diff headers."
  (gen/one-of [gen/string-ascii
               (gen/fmap #(str "++ " %) gen/string-ascii)
               (gen/fmap #(str "-- " %) gen/string-ascii)
               (gen/return "@@ -1 +1 @@")]))

(def hunk
  (gen/such-that (fn [{:keys [removed added]}] (pos? (+ removed added)))
                 (gen/hash-map :gap (gen/choose 1 20) :removed (gen/choose 0 3) :added (gen/choose 0 4)
                               :short? gen/boolean :texts (gen/vector diff-text 7))))

(defn- header-count [n short?]
  (if (and short? (= 1 n)) "" (str "," n)))

(defn- render-file
  "A file's diff section, and the new-side lines it adds."
  [file hunks]
  (loop [[{:keys [gap removed added short? texts]} & more :as todo] hunks
         cursor 0
         lines [(str "diff --git a/" file " b/" file) (str "--- a/" file) (str "+++ b/" file)]
         added-lines (sorted-set)]
    (if (empty? todo)
      [(str/join "\n" lines) added-lines]
      (let [start (+ cursor gap)
            shown-start (if (zero? added) (dec start) start)
            body (concat [(str "@@ -" start (header-count removed short?) " +" shown-start (header-count added short?) " @@")]
                         (map #(str "-" %) (take removed texts))
                         (map #(str "+" %) (take added (drop removed texts))))]
        (recur more (+ start added) (into lines body) (into added-lines (range start (+ start added))))))))

(defspec diffs-read-back-every-added-line trials
  (prop/for-all [files (gen/map relative (gen/vector hunk 0 4) {:max-elements 4})]
    (let [rendered (into (sorted-map) (for [[file hunks] files] [file (render-file file hunks)]))
          text (str/join "\n" (map first (vals rendered)))]
      (= (into {} (for [[file [_ lines]] rendered] [file lines]))
         (changes/changed-lines-from-diff text)))))

;; Paths reported by tools

(defspec paths-under-the-root-become-relative trials
  (prop/for-all [root absolute rel relative]
    (let [windows #(str/replace % "/" "\\")]
      (= rel
         (changes/relative-path (str root "/" rel) root)
         (changes/relative-path (str root "/" rel) (str root "/"))
         (changes/relative-path (windows (str root "/" rel)) (windows root))
         (changes/relative-path rel root)
         (changes/relative-path (str "./" rel) root)))))

(defspec changed-paths-match-from-anywhere trials
  (prop/for-all [root absolute rel relative elsewhere absolute]
    (let [changed {rel #{1}}]
      (= rel
         (changes/to-changed-path rel changed root)
         (changes/to-changed-path (str root "/" rel) changed root)
         (changes/to-changed-path (str "/zz-other" elsewhere "/" rel) changed root)))))

(defspec overlap-means-a-shared-line trials
  (prop/for-all [lines (gen/set (gen/choose 1 60)) start (gen/choose 1 60) length (gen/choose 0 10)]
    (let [end (+ start length)]
      (and (= (boolean (some lines (range start (inc end))))
              (changes/overlaps? "f.ts" start end {"f.ts" lines}))
           (false? (changes/overlaps? "g.ts" start end {"f.ts" lines}))))))

;; CRAP

(def coverage (gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(defspec crap-falls-from-the-uncovered-score-to-the-complexity trials
  (prop/for-all [complexity (gen/choose 1 60) a coverage b coverage]
    (let [[low high] (sort [a b])
          score (partial crap/crap-score complexity)]
      (and (== complexity (score 1.0))
           (== (+ (* complexity complexity) complexity) (score 0.0))
           (>= (score low) (score high) complexity)))))

(def unit
  (gen/fmap (fn [[complexity coverage line]]
              {:complexity complexity :coverage coverage :crap (crap/crap-score complexity coverage)
               :file "src/a.ts" :line line :name "f"})
            (gen/tuple (gen/choose 1 30) coverage (gen/choose 1 500))))

(defspec crap-review-flags-exactly-the-units-over-the-limit trials
  (prop/for-all [units (gen/vector unit 0 8)]
    (let [{:keys [lines status]} (crap/review units crap/format-row "function" nil)
          over (filter crap/over? units)]
      (and (= (inc (count units)) (count lines))
           (= (count over) (count (filter #(str/starts-with? % "OVER") lines)))
           (= (if (seq over) 1 0) status)
           (= (map :crap (crap/sort-worst-first units)) (sort > (map :crap units)))))))

;; Stryker

(def statuses ["Killed" "Survived" "NoCoverage" "Timeout" "Ignored" "CompileError" "RuntimeError"])

(def mutant
  (gen/hash-map :file (gen/elements ["src/a.ts" "src/b.ts" "lib/c.js"]) :line (gen/choose 1 99)
                :status (gen/elements statuses) :mutator (gen/elements ["EqualityOperator" "BooleanLiteral"])))

(defn- report-of [mutants]
  {"files" (into {} (for [[file group] (group-by :file mutants)]
                      [file {"mutants" (for [{:keys [line status mutator]} group]
                                         {"location" {"start" {"line" line}} "status" status "mutatorName" mutator})}]))})

(defspec stryker-reports-read-back-every-mutant trials
  (prop/for-all [mutants (gen/vector mutant 0 12)]
    (let [parsed (stryker/parse-mutants (report-of mutants))
          key (juxt :file :line :status :mutator)]
      (and (= (frequencies (map key mutants)) (frequencies (map key parsed)))
           (= parsed (sort-by (juxt :file :line) parsed))))))

(defspec stryker-scores-stay-in-range-and-count-every-mutant trials
  (prop/for-all [mutants (gen/vector mutant 0 12)]
    (let [{:keys [counts score]} (stryker/summarize mutants)
          alive (stryker/survivors mutants)]
      (and (<= 0.0 score 100.0)
           (= (count mutants) (reduce + 0 (vals counts)))
           (every? #{"Survived" "NoCoverage"} (map :status alive))
           (= (if (seq alive) 1 0) (:status (stryker/review mutants)))
           (= (inc (count alive)) (count (:lines (stryker/review mutants))))))))

;; jscpd

(def side
  (gen/fmap (fn [[file start length]] {"name" file "start" start "end" (+ start length)})
            (gen/tuple (gen/elements ["a.ts" "b.ts" "c.ts"]) (gen/choose 1 50) (gen/choose 0 20))))

(def duplicate
  (gen/fmap (fn [[tokens a b]] {"tokens" tokens "lines" 1 "firstFile" a "secondFile" b})
            (gen/tuple (gen/choose 50 200) side side)))

(def changed-lines
  (gen/map (gen/elements ["a.ts" "b.ts" "c.ts"]) (gen/set (gen/choose 1 80)) {:max-elements 3}))

(defspec clones-shown-are-exactly-those-touching-changes trials
  (prop/for-all [duplicates (gen/vector duplicate 0 6) changed changed-lines]
    (let [clones (jscpd/parse-clones {"duplicates" duplicates})
          touches? (fn [{:keys [file start end]}] (changes/overlaps? file start end changed))
          shown (jscpd/clones-touching-changes clones changed "/repo")]
      (and (= (count clones) (count duplicates))
           (= (filter #(some touches? (:sides %)) clones)
              (map (fn [clone] (update clone :sides (partial mapv #(dissoc % :changed)))) shown))
           (every? (fn [clone] (= (map touches? (:sides clone)) (map :changed (:sides clone)))) shown)
           (= (if (seq shown) 1 0) (:status (jscpd/review clones changed "/repo")))
           (= (count clones) (dec (count (:lines (jscpd/review clones nil nil)))))))))
