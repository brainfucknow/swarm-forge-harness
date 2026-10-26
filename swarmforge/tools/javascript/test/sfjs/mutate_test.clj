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
