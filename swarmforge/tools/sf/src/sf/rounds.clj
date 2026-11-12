(ns sf.rounds
  "The rounds of a specification gate, as plain values: each specifier review
  with the verdicts and change requests after it, the size of the
  specification it reviewed, its growth over its baseline, the limits on an
  automatic send-back, and the lines sf rounds prints."
  (:require [clojure.string :as str]
            [sf.lore :as lore]))

(def verdicts
  "The verdicts sf verdict records, in the order the judge's rules name them."
  ["converged" "continue" "needs-operator" "stuck"])

(def cap
  "The automatic send-backs a task may have."
  2)

(defn automatic?
  "True for a send-back that sf verdict continue wrote."
  [{:keys [record verdict]}]
  (and (= "changes-requested" record) (= "continue" verdict)))

(defn automatic-count [records]
  (count (filter automatic? records)))

(defn- round-start? [{:keys [record stage]}]
  (and (= "review" record) (= "specifier" stage)))

(defn- operator-send-back? [{:keys [record from verdict]}]
  (and (= "changes-requested" record) (= "operator" from) (nil? verdict)))

(defn- round-record? [{:keys [record]}]
  (contains? #{"verdict" "changes-requested"} record))

(defn- open-round
  "Starts round n at review; its baseline is n after an operator send-back or
  for round 1, else the baseline before it."
  [{:keys [rounds reset?]} review]
  (let [n (inc (count rounds))
        baseline (if (or reset? (= 1 n)) n (:baseline (peek rounds)))]
    {:rounds (conj rounds {:n n :reviewed (or (:reviewed review) (:commit review))
                           :baseline baseline :records []})
     :reset? false}))

(defn- add-record [{:keys [rounds] :as acc} rec]
  (cond-> acc
    (and (seq rounds) (round-record? rec)) (update-in [:rounds (dec (count rounds)) :records] conj rec)
    (operator-send-back? rec) (assoc :reset? true)))

(defn rounds
  "The rounds of records, oldest first: :n from 1, the :reviewed commit, the
  :baseline round and the verdict and changes-requested :records after the
  review and before the next."
  [records]
  (:rounds (reduce (fn [acc rec] (if (round-start? rec) (open-round acc rec) (add-record acc rec)))
                   {:rounds [] :reset? false}
                   records)))

(defn verdict-of
  "The verdict of the round, or nil."
  [round]
  (some :verdict (:records round)))

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
  "The nonblank lines of the message, without its last paragraph when git
  reads a trailer from it."
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
  "The size of the specification at the commits of a round: the message lines
  of the counted commits current among them (lore/current), plus the lines
  added minus the lines removed over every counted commit, at least 0."
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
  "The growth of size over base for display: the percentage rounded down, with
  %, or - when base is 0."
  [size base]
  (if (zero? base) "-" (str (Math/floorDiv (* 100 (- size base)) base) "%")))

(defn over-limit?
  "True when size is more than half over a base that is not 0."
  [size base]
  (and (pos? base) (> (* 100 (- size base)) (* 50 base))))

(defn limits
  "The LIMIT lines that hold for the newest round, cap first, from the task's
  count of automatic send-backs and the newest round's size and base."
  [automatic size base]
  (cond-> []
    (>= automatic cap) (conj (str "LIMIT: cap " cap " of " cap " automatic send-backs used"))
    (over-limit? size base) (conj (str "LIMIT: growth size " size " is more than half over " base))))

;; output

(defn- text-lines
  "A record's block: its first text line after `  <from>: `, the other
  nonblank lines indented by four spaces."
  [{:keys [from text]}]
  (let [[first-line & more] (str/split-lines (or text ""))]
    (cons (str "  " from ": " first-line)
          (for [line more] (if (str/blank? line) "" (str "    " line))))))

(defn report
  "The lines of sf rounds: NO_ROUNDS, or a ROUND line per round (each with
  :n, :short, :size, :growth and :records) followed by its records' blocks,
  then limit-lines."
  [rounds limit-lines]
  (if (empty? rounds)
    ["NO_ROUNDS"]
    (vec (concat (for [{:keys [n short size growth records] :as round} rounds
                       line (cons (str "ROUND " n " " short " size:" size " growth:" growth
                                       " verdict:" (or (verdict-of round) "-"))
                                  (mapcat text-lines records))]
                   line)
                 limit-lines))))

;; sf verdict

(defn verdict-refusal
  "Why sf verdict cannot record verdict with text on the task whose newest
  record is state, or nil. judge is the judge role in roles.conf, or nil.
  The limits on continue are not checked here."
  [{:keys [state rounds verdict text judge]}]
  (let [current (peek (vec rounds))]
    (cond
      (not (some #{verdict} verdicts))
      (str "verdict must be converged, continue, needs-operator or stuck, not '" verdict "'")
      (str/blank? text) "verdict needs a reason"
      (nil? judge) "roles.conf has no judge; add `<role> <tier> subagent judge` to record verdicts"
      (not= "specifier" (:stage state))
      (str "task is at stage:" (:stage state) "; verdicts are recorded at stage:specifier")
      (not= "approval" (:needs state)) "task is not waiting for approval"
      (verdict-of current) (str "round " (:n current) " already has the verdict " (verdict-of current)))))
