(ns sfjs.acceptance-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfjs.acceptance.generator :as generator]
            [sfjs.acceptance.runtime :as runtime]))

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
