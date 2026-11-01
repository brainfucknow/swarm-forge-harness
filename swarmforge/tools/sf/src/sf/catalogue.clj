(ns sf.catalogue
  "The rows of swarmforge/tools.conf as tools, and the rules a row must keep:
  its column count, a pinned ref of the form its kind needs, a GitHub source
  github.com/<owner>/<repo> for the kinds that clone one, and one ref for rows
  that share a source."
  (:require [clojure.string :as str]))

(def columns
  "<name> <kind> <source> <ref> <task> <rewrite> <needs>")

(def kinds
  "The kinds sf tool can install."
  #{"bb" "bbmain" "clj" "local"})

(def rewrites
  "The argument rewrites sf tool can write."
  #{"mutate" "gherkin" "coverage" "-"})

(defn tool
  "The tool a row of fields describes."
  [[name kind source ref task rewrite needs :as row]]
  {:name name :kind kind :source source :ref ref :task task :rewrite rewrite
   :needs (if (contains? #{nil "-"} needs) [] (str/split needs #","))
   :columns (count row)})

(defn clones?
  "True for the kinds whose source is a GitHub repository sf tool clones."
  [kind]
  (contains? #{"bb" "bbmain"} kind))

(defn- safe-part? [part]
  (and (re-matches #"[A-Za-z0-9._-]+" part) (not (#{"." ".."} part))))

(defn github-repo
  "The repository name of a source github.com/<owner>/<repo> whose parts use
  only letters, digits, \".\", \"_\" and \"-\" and are not \".\" or \"..\";
  nil for any other source."
  [source]
  (let [[host owner repo & more] (str/split (str source) #"/" -1)]
    (when (and (= "github.com" host) (nil? more) owner repo (safe-part? owner) (safe-part? repo))
      repo)))

(defn commit-sha?
  "True for a full commit SHA: 40 lowercase hexadecimal digits."
  [ref]
  (boolean (re-matches #"[0-9a-f]{40}" (str ref))))

(defn maven-version?
  "True for a pinned Maven version: a digit first, then letters, digits, \".\",
  \"_\", \"+\" and \"-\", and no -SNAPSHOT at the end."
  [ref]
  (boolean (and (re-matches #"[0-9][A-Za-z0-9._+-]*" (str ref))
                (not (str/ends-with? ref "-SNAPSHOT")))))

(defn- ref-problem [{:keys [name kind ref]}]
  (cond
    (and (clones? kind) (not (commit-sha? ref)))
    (str "'" name "' needs a 40-digit lowercase hex commit SHA as its ref, not '" ref "'")
    (and (= "clj" kind) (not (maven-version? ref)))
    (str "'" name "' needs a pinned Maven version as its ref, not '" ref "'")
    (and (= "local" kind) (not= "-" ref))
    (str "'" name "' is local and needs '-' as its ref, not '" ref "'")))

(defn- source-problem [{:keys [name kind source]}]
  (when (and (clones? kind) (not (github-repo source)))
    (str "'" name "' needs a source github.com/<owner>/<repo>, not '" source "'")))

(defn- shared-ref-problem
  "The tool's ref against the first of tools with the same source."
  [{:keys [name source ref]} tools]
  (let [first-row (some #(when (= source (:source %)) %) tools)]
    (when (and first-row (not= ref (:ref first-row)))
      (str "'" name "' pins " source " at a different ref than '" (:name first-row) "'"))))

(defn ref-problems
  "The problems of the tool's columns, ref and source, then of its ref against
  the other tools of its source. Only the column count is checked on a row
  with the wrong number of columns."
  [tool tools]
  (if (not= 7 (:columns tool))
    [(str "'" (:name tool) "' needs exactly " columns)]
    (vec (keep identity [(ref-problem tool) (source-problem tool) (shared-ref-problem tool tools)]))))
