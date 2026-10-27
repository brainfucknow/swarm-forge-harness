(ns sfjs.eslint
  "ESLint as the complexity source: sf-js's own config and command line, and
  its JSON report read into the functions sfjs.crap scores."
  (:require [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfjs.commands :as commands]
            [sfjs.crap :as crap]))

(def config-path (str commands/eslint-dir "/eslint.config.mjs"))
(def report-path (str commands/eslint-dir "/eslint.json"))

(defn config
  "sf-js's own flat config: the typescript-eslint parser and only the
  complexity rule, reporting every function. Written under tmp/ so that
  typescript-eslint resolves from the project's node_modules."
  []
  (str "import tseslint from \"typescript-eslint\";\n\n"
       "export default [\n"
       "  { ignores: [" (str/join ", " (map pr-str commands/lint-ignores)) "] },\n"
       "  {\n"
       "    files: [\"**/*.{" (str/join "," commands/extensions) "}\"],\n"
       "    languageOptions: { parser: tseslint.parser, parserOptions: { ecmaFeatures: { jsx: true } } },\n"
       "    linterOptions: { reportUnusedDisableDirectives: \"off\" },\n"
       "    rules: { complexity: [\"warn\", 0] },\n"
       "  },\n"
       "];\n"))

(defn args [config output files]
  (into ["eslint" "--config" config "--no-inline-config" "--format" "json" "--output-file" output] files))

(def exit-failed
  "ESLint's own exit status for a configuration or internal error; 1 only
  means error messages, and every complexity message is one at maximum 0."
  2)

(defn fatal-messages
  "Each fatal message in an ESLint JSON report (a file ESLint could not
  parse) as FILE:LINE: MESSAGE, with the file relative to `root`."
  [report root]
  (vec (for [result report
             message (get result "messages")
             :when (true? (get message "fatal"))]
         (str (changes/relative-path (get result "filePath") root) ":"
              (get message "line") ": " (get message "message")))))

(defn function-name
  "The function's name in an ESLint complexity message, or (anonymous)."
  [message]
  (let [kind (:kind (crap/complexity-message message))]
    (or (second (re-find #"'(.+)'" kind))
        (re-find #"#\S+$" kind)
        "(anonymous)")))

(defn parse-report
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
          :complexity (:complexity (crap/complexity-message text))})))
