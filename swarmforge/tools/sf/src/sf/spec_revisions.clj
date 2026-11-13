(ns sf.spec-revisions
  "The spec revisions of a specification gate: sizes, growth, limits on
  automatic send-backs, and the lines sf spec-revisions prints."
  (:require [clojure.string :as str]
            [sf.lore :as lore]))

(def spec-verdicts
  "The spec verdicts sf spec-verdict records."
  ["converged" "continue" "needs-operator" "stuck"])

(def cap
  "The automatic send-backs a task may have."
  2)

(defn automatic?
  "True for a send-back that sf spec-verdict continue wrote."
  [{:keys [record spec-verdict]}]
  (and (= "changes-requested" record) (= "continue" spec-verdict)))

(defn automatic-count [records]
  (count (filter automatic? records)))

(defn- revision-start? [{:keys [record stage]}]
  (and (= "review" record) (= "specifier" stage)))

(defn- operator-send-back? [{:keys [record from spec-verdict]}]
  (and (= "changes-requested" record) (= "operator" from) (nil? spec-verdict)))

(defn- revision-record? [{:keys [record]}]
  (contains? #{"spec-verdict" "changes-requested"} record))

(defn- open-revision
  "Starts spec revision n at review. Its baseline is n after an operator
  send-back or in spec revision 1, else the previous baseline."
  [{:keys [revisions reset?]} review]
  (let [n (inc (count revisions))
        baseline (if (or reset? (= 1 n)) n (:baseline (peek revisions)))]
    {:revisions (conj revisions {:n n :reviewed (or (:reviewed review) (:commit review))
                                 :baseline baseline :records []})
     :reset? false}))

(defn- add-record [{:keys [revisions] :as acc} rec]
  (cond-> acc
    (and (seq revisions) (revision-record? rec)) (update-in [:revisions (dec (count revisions)) :records] conj rec)
    (operator-send-back? rec) (assoc :reset? true)))

(defn spec-revisions
  "The spec revisions of records, oldest first, each with :n, :reviewed,
  :baseline and the spec-verdict and changes-requested :records after its review."
  [records]
  (:revisions (reduce (fn [acc rec] (if (revision-start? rec) (open-revision acc rec) (add-record acc rec)))
                      {:revisions [] :reset? false}
                      records)))

(defn spec-verdict-of
  "The spec verdict of the spec revision, or nil."
  [revision]
  (some :spec-verdict (:records revision)))

;; size

(defn- record-commit? [{:keys [trailers]}]
  (boolean (some #(= "SwarmForge-Record" (lore/trailer-key %)) trailers)))

(defn- counted
  "The commits that count toward size: no record commit, no merge."
  [commits]
  (remove #(or (record-commit? %) (< 1 (count (:parents %)))) commits))

(defn- without-last-paragraph [lines]
  (let [gap (last (keep-indexed #(when (str/blank? %2) %1) lines))]
    (if gap (subvec lines 0 gap) lines)))

(defn- message-lines
  "The nonblank lines of the message, without the trailer paragraph."
  [{:keys [message trailers]}]
  (let [lines (vec (str/split-lines (str/replace (or message "") #"\s+\z" "")))]
    (count (remove str/blank? (if (seq trailers) (without-last-paragraph lines) lines)))))

(defn- numstat-count [value]
  (or (parse-long value) 0))

(defn- tracked-lines [commits]
  (max 0 (reduce + (for [{:keys [numstat]} commits
                         [added removed] numstat]
                     (- (numstat-count added) (numstat-count removed))))))

(defn size
  "The size of a spec revision: the message lines of the current counted commits
  (lore/current), plus the lines added minus removed over all of them, at least 0."
  [commits]
  (let [commits (counted commits)]
    (+ (reduce + (map message-lines (lore/current commits))) (tracked-lines commits))))

(def log-format
  "The git log --numstat --format that read-log reads."
  "%x1e%h%x1f%p%x1f%B%x1f%(trailers:only,unfold)%x1f")

(defn- numstat-rows [text]
  (vec (for [line (str/split-lines (or text ""))
             :let [[added removed] (str/split line #"\t")]
             :when (and removed (not (str/blank? added)))]
         [added removed])))

(defn read-log
  "The commits in git log --numstat output made with log-format."
  [output]
  (vec (for [entry (str/split output #"\x1e")
             :let [[sha parents message trailers numstat] (str/split entry #"\x1f" -1)]
             :when (not (str/blank? sha))]
         {:sha sha :parents (vec (remove str/blank? (str/split (or parents "") #" ")))
          :message message :trailers (vec (remove str/blank? (str/split-lines (or trailers ""))))
          :numstat (numstat-rows numstat)})))

;; growth and limits

(defn growth
  "The growth of size over base: a percentage rounded down, or - when base is 0."
  [size base]
  (if (zero? base) "-" (str (Math/floorDiv (* 100 (- size base)) base) "%")))

(defn over-limit?
  "True when size is more than half over a base that is not 0."
  [size base]
  (and (pos? base) (> (* 100 (- size base)) (* 50 base))))

(defn limits
  "The LIMIT lines that hold for the newest spec revision, cap first."
  [automatic size base]
  (cond-> []
    (>= automatic cap) (conj (str "LIMIT: cap " cap " of " cap " automatic send-backs used"))
    (over-limit? size base) (conj (str "LIMIT: growth size " size " is more than half over " base))))

;; total growth

(defn notes
  "The NOTE line on the newest spec revision's total growth over spec
  revision 1: printed when it is more than half over a spec revision 1 that
  is not its baseline, where the growth limit does not measure it. Not a limit."
  [sized-revisions]
  (let [{s1 :size} (first sized-revisions)
        {:keys [size baseline]} (peek (vec sized-revisions))]
    (if (and size (not= 1 baseline) (over-limit? size s1))
      [(str "NOTE: total growth " (growth size s1) " over spec revision 1 (" s1 "); not a limit")]
      [])))

;; output

(defn- text-lines
  "A record's text: the first line after `  <from>: `, the rest indented four spaces."
  [{:keys [from text]}]
  (let [[first-line & more] (str/split-lines (or text ""))]
    (cons (str "  " from ": " first-line)
          (for [line more] (if (str/blank? line) "" (str "    " line))))))

(defn report
  "The lines of sf spec-revisions: NO_SPEC_REVISIONS, or each spec revision's
  line, with its records' texts under it when full, then tail-lines."
  [revisions tail-lines full]
  (if (empty? revisions)
    ["NO_SPEC_REVISIONS"]
    (vec (concat (for [{:keys [n short size growth total records] :as revision} revisions
                       line (cons (str "SPEC-REVISION " n " " short " size:" size " growth:" growth
                                       " total:" total " spec-verdict:" (or (spec-verdict-of revision) "-"))
                                  (when full (mapcat text-lines records)))]
                   line)
                 tail-lines))))

;; sf spec-verdict

(defn spec-verdict-refusal
  "Why sf spec-verdict cannot record spec-verdict, or nil. Does not check the
  limits on continue."
  [{:keys [state revisions spec-verdict text spec-judge]}]
  (let [current (peek (vec revisions))]
    (cond
      (not (some #{spec-verdict} spec-verdicts))
      (str "spec verdict must be converged, continue, needs-operator or stuck, not '" spec-verdict "'")
      (str/blank? text) "spec verdict needs a reason"
      (nil? spec-judge) "roles.conf has no spec-judge; add `<role> <tier> subagent spec-judge` to record spec verdicts"
      (not= "specifier" (:stage state))
      (str "task is at stage:" (:stage state) "; spec verdicts are recorded at stage:specifier")
      (not= "approval" (:needs state)) "task is not waiting for approval"
      (spec-verdict-of current)
      (str "spec revision " (:n current) " already has the spec verdict " (spec-verdict-of current)))))
