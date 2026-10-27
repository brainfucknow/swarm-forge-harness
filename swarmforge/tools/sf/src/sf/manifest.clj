(ns sf.manifest
  "Mutation manifests kept in git notes: which copy of a path is current,
  which files to load or save, and the Tested: trailer of each manifest."
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [sf.feature-block :as feature-block]))

(def notes-ref "refs/notes/mutate")
(def root ".metrics/")
(def ^:private clj-mutate-root ".metrics/mutate/")
(def ^:private gherkin-root ".metrics/gherkin/")

(defn saved-block-path
  "Where the gherkin-mutator block of a feature is kept between runs."
  [feature]
  (str gherkin-root feature))

(defn newest-copies
  "The copy of each path from the first note that holds it. notes are maps of
  path to content, newest commit first."
  [notes]
  (apply merge {} (reverse notes)))

(defn to-load
  "The copies whose paths are not in the worktree."
  [copies existing-paths]
  (apply dissoc copies existing-paths))

(defn to-save
  "The files whose content is not the same as their copy."
  [files copies]
  (into {} (remove (fn [[path content]] (= content (get copies path))) files)))

(defn encode-note [files]
  (str (pr-str (into (sorted-map) files)) "\n"))

(defn decode-note
  "The files of a note, or an empty map when the note is not one of ours."
  [text]
  (let [value (try (edn/read-string text) (catch Exception _ nil))]
    (if (and (map? value) (every? string? (concat (keys value) (vals value))))
      value
      {})))

(defn- counts [kind subject killed survived & extra]
  (str "Tested: " kind " " subject " | " killed " killed, " survived " survived"
       (apply str (map #(str ", " %) extra))))

(defn- sum [key items]
  (reduce + 0 (map #(or (get % key) 0) items)))

(defn- clj-mutate-line [content]
  (let [{:keys [source forms] :as manifest} (edn/read-string content)]
    (when (and (map? manifest) (string? source) (sequential? forms))
      (let [uncovered (sum :uncovered forms)]
        (apply counts "clj-mutate" source (sum :killed forms) (sum :survived forms)
               (when (pos? uncovered) [(str uncovered " uncovered")]))))))

(defn- gherkin-line [feature content]
  (when-let [text (feature-block/manifest-json (str/split-lines content))]
    (let [results (map :result (:scenarios (json/parse-string text true)))]
      (counts "gherkin-mutator" feature (sum :Killed results) (sum :Survived results)))))

(defn tested-line
  "The Tested: trailer of a manifest file, or nil when it is not a manifest."
  [path content]
  (try
    (cond
      (str/starts-with? path clj-mutate-root) (clj-mutate-line content)
      (str/starts-with? path gherkin-root) (gherkin-line (subs path (count gherkin-root)) content)
      :else nil)
    (catch Exception _ nil)))

(defn tested-lines
  "The Tested: trailers of the files, in path order."
  [files]
  (keep (fn [[path content]] (tested-line path content)) (sort-by key files)))
