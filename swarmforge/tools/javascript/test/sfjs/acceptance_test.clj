(ns sfjs.acceptance-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]
            [sfjs.acceptance.generator :as generator]
            [sfjs.acceptance.pipeline :as pipeline]
            [sfjs.acceptance.runtime :as runtime]
            [sfjs.acceptance.worker :as worker]))

(def feature
  {"background" [{"keyword" "Given" "text" "a world"}]
   "scenarios" [{"name" "s1" "steps" [{"keyword" "Then" "text" "the value is <v>" "parameters" ["v"]}]
                 "examples" [{"v" "1"} {"v" "2"}]}
                {"name" "s2" "steps" [{"keyword" "Then" "text" "nothing"}] "examples" []}]})

(def handlers
  {:steps [[#"a world" (fn [w] (assoc w :ready true))]
           [#"the value is (\d+)" (fn [w v] (when-not (and (:ready w) (= v "1")) (throw (ex-info (str "got " v) {}))) w)]
           [#"nothing" identity]
           [#"noth.*" identity]]
   :fresh (constantly {})
   :cleanup (constantly nil)})

(deftest executions-expand-examples
  (is (= [["s1/example_1" {"v" "1"} 2] ["s1/example_2" {"v" "2"} 2]]
         (map (juxt :name :example (comp count :steps)) (runtime/executions feature 0))))
  (is (= [["s2/example_1" {}]] (map (juxt :name :example) (runtime/executions feature 1))))
  (is (thrown-with-msg? Exception #"no example value for <v>"
                        (runtime/resolve-step {"text" "the value is <v>" "parameters" ["v"]} {}))))

(deftest executions-report-failures
  (let [[first-run second-run] (runtime/executions feature 0)]
    (is (nil? (runtime/run-execution handlers first-run)))
    (is (= "Then the value is 2: got 2" (runtime/run-execution handlers second-run))))
  (testing "unsupported and ambiguous steps fail"
    (is (str/includes? (runtime/run-execution handlers {:steps [{"keyword" "Given" "text" "huh"}] :example {}})
                       "unsupported step: huh"))
    (is (str/includes? (runtime/run-execution handlers (first (runtime/executions feature 1)))
                       "ambiguous step: nothing"))))

(deftest generated-entry-points
  (is (= "swarmforge-tools-javascript-features-crap-feature" (generator/slug "swarmforge/tools/javascript/features/crap.feature")))
  (is (= "swarmforge/tools/javascript/features/crap.feature" (generator/feature-path "tmp/x/ir/crap.json")))
  (let [source (generator/entry-point "f/a.feature" "{\"scenarios\":[{\"name\":\"one\"},{\"name\":\"two\"}]}")]
    (is (str/includes? source "(ns sfjs.generated.f-a-feature"))
    (is (str/includes? source "(runtime/run-scenario steps/handlers ir-json 1)"))
    (is (= source (generator/entry-point "f/a.feature" "{\"scenarios\":[{\"name\":\"one\"},{\"name\":\"two\"}]}")))))

(defn- stderr-of [f]
  (let [err (java.io.StringWriter.)]
    [(binding [*err* err] (f)) (str/trim (str err))]))

(deftest generator-command-line
  (is (= [2 "usage: acceptance-entrypoint-generator <json-ir> <generated-test-output>"]
         (stderr-of #(generator/run-command ["only-one"]))))
  (let [[status message] (stderr-of #(generator/run-command ["missing.json" "out"]))]
    (is (= 1 status))
    (is (str/includes? message "missing.json")))
  (let [dir (fs/create-temp-dir {:prefix "sfjs-generator"})
        ir (str (fs/path dir "crap.json"))]
    (try
      (spit ir "{\"scenarios\":[]}")
      (is (= [0 ""] (stderr-of #(generator/run-command [ir (str (fs/path dir "out"))]))))
      (is (fs/exists? (fs/path dir "out" "metadata")))
      (finally (fs/delete-tree dir)))))

(defn- generated-dir [tests]
  (let [dir (fs/create-temp-dir {:prefix "sfjs-worker"})]
    (spit (str (fs/path dir "feature.json")) "{}")
    (when tests
      (spit (str (fs/path dir "entry.clj"))
            (str "(ns sfjs.generated.worker-" (str/replace (str (fs/file-name dir)) #"\W" "-") "\n"
                 "  (:require [clojure.test :refer [deftest is]]))\n"
                 "(deftest outcome (is " tests "))\n")))
    (str dir)))

(defn- job [dir]
  (worker/run-job {"id" "j1" "feature_json" (str (fs/path dir "feature.json")) "generated_dir" dir}))

(deftest worker-jobs-report-test-outcomes
  (let [passing (generated-dir "true")
        failing (generated-dir "false")
        empty-dir (generated-dir nil)]
    (try
      (is (= ["j1" "test_success" ""] ((juxt :id :outcome :error) (job passing))))
      (is (= "test_failure" (:outcome (job failing))))
      (is (str/includes? (:output (job failing)) "FAIL"))
      (is (= "test_success" (:outcome (job passing))) "a directory's entry points load once")
      (is (= ["infrastructure_error" (str "no generated entry points in " empty-dir)]
             ((juxt :outcome :error) (job empty-dir))))
      (is (= "no generated_dir; pass --generated-dir to gherkin-mutator"
             (:error (worker/run-job {"id" "j2"}))))
      (finally (run! fs/delete-tree [passing failing empty-dir])))))

(deftest worker-answers-each-line-of-jobs
  (let [dir (generated-dir "true")]
    (try
      (let [request (json/generate-string {"id" "j9" "feature_json" (str (fs/path dir "feature.json")) "generated_dir" dir})
            output (with-out-str (with-in-str (str request "\n\n") (worker/run)))]
        (is (= ["j9"] (map #(get % "id") (map json/parse-string (str/split-lines output))))))
      (finally (fs/delete-tree dir)))))

(deftest scenarios-report-each-execution-to-clojure-test
  (let [counts (binding [t/*report-counters* (ref t/*initial-report-counters*)
                         t/*test-out* (java.io.StringWriter.)]
                 (runtime/run-scenario handlers (json/generate-string feature) 0)
                 (select-keys @t/*report-counters* [:pass :fail]))]
    (is (= {:pass 1 :fail 1} counts)))
  (let [passing {"scenarios" [{"name" "p" "steps" [{"keyword" "Given" "text" "a world"}] "examples" []}]}
        counts (binding [t/*report-counters* (ref t/*initial-report-counters*)
                         t/*test-out* (java.io.StringWriter.)]
                 (runtime/run-scenario handlers (json/generate-string passing) 0)
                 (select-keys @t/*report-counters* [:pass :fail]))]
    (is (= {:pass 1 :fail 0} counts))))

(deftest a-bound-feature-replaces-the-embedded-one
  (is (= {"scenarios" []} (binding [runtime/*feature* {"scenarios" []}] (runtime/feature "{\"scenarios\":[1]}"))))
  (is (= {"scenarios" [1]} (runtime/feature "{\"scenarios\":[1]}"))))

(deftest generated-metadata
  (let [dir (fs/create-temp-dir {:prefix "sfjs-metadata"})
        ir (str (fs/path dir "crap.json"))]
    (try
      (spit ir "{\"scenarios\":[{\"name\":\"one\"},{\"name\":\"two\"}]}")
      (let [entry (generator/generate ir (str (fs/path dir "out")))
            metadata-path (str (fs/path dir "out" "metadata" "swarmforge-tools-javascript-features-crap-feature.json"))
            metadata (json/parse-string (slurp metadata-path))]
        (is (= [1 "swarmforge/tools/javascript/features/crap.feature" ir "generated_files" [entry]]
               ((juxt #(get % "schema_version") #(get % "feature_path") #(get % "ir_path")
                      #(get % "hash_scope") #(get % "generated_files")) metadata)))
        (is (re-matches #"sha256:[0-9a-f]{64}" (get metadata "implementation_hash")))
        (is (str/includes? (slurp metadata-path) "\n  \"schema_version\""))
        (is (str/includes? (slurp entry) "(deftest scenario-1\n"))
        (is (str/includes? (slurp entry) "(deftest scenario-2\n")))
      (finally (fs/delete-tree dir)))))

(deftest the-parser-is-the-one-on-path-else-the-executable-wrapper
  (let [dir (fs/create-temp-dir {:prefix "sfjs-parser"})
        wrapper (fs/path dir "gherkin-parser")]
    (try
      (is (= "/usr/bin/gherkin-parser" (pipeline/parser-command (fs/path "/usr/bin/gherkin-parser") #(throw (ex-info "not asked" {})))))
      (is (thrown-with-msg? Exception #"gherkin-parser not found" (pipeline/parser-command nil (constantly wrapper))))
      (spit (str wrapper) "#!/bin/sh\n")
      (fs/set-posix-file-permissions wrapper "rwxr-xr-x")
      (is (= (str wrapper) (pipeline/parser-command nil (constantly wrapper))))
      (finally (fs/delete-tree dir)))))

(deftest features-are-the-named-ones-or-all-of-them
  (let [dir (fs/create-temp-dir {:prefix "sfjs-features"})]
    (try
      (run! #(spit (str (fs/path dir %)) "") ["b.feature" "a.feature" "notes.txt"])
      (with-redefs [pipeline/features-dir (str dir)]
        (is (= [(str (fs/path dir "a.feature")) (str (fs/path dir "b.feature"))] (pipeline/feature-files nil)))
        (is (= ["c.feature"] (pipeline/feature-files ["c.feature"]))))
      (finally (fs/delete-tree dir)))))

(deftest the-pipeline-exits-nonzero-on-any-failure-or-error
  (is (= 0 (pipeline/exit-status {:fail 0 :error 0})))
  (is (= 1 (pipeline/exit-status {:fail 1 :error 0})))
  (is (= 1 (pipeline/exit-status {:fail 0 :error 1})))
  (is (= 1 (pipeline/exit-status {:fail 1 :error 1}))))

(deftest worker-jobs-count-failures-and-errors-together
  (let [dir (generated-dir "true")]
    (try
      (spit (str (fs/path dir "second.clj"))
            (str "(ns sfjs.generated.second-" (str/replace (str (fs/file-name dir)) #"\W" "-") "\n"
                 "  (:require [clojure.test :refer [deftest is]]))\n"
                 "(deftest broken (is false))\n"
                 "(deftest raises (is (throw (ex-info \"boom\" {}))))\n"))
      (is (= "test_failure" (:outcome (job dir))))
      (is (< 0 (:duration (job dir)) (* 30 1000000000)))
      (is (< 0 (:duration (worker/run-job {"id" "j3"})) (* 30 1000000000)))
      (finally (fs/delete-tree dir)))))
