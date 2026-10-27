(ns sfjs.commands
  "The tool command lines sf-js runs, and which files are JavaScript or
  TypeScript sources."
  (:require [clojure.string :as str]))

(def tmp "tmp")
(def coverage-dir "tmp/coverage")
(def jscpd-dir "tmp/jscpd")
(def eslint-dir "tmp/eslint")
(def stryker-dir "tmp/stryker")

(def coverage-file "coverage-final.json")
(def coverage-report (str coverage-dir "/" coverage-file))
(def jscpd-report (str jscpd-dir "/jscpd-report.json"))
(def eslint-config-path (str eslint-dir "/eslint.config.mjs"))
(def eslint-report (str eslint-dir "/eslint.json"))

(def extensions ["js" "jsx" "ts" "tsx" "mjs" "cjs" "mts" "cts"])
(def ^:private declarations ["d.ts" "d.mts" "d.cts"])

(def source-pathspecs
  "git pathspecs for the changed-file scope."
  (vec (concat (map #(str "*." %) extensions)
               (map #(str ":(exclude)*." %) declarations))))

(defn source-file? [path]
  (boolean (and (some #(str/ends-with? path (str "." %)) extensions)
                (not-any? #(str/ends-with? path (str "." %)) declarations))))

(defn test-file? [path]
  (boolean (or (re-find #"\.(test|spec)\.[^/]+$" path)
               (re-find #"(^|/)__tests__/" path))))

(defn production-sources [paths]
  (vec (sort (filter #(and (source-file? %) (not (test-file? %))) paths))))

(def ^:private property-glob "**/*.property.test.*")
(def ^:private property-regex "\\.property\\.test\\.")
(def ^:private jest-ignores
  ["--testPathIgnorePatterns" "/node_modules/" "--testPathIgnorePatterns" property-regex])

(defn test-args [runner extra]
  (into (case runner
          "vitest" ["vitest" "run" "--exclude" property-glob]
          "jest" (into ["jest"] jest-ignores))
        extra))

(defn property-args [runner extra]
  (into (case runner
          "vitest" ["vitest" "run" ".property.test."]
          "jest" ["jest" property-regex])
        extra))

(defn coverage-args [runner extra]
  (into (case runner
          "vitest" ["vitest" "run" "--coverage.enabled" "--coverage.provider=v8" "--coverage.reporter=json"
                    (str "--coverage.reportsDirectory=" coverage-dir) "--exclude" property-glob]
          "jest" (into ["jest" "--coverage" "--coverageReporters=json" (str "--coverageDirectory=" coverage-dir)]
                       jest-ignores))
        extra))

(def typecheck-args ["tsc" "--noEmit"])

(def ^:private ignored-paths
  ["**/node_modules/**" "**/dist/**" "**/build/**" "**/coverage/**" "**/tmp/**" "**/*.d.ts" "**/*.min.js"])

(def ^:private jscpd ["npx" "--yes" "jscpd@4"])

(def jscpd-version-args (conj jscpd "--version"))

(defn jscpd-args [min-tokens]
  (into jscpd ["--format" "javascript,typescript,jsx,tsx"
               "--min-tokens" (str min-tokens) "--reporters" "json" "--output" jscpd-dir
               "--ignore" (str/join "," ignored-paths) "."]))

(def ^:private lint-ignores
  (into ignored-paths ["**/*.d.mts" "**/*.d.cts" "**/*.test.*" "**/*.spec.*" "**/__tests__/**"]))

(defn eslint-config
  "sf-js's own flat config: the typescript-eslint parser and only the
  complexity rule, reporting every function. Written under tmp/ so that
  typescript-eslint resolves from the project's node_modules."
  []
  (str "import tseslint from \"typescript-eslint\";\n\n"
       "export default [\n"
       "  { ignores: [" (str/join ", " (map pr-str lint-ignores)) "] },\n"
       "  {\n"
       "    files: [\"**/*.{" (str/join "," extensions) "}\"],\n"
       "    languageOptions: { parser: tseslint.parser, parserOptions: { ecmaFeatures: { jsx: true } } },\n"
       "    linterOptions: { reportUnusedDisableDirectives: \"off\" },\n"
       "    rules: { complexity: [\"warn\", 0] },\n"
       "  },\n"
       "];\n"))

(defn eslint-args [config output files]
  (into ["eslint" "--config" config "--no-inline-config" "--format" "json" "--output-file" output] files))
