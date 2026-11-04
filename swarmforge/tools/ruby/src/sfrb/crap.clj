(ns sfrb.crap
  "CRAP per method: analysed methods joined with SimpleCov's JSON coverage."
  (:require [sfcore.changes :as changes]
            [sfcore.crap :as crap]))

(def limits
  "The CRAP limit per complexity source; flog's ABC score runs higher than a
  cyclomatic count."
  {"cyclomatic" crap/crap-limit "perceived" crap/crap-limit "flog" 20})

(defn- nested-in? [outer inner]
  (and (not= outer inner)
       (<= (:line outer) (:line inner))
       (<= (:last-line inner) (:last-line outer))))

(defn- own-lines
  "The method's lines after its definition line, less nested methods' spans."
  [method methods]
  (let [nested (filter #(nested-in? method %) methods)]
    (remove (fn [line] (some #(<= (:line %) line (:last-line %)) nested))
            (range (inc (:line method)) (inc (:last-line method))))))

(defn- called? [{:keys [line]} simplecov-methods]
  (boolean (some #(and (= line (get % "start_line")) (pos? (get % "coverage" 0))) simplecov-methods)))

(defn method-coverage
  "The share of the method's own relevant lines that ran. Without such a line,
  1.0 when SimpleCov counts the method called, else 0.0."
  [method methods {:strs [lines] :as entry}]
  (let [relevant (keep #(let [hits (get lines (dec %))] (when (number? hits) hits))
                       (own-lines method methods))]
    (cond
      (seq relevant) (double (/ (count (filter pos? relevant)) (count relevant)))
      (called? method (get entry "methods")) 1.0
      :else 0.0)))

(defn- coverage-by-file [report root]
  (into {} (for [[path entry] (get report "coverage")]
             [(changes/relative-path path root) entry])))

(defn score
  "Each analysed method with its coverage and CRAP; a method in a file
  SimpleCov did not report counts as uncovered."
  [methods report root]
  (let [by-file (coverage-by-file report root)
        in-file (group-by :file methods)]
    (vec (for [{:keys [file complexity] :as method} methods
               :let [entry (get by-file file)
                     coverage (if entry (method-coverage method (in-file file) entry) 0.0)]]
           (assoc method :coverage coverage :crap (crap/crap-score complexity coverage))))))
