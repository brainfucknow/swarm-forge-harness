(ns sfjs.commands
  "The tool command lines sf-js runs, and which files are JavaScript or
  TypeScript sources."
  (:require [clojure.string :as str]))

(def coverage-dir "tmp/coverage")
(def jscpd-dir "tmp/jscpd")
(def eslint-dir "tmp/eslint")
(def oxlint-dir "tmp/oxlint")
(def stryker-dir "tmp/stryker")

(def coverage-file "coverage-final.json")
(def coverage-report (str coverage-dir "/" coverage-file))
(def jscpd-report (str jscpd-dir "/jscpd-report.json"))

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

(def ^:private ignored-dirs ["node_modules" "dist" "build" "coverage" "tmp"])

(def ^:private ignored-paths
  (into (mapv #(str "**/" % "/**") ignored-dirs) ["**/*.d.ts" "**/*.min.js"]))

(def ^:private jscpd ["npx" "--yes" "jscpd@4"])

(def jscpd-version-args (conj jscpd "--version"))

(defn jscpd-args [min-tokens]
  (into jscpd ["--format" "javascript,typescript,jsx,tsx"
               "--min-tokens" (str min-tokens) "--reporters" "json" "--output" jscpd-dir
               "--ignore" (str/join "," ignored-paths) "."]))

(def lint-ignores
  "What the complexity linters leave out, as globs."
  (into ignored-paths ["**/*.d.mts" "**/*.d.cts" "**/*.test.*" "**/*.spec.*" "**/__tests__/**"]))

(defn- ignored-path? [path]
  (or (str/ends-with? path ".min.js")
      (boolean (some (set ignored-dirs) (butlast (str/split path #"/"))))))

(defn linted-sources
  "The production sources the complexity linters do not leave out."
  [paths]
  (vec (remove ignored-path? (production-sources paths))))
