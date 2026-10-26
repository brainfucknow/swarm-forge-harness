(ns sfcs.stryker
  (:require [clojure.string :as str]))

(def surviving #{"Survived" "NoCoverage"})

(defn parse-mutants
  "Reads a Stryker mutation-report.json (mutation-testing-elements schema)
  into a flat list of mutants, each with its file and line."
  [report]
  (->> (get report "files")
       (mapcat (fn [[file entry]]
                 (map (fn [mutant]
                        {:file file
                         :line (or (get-in mutant ["location" "start" "line"]) 0)
                         :status (get mutant "status")
                         :mutator (get mutant "mutatorName")
                         :replacement (or (get mutant "replacement") "")
                         :description (or (get mutant "description") "")})
                      (get entry "mutants"))))
       (sort-by (juxt :file :line))
       vec))

(defn survivors [mutants]
  (filterv #(surviving (:status %)) mutants))

(defn summarize [mutants]
  (let [counts (frequencies (map :status mutants))
        scored (remove #(#{"Ignored" "CompileError"} (:status %)) mutants)
        killed (count (remove #(surviving (:status %)) scored))
        score (if (empty? scored) 100.0 (* 100.0 (/ killed (count scored))))]
    {:counts counts :score score}))

(defn format-mutant [{:keys [status file line mutator description replacement]}]
  (let [detail (if (str/blank? description) replacement description)]
    (str (format "%-10s" status) " " file ":" line "  " mutator
         (when-not (str/blank? detail) (str "  " detail)))))
