(ns sf.missing-tools
  "The missing-tool check of sf handoff and sf review, as plain values: which
  catalogue tools the Not-tested trailers of a role's commits name, which of
  them an answered question accepts, and the refusal lines."
  (:require [clojure.string :as str]
            [sf.record :as record]))

(defn- position
  "The index where name first appears in text, ignoring case, bounded on each
  side by the text's edge or a character other than a letter, a digit, - or _;
  nil when it does not."
  [text name]
  (let [matcher (re-matcher (re-pattern (str "(?iu)(?<![\\p{L}\\p{N}_-])"
                                             (java.util.regex.Pattern/quote name)
                                             "(?![\\p{L}\\p{N}_-])"))
                            text)]
    (when (.find matcher) (.start matcher))))

(defn named
  "The names that text names, in the order they first appear."
  [names text]
  (->> names
       (keep #(when-let [at (position (str text) %)] [at %]))
       (sort-by first)
       (mapv second)))

(defn- not-tested-values
  "The values of the Not-tested trailers among trailer lines, key in any case."
  [trailers]
  (keep #(second (re-matches #"(?is)not-tested:\s*(.*)" %)) trailers))

(defn named-in-commits
  "The names that the Not-tested trailers of commits name, each once, in the
  order the commits (oldest first), their trailers and their texts name them.
  Each commit is its trailer lines."
  [names commits]
  (vec (distinct (mapcat #(named names %) (mapcat not-tested-values commits)))))

(defn- since-milestone
  "The records after the newest created, review or handoff record."
  [records]
  (reverse (take-while (complement record/milestone?) (reverse records))))

(defn accepted
  "The tools among missing that a question record after the newest milestone
  names, with an answer from the operator after that question."
  [missing records]
  (let [recent (vec (since-milestone records))]
    (set (for [[at {:keys [record text]}] (map-indexed vector recent)
               :when (and (= "question" record)
                          (some #(and (= "answer" (:record %)) (= "operator" (:from %)))
                                (subvec recent (inc at))))
               tool (named missing text)]
           tool))))

(defn refusal
  "The lines of the refusal of task id over the missing tools: an ALLOW_REFUSED
  line for each tool in refused, then each missing tool and its install
  command, then what to do."
  [id missing refused]
  (concat
   (for [tool refused]
     (str "ALLOW_REFUSED: " id " " tool " has no answered question since the last record"))
   (mapcat (fn [tool]
             [(str "MISSING_TOOL: " id " " tool " is named in Not-tested but is not installed; nothing was recorded")
              (str "Run: sf tool ensure " tool)])
           missing)
   ["Then run the check, or record Not-tested with the install or run error. If the install fails, run sf ask; after the operator answers, pass --allow-missing-tool."]))
