(ns sfcs.cobertura
  (:require [babashka.fs :as fs]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [sfcore.crap :as crap]
            [sfcore.outcome :as outcome]))

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

(defn- usable-method-data? [lines complexity coverage]
  (and (seq lines) (every? #(and (int? %) (pos? %)) lines)
       (number? complexity) (<= 0 complexity Double/MAX_VALUE)
       (number? coverage) (<= 0 coverage 1)))

(defn- method-entry [class-name file method strict?]
  (let [numbers (map #(parse-long (:number (:attrs %))) (elements :line method))]
    (when (or strict? (seq numbers))
      (let [complexity (number-attr method :complexity (when-not strict? 1.0))
            coverage (number-attr method :line-rate (when-not strict? 0.0))]
        (when (and strict?
                   (not (usable-method-data? numbers complexity coverage)))
          (throw (outcome/usage-error (str "unusable method data: " file " " class-name "." (:name (:attrs method))))))
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
  ([xml-text] (parse-cobertura xml-text nil))
  ([xml-text {:keys [extension strict?]}]
   (let [doc (xml/parse-str (-> xml-text (str/replace #"^\uFEFF" "") (str/replace #"<!DOCTYPE[^>]*>" "")))
         sources (map text (elements :source doc))]
     (vec (for [class (elements :class doc)
                :let [class-name (:name (:attrs class))
                      file (resolve-file (:filename (:attrs class)) sources)]
                :when (or (nil? extension) (str/ends-with? file extension))
                method (elements :method class)
                :let [entry (method-entry class-name file method strict?)]
                :when entry]
            entry)))))

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
