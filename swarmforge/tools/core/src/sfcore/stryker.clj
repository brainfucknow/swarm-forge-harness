(ns sfcore.stryker
  (:require [clojure.string :as str]))

(def surviving #{"Survived" "NoCoverage"})

(defn parse-mutants
  "Reads a Stryker mutation-report.json (mutation-testing-elements schema)
  into a flat list of mutants with their file and line."
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

(def ^:private unscored #{"Ignored" "CompileError" "RuntimeError"})

(defn summarize [mutants]
  (let [counts (frequencies (map :status mutants))
        scored (remove #(unscored (:status %)) mutants)
        killed (count (remove #(surviving (:status %)) scored))
        score (if (empty? scored) 100.0 (* 100.0 (/ killed (count scored))))]
    {:counts counts :score score}))

(defn format-mutant [{:keys [status file line mutator description replacement]}]
  (let [detail (if (str/blank? description) replacement description)]
    (str (format "%-10s" status) " " file ":" line "  " mutator
         (when-not (str/blank? detail) (str "  " detail)))))

(defn format-summary [{:keys [counts score]}]
  (let [parts (str/join ", " (map (fn [[status n]] (str status " " n)) (sort counts)))]
    (str "mutation score " (String/format java.util.Locale/ROOT "%.1f" (to-array [(double score)]))
         "% (" (if (str/blank? parts) "no mutants" parts) ")")))
