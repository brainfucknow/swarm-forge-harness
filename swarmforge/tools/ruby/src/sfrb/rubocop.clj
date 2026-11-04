(ns sfrb.rubocop
  "Method complexity from one RuboCop metrics cop, run with sf-rb's own
  configuration so the project's RuboCop settings cannot change the gate."
  (:require [clojure.string :as str]
            [sfcore.changes :as changes]))

(def cops
  {"cyclomatic" "Metrics/CyclomaticComplexity"
   "perceived" "Metrics/PerceivedComplexity"})

(defn config-path [source]
  (str "tmp/rubocop/" source ".yml"))

(defn config
  "Only `cop`, at Max 0 so it reports every method. RuboCop reads the file
  through ERB: the target is the Ruby running it."
  [cop]
  (str "AllCops:\n"
       "  DisabledByDefault: true\n"
       "  NewCops: disable\n"
       "  SuggestExtensions: false\n"
       "  TargetRubyVersion: <%= RUBY_VERSION[/\\A\\d+\\.\\d+/] %>\n"
       cop ":\n"
       "  Enabled: true\n"
       "  Max: 0\n"))

(defn args
  "`bundle` arguments running RuboCop with `config` over `files`; disable
  comments are ignored."
  [config files]
  (into ["exec" "rubocop" "--config" config "--ignore-disable-comments" "--cache" "false" "--format" "json" "--"]
        files))

(def exit-failed
  "RuboCop's status when it could not run; 1 only means offenses."
  2)

(defn- offenses [report root]
  (for [file (get report "files")
        offense (get file "offenses")]
    (assoc offense :file (changes/relative-path (get file "path") root))))

(defn- syntax-error? [offense]
  (or (= "fatal" (get offense "severity")) (= "Lint/Syntax" (get offense "cop_name"))))

(defn analysis-errors
  "One line per source RuboCop could not parse."
  [report root]
  (vec (for [{:keys [file] :as offense} (offenses report root)
             :when (syntax-error? offense)]
         (str "rubocop could not parse " file ":" (get-in offense ["location" "start_line"]) ": "
              (first (str/split-lines (get offense "message" "")))))))

(defn parse-report
  "The methods `cop` reported, each with its file, span, name and complexity."
  [report cop root]
  (vec (for [{:keys [file] :as offense} (offenses report root)
             :when (= cop (get offense "cop_name"))
             :let [[_ name complexity] (re-find #"for `([^`]*)` is too high\. \[(\d+)/" (get offense "message" ""))]
             :when complexity]
         {:file file
          :line (get-in offense ["location" "start_line"])
          :last-line (get-in offense ["location" "last_line"])
          :name name
          :complexity (parse-long complexity)})))
