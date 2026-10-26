(ns sfjs.mutate-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfjs.mutate :as mutate]))

(deftest stryker-runs-incrementally-on-the-changed-files
  (is (= ["stryker" "run" "tmp/stryker/stryker.config.mjs"
          "--testRunner" "vitest" "--reporters" "json,progress"
          "--incremental" "--incrementalFile" "tmp/stryker/incremental.json"
          "--concurrency" "4" "--mutate" "src/a.ts,src/b.ts"]
         (mutate/stryker-args {:runner "vitest" :files ["src/a.ts" "src/b.ts"]}))))

(deftest stryker-options
  (testing "the TypeScript checker with a tsconfig.json"
    (is (str/includes? (str/join " " (mutate/stryker-args {:runner "jest" :tsconfig? true :files ["a.ts"]}))
                       "--testRunner jest --reporters json,progress"))
    (is (some #{"--checkers"} (mutate/stryker-args {:runner "jest" :tsconfig? true :files ["a.ts"]}))))
  (testing "--all leaves the files to Stryker's configuration"
    (is (not-any? #{"--mutate"} (mutate/stryker-args {:runner "vitest"}))))
  (testing "arguments after -- come last"
    (is (= ["--timeoutMS" "9000"] (take-last 2 (mutate/stryker-args {:runner "vitest" :extra ["--timeoutMS" "9000"]})))))
  (testing "a caller's concurrency replaces the default"
    (doseq [extra [["--concurrency" "2"] ["-c" "2"] ["--concurrency=2"]]]
      (let [args (mutate/stryker-args {:runner "vitest" :extra extra})]
        (is (not (str/includes? (str/join " " args) "--concurrency 4")) (pr-str extra))
        (is (= extra (take-last (count extra) args)))))))

(deftest generated-stryker-config
  (let [config (mutate/stryker-config)]
    (is (str/includes? config "fileName: \"tmp/stryker/mutation.json\""))
    (is (str/includes? config "\"stryker.config.mjs\""))
    (is (str/includes? config "\".stryker.conf.json\""))
    (is (str/includes? config "appendPlugins: [...(project.appendPlugins ?? []), ...linkedPlugins]"))))

(deftest changed-files-are-literal-mutate-patterns
  (testing "each glob character is wrapped in a one-character class"
    (is (= "pages/[[]id[]].ts" (mutate/literal-pattern "pages/[id].ts")))
    (is (= "pages/[[]...slug[]].tsx" (mutate/literal-pattern "pages/[...slug].tsx")))
    (is (= "src/a[*]b[?]c.ts" (mutate/literal-pattern "src/a*b?c.ts")))
    (is (= "src/[{]a[}]@[(]x[)].ts" (mutate/literal-pattern "src/{a}@(x).ts")))
    (is (= "src/plain-name_1.ts" (mutate/literal-pattern "src/plain-name_1.ts"))))
  (testing "the --mutate list holds the escaped files"
    (is (= ["--mutate" "pages/[[]id[]].ts,src/a.ts"]
           (take-last 2 (mutate/stryker-args {:runner "vitest" :files ["pages/[id].ts" "src/a.ts"]}))))))

(deftest file-names-with-commas-cannot-be-mutated
  (is (= ["cannot mutate src/a,b.ts: Stryker splits --mutate on commas"
          "cannot mutate x,y.js: Stryker splits --mutate on commas"]
         (mutate/comma-errors ["src/a,b.ts" "src/c.ts" "x,y.js"])))
  (is (= [] (mutate/comma-errors ["src/c.ts"]))))

(deftest mutants-in-scope
  (let [mutants [{:file "src/a.ts" :line 1} {:file "src/old.ts" :line 2} {:file "/repo/src/b.ts" :line 3}]]
    (testing "a differential run keeps only the mutants in the changed files"
      (is (= [1 3] (map :line (mutate/in-scope mutants ["src/a.ts" "src/b.ts"] "/repo")))))
    (testing "nil files keep every mutant"
      (is (= mutants (mutate/in-scope mutants nil "/repo"))))))
