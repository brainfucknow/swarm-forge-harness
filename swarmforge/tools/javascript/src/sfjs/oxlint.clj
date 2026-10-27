(ns sfjs.oxlint
  "oxlint as the complexity source: sf-js's own config and command line, its
  JSON report read into the functions sfjs.crap scores, and the
  oxlint-disable directives that can hide one."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.outcome :as outcome]
            [sfjs.commands :as commands]
            [sfjs.crap :as crap]))

(def config-path (str commands/oxlint-dir "/oxlintrc.json"))

(def ignore-path
  "An empty ignore file of sf-js's own. Without it oxlint reads the project's
  .eslintignore and leaves out the files it names."
  (str commands/oxlint-dir "/oxlintignore"))

(defn config
  "sf-js's own oxlint config: only the complexity rule, reporting every
  function, with eslint-disable comments ignored. oxlint resolves a config's
  ignorePatterns against the config's own directory, so the ignores go on the
  command line instead."
  []
  (json/generate-string {"categories" {"correctness" "off"}
                         "options" {"respectEslintDisableDirectives" false}
                         "rules" {"eslint/complexity" ["warn" 0]}}))

(defn args [config files]
  (-> ["oxlint" "--config" config "--disable-nested-config" "--ignore-path" ignore-path "--format" "json"]
      (into (mapcat #(vector "--ignore-pattern" %) commands/lint-ignores))
      (into files)))

(def ^:private version-requirement "sf-js needs oxlint 1.62.0 or later")

(defn function-name
  "The function's name in an oxlint complexity message: the one between
  backticks, # in front for a private method, else (anonymous)."
  [message]
  (let [kind (:kind (crap/complexity-message message))
        name (second (re-find #"`([^`]+)`" kind))]
    (cond
      (nil? name) "(anonymous)"
      (str/starts-with? kind "private") (str "#" name)
      :else name)))

(defn report
  "The JSON report in oxlint's stdout, or nil when it printed something else."
  [text]
  (let [parsed (try (json/parse-string text) (catch Exception _ nil))]
    (when (and (map? parsed) (contains? parsed "diagnostics"))
      parsed)))

(defn read-report
  "The JSON report oxlint printed with exit `status`; an input error when
  there is none or when oxlint ran no rule."
  [status text]
  (let [parsed (report text)]
    (cond
      (nil? parsed)
      (throw (outcome/usage-error (str "oxlint exited " status " without a report; " version-requirement)))
      (not (pos? (get parsed "number_of_rules" 0)))
      (throw (outcome/usage-error (str "oxlint ran no complexity rule; " version-requirement)))
      :else parsed)))

(defn parse-report
  "Every eslint(complexity) diagnostic in an oxlint report, with the file
  relative to `root`; a function ends on the line it starts."
  [parsed root]
  (vec (for [diagnostic (get parsed "diagnostics")
             :when (= "eslint(complexity)" (get diagnostic "code"))
             :let [text (get diagnostic "message")
                   span (get (first (get diagnostic "labels")) "span")]]
         {:file (changes/relative-path (get diagnostic "filename") root)
          :line (get span "line")
          :column (get span "column" 1)
          :end-line (get span "line")
          :name (function-name text)
          :complexity (:complexity (crap/complexity-message text))})))

(def ^:private directive
  "An oxlint-disable comment and the rule list after it, up to the end of the
  comment."
  #"(?://|/\*)\s*oxlint-disable(?:-next-line|-line)?(?![\w-])(.*?)(?:\*/|$)")

(defn- hides-complexity? [rule-list]
  (let [rules (remove str/blank? (str/split (first (str/split rule-list #"\s--(\s|$)")) #"[\s,]+"))]
    (or (empty? rules) (boolean (some #{"complexity" "eslint/complexity"} rules)))))

(defn hiding-lines
  "The line numbers of `text` holding an oxlint-disable directive that names
  no rule, or names complexity."
  [text]
  (vec (keep-indexed (fn [i line]
                       (when (some #(hides-complexity? (second %)) (re-seq directive line))
                         (inc i)))
                     (str/split-lines text))))
