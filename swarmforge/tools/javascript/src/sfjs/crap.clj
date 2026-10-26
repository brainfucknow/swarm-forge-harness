(ns sfjs.crap
  "CRAP per function from ESLint's complexity messages joined with Istanbul
  coverage (coverage-final.json)."
  (:require [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.crap :as crap]))

(defn function-name
  "The function's name in an ESLint complexity message, or (anonymous)."
  [message]
  (let [kind (first (str/split message #" has a complexity of "))]
    (or (second (re-find #"'(.+)'" kind))
        (second (re-find #"(#\S+)$" kind))
        "(anonymous)")))

(defn parse-eslint
  "Every complexity message in an ESLint JSON report, with the file relative
  to `root`."
  [report root]
  (vec (for [result report
             message (get result "messages")
             :when (= "complexity" (get message "ruleId"))
             :let [text (get message "message")]]
         {:file (changes/relative-path (get result "filePath") root)
          :line (get message "line")
          :column (get message "column" 1)
          :end-line (or (get message "endLine") (get message "line"))
          :name (function-name text)
          :complexity (parse-long (second (re-find #"has a complexity of (\d+)" text)))})))

(defn- position [point default-column]
  [(get point "line") (or (get point "column") default-column)])

(defn- span [location]
  {:start (position (get location "start") 0)
   :end (position (get location "end") Long/MAX_VALUE)})

(defn- inside? [{:keys [start end]} point]
  (and (<= (compare start point) 0) (<= (compare point end) 0)))

(defn- nested-in? [outer inner]
  (and (not= outer inner) (inside? outer (:start inner)) (inside? outer (:end inner))))

(defn- own-coverage [range nested statements function-hits]
  (let [own (filter (fn [{:keys [start]}]
                      (and (inside? range start) (not-any? #(inside? % start) nested)))
                    statements)]
    (cond
      (seq own) (double (/ (count (filter #(pos? (:hits %)) own)) (count own)))
      (pos? function-hits) 1.0
      :else 0.0)))

(defn function-coverage
  "Each Istanbul function of one file entry with the share of its own
  statements that ran; statements of nested functions are left out."
  [entry]
  (let [functions (for [[id f] (sort-by (comp parse-long key) (get entry "fnMap"))]
                    (merge (span (get f "loc")) {:name (get f "name") :hits (get-in entry ["f" id] 0)}))
        statements (for [[id location] (get entry "statementMap")]
                     (assoc (span location) :hits (get-in entry ["s" id] 0)))]
    (vec (for [function functions
               :let [nested (filter #(nested-in? function %) functions)]]
           (assoc function :coverage (own-coverage function nested statements (:hits function)))))))

(defn- innermost [functions]
  (last (sort-by :start functions)))

(defn- coverage-for
  "The innermost Istanbul function holding the ESLint function head, by
  position, else by line."
  [functions {:keys [line column]}]
  (or (innermost (filter #(inside? % [line (dec column)]) functions))
      (innermost (filter (fn [{:keys [start end]}] (<= (first start) line (first end))) functions))))

(defn- coverage-by-file [report root]
  (into {} (for [[path entry] report]
             [(changes/relative-path (get entry "path" path) root) (function-coverage entry)])))

(defn score
  "Joins ESLint functions with coverage; a function missing from coverage
  counts as uncovered and spans only its head."
  [eslint-functions coverage-report root]
  (let [by-file (coverage-by-file coverage-report root)]
    (vec (for [{:keys [file line end-line complexity] :as function} eslint-functions
               :let [covered (coverage-for (get by-file file) function)
                     coverage (if covered (:coverage covered) 0.0)]]
           (-> (select-keys function [:file :name :complexity])
               (assoc :line (min line (or (first (:start covered)) line))
                      :last-line (max end-line (or (first (:end covered)) end-line))
                      :coverage coverage
                      :crap (crap/crap-score complexity coverage)))))))

(defn select
  "The scored functions that touch a changed line (every one when `changed`
  is nil), worst first."
  [functions changed]
  (->> functions
       (filter #(or (nil? changed) (changes/overlaps? (:file %) (:line %) (:last-line %) changed)))
       crap/sort-worst-first))
