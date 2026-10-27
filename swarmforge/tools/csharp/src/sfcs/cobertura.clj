(ns sfcs.cobertura
  (:require [babashka.fs :as fs]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [sfcore.crap :as crap]))

(defn- elements [tag node]
  (filter #(and (map? %) (= tag (:tag %))) (tree-seq map? :content node)))

(defn- text [node]
  (apply str (filter string? (:content node))))

(defn- number-attr [node attr default]
  (if-let [value (get (:attrs node) attr)]
    (parse-double value)
    default))

(defn- absolute-path? [filename]
  (or (fs/absolute? filename) (boolean (re-find #"^[A-Za-z]:[\\/]" filename))))

(defn- resolve-file [filename sources]
  (cond
    (str/blank? filename) ""
    (absolute-path? filename) filename
    :else (if-let [base (some #(when (fs/exists? (fs/path % filename)) %) sources)]
            (str (fs/path base filename))
            filename)))

(defn- method-entry [class-name file method]
  (let [numbers (map #(parse-long (:number (:attrs %))) (elements :line method))]
    (when (seq numbers)
      (let [complexity (number-attr method :complexity 1.0)
            coverage (number-attr method :line-rate 0.0)]
        {:class-name class-name
         :name (:name (:attrs method))
         :signature (or (:signature (:attrs method)) "")
         :file file
         :complexity complexity
         :coverage coverage
         :crap (crap/crap-score complexity coverage)
         :first-line (apply min numbers)
         :last-line (apply max numbers)}))))

(defn parse-cobertura
  "Reads every method out of a coverlet Cobertura file: its class, the source
  file (resolved against <source> entries when relative), cyclomatic
  complexity, line coverage, and the span of coverable lines."
  [xml-text]
  (let [doc (xml/parse-str (str/replace xml-text #"<!DOCTYPE[^>]*>" ""))
        sources (map text (elements :source doc))]
    (vec (for [class (elements :class doc)
               :let [class-name (:name (:attrs class))
                     file (resolve-file (:filename (:attrs class)) sources)]
               method (elements :method class)
               :let [entry (method-entry class-name file method)]
               :when entry]
           entry))))

(defn find-cobertura-files
  "coverage.cobertura.xml files under a results directory, newest first."
  [directory]
  (if (fs/exists? directory)
    (->> (fs/glob directory "**")
         (filter #(= "coverage.cobertura.xml" (fs/file-name %)))
         (sort-by #(- (fs/file-time->millis (fs/last-modified-time %))))
         (mapv str))
    []))

(defn signature-start
  "Widens a method's coverable span upward over its signature and attributes."
  [source-lines first-line]
  (loop [line first-line]
    (let [above (str/trim (or (nth source-lines (- line 2) nil) ""))]
      (if (or (= above "") (str/starts-with? above "//") (re-find #"[;{}]$" above))
        line
        (recur (dec line))))))

(defn format-method [{:keys [class-name signature-line first-line name] :as method}]
  (crap/format-row (assoc method
                          :line (or signature-line first-line)
                          :name (str (last (str/split class-name #"\.")) "." name))))
