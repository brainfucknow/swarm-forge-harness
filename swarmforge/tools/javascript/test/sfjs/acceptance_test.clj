(ns sfjs.acceptance-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfjs.acceptance.generator :as generator]
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
