(ns sfjs.commands-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfjs.commands :as commands]))

(deftest test-runs-leave-out-property-tests
  (is (= ["vitest" "run" "--exclude" "**/*.property.test.*" "--reporter=dot"]
         (commands/test-args "vitest" ["--reporter=dot"])))
  (is (= ["jest" "--testPathIgnorePatterns" "/node_modules/" "--testPathIgnorePatterns" "\\.property\\.test\\." "--ci"]
         (commands/test-args "jest" ["--ci"]))))

(deftest property-runs-only-property-tests
  (is (= ["vitest" "run" ".property.test."] (commands/property-args "vitest" [])))
  (is (= ["jest" "\\.property\\.test\\." "--ci"] (commands/property-args "jest" ["--ci"]))))

(deftest coverage-writes-istanbul-json-under-tmp
  (let [vitest (commands/coverage-args "vitest" ["--bail=1"])
        jest (commands/coverage-args "jest" [])]
    (is (= ["vitest" "run" "--coverage.enabled" "--coverage.provider=v8" "--coverage.reporter=json"
            "--coverage.reportsDirectory=tmp/coverage" "--exclude" "**/*.property.test.*" "--bail=1"]
           vitest))
    (is (= ["jest" "--coverage" "--coverageReporters=json" "--coverageDirectory=tmp/coverage"
            "--testPathIgnorePatterns" "/node_modules/" "--testPathIgnorePatterns" "\\.property\\.test\\."]
           jest))))

(deftest jscpd-covers-javascript-and-typescript
  (let [args (commands/jscpd-args 30)
        line (str/join " " args)]
    (is (= ["npx" "--yes" "jscpd@4"] (take 3 args)))
    (is (str/includes? line "--format javascript,typescript,jsx,tsx"))
    (is (str/includes? line "--min-tokens 30"))
    (is (str/includes? line "--reporters json --output tmp/jscpd"))
    (doseq [ignored ["**/node_modules/**" "**/dist/**" "**/build/**" "**/coverage/**" "**/tmp/**" "**/*.d.ts" "**/*.min.js"]]
      (is (str/includes? line ignored) ignored))
    (is (= "." (last args)))))

(deftest source-files
  (testing "JavaScript and TypeScript sources, declarations left out"
    (is (= [true true true true true true true true false false false false]
           (map commands/source-file?
                ["a.js" "a.jsx" "a.ts" "a.tsx" "a.mjs" "a.cjs" "a.mts" "a.cts" "a.d.ts" "a.d.mts" "a.json" "a.cs"]))))
  (testing "test files are not production sources"
    (is (= [true true true true false]
           (map commands/test-file?
                ["src/a.test.ts" "src/a.spec.js" "src/a.property.test.ts" "src/__tests__/a.ts" "src/attest.ts"]))))
  (is (= ["src/a.ts" "src/b.mjs"]
         (commands/production-sources ["src/b.mjs" "src/a.test.ts" "src/a.ts" "src/types.d.ts" "README.md"]))))
