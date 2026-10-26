(ns sfcore.jscpd
  "jscpd JSON reports: the clones, which of them touch changed lines, and the
  report every sf-* dry command prints."
  (:require [sfcore.changes :as changes]
            [sfcore.outcome :as outcome]))

(defn- side [fragment]
  {:file (get fragment "name") :start (get fragment "start") :end (get fragment "end")})

(defn parse-clones
  "Flattens a jscpd JSON report into clones with both sides' file and range."
  [report]
  (mapv (fn [duplicate]
          {:tokens (get duplicate "tokens")
           :lines (get duplicate "lines")
           :sides [(side (get duplicate "firstFile")) (side (get duplicate "secondFile"))]})
        (get report "duplicates")))

(defn clones-touching-changes
  "Marks each side that touches a changed line; keeps clones with such a side."
  [clones changed root]
  (->> clones
       (map (fn [clone]
              (update clone :sides
                      (fn [sides]
                        (mapv (fn [{:keys [file start end] :as s}]
                                (let [matched (changes/to-changed-path file changed root)]
                                  (assoc s
                                         :file (or matched file)
                                         :changed (boolean (and matched (changes/overlaps? matched start end changed))))))
                              sides)))))
       (filter #(some :changed (:sides %)))
       vec))

(defn format-clone [{:keys [tokens sides]}]
  (let [one (fn [{:keys [file start end changed]}]
              (str file ":" start "-" end (when changed " (changed)")))]
    (str tokens " tokens  " (one (first sides)) "  ==  " (one (second sides)))))

(defn format-summary [shown clones changed?]
  (str (count shown) " of " (count clones) " clone(s)" (when changed? " touch changed lines")))

(defn review
  "The clones touching `changed` (every clone when it is nil), then the
  summary; findings when any is shown."
  [clones changed root]
  (let [shown (if changed (clones-touching-changes clones changed root) clones)]
    (outcome/report (conj (mapv format-clone shown) (format-summary shown clones changed))
                    (seq shown))))
