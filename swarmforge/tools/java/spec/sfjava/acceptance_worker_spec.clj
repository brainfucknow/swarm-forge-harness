(ns sfjava.acceptance-worker-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [cheshire.core :as json]
            [sfjs.acceptance.generator :as generator]
            [sfjava.acceptance :as acceptance]
            [sfjs.acceptance.worker :as worker]
            [sfjs.acceptance.runtime :as runtime]))

(describe "Speclj acceptance mutation worker"
  (it "preserves explicit feature paths and uses stable relative defaults"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "feature-selection"}))]
      (try
        (fs/create-dirs (fs/path dir "features"))
        (doseq [name ["quality.feature" "execution.feature" "installation.feature"]]
          (spit (str (fs/path dir "features" name)) "Feature: probe"))
        (with-redefs [acceptance/java-dir (str (fs/absolutize dir))]
          (should= ["chosen.feature"] (acceptance/feature-paths ["chosen.feature"]))
          (let [paths (acceptance/feature-paths [])]
            (should= ["execution.feature" "installation.feature" "quality.feature"] (map #(str (fs/file-name %)) paths))
            (should (every? #(not (fs/absolute? %)) paths))))
        (finally (fs/delete-tree dir)))))
  (it "runs generated specs against each job's replacement IR"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "speclj-worker"}))
          feature (str (fs/path dir "feature.json"))]
      (try
        (spit (str (fs/path dir "entry.clj"))
              (str "(ns generated.mutation-worker (:require [speclj.core :refer :all] [sfjs.acceptance.runtime :as runtime]))"
                   "(describe \"mutation probe\" (it \"observes the supplied IR\" (should= true (get (runtime/feature \"{}\") \"passing\"))))"))
        (binding [worker/*execute* acceptance/execute]
          (spit feature "{\"passing\":true}")
          (should= "test_success" (:outcome (worker/run-job {"id" "first" "feature_json" feature "generated_dir" dir})))
          (spit feature "{\"passing\":false}")
          (should= "test_failure" (:outcome (worker/run-job {"id" "second" "feature_json" feature "generated_dir" dir})))
          (spit feature "{\"passing\":true}")
          (should= "test_success" (:outcome (worker/run-job {"id" "third" "feature_json" feature "generated_dir" dir}))))
        (finally (fs/delete-tree dir))))))

(def events (atom []))
(def handlers
  {:fresh (constantly {}) :cleanup (constantly nil)
   :steps [[#"value (.+)" (fn [world value]
                            (swap! events conj value)
                            (when-not (= "pass" value) (throw (ex-info "unexpected value" {})))
                            world)]]})

(describe "Configured Speclj acceptance generation"
  (it "runs every example and propagates failing steps using generated metadata"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "generated-speclj"}))
          ir (str (fs/path dir "input.json"))
          feature {"scenarios" [{"name" "one" "steps" [{"keyword" "Then" "text" "value <value>" "parameters" ["value"]}]
                                 "examples" [{"value" "pass"} {"value" "fail"}]}]}]
      (try
        (spit ir (json/generate-string feature))
        (reset! events [])
        (let [file (generator/generate ir dir {:feature-path "probe.feature" :namespace-prefix "generated."
                                              :steps-ns "sfjava.acceptance-worker-spec"})
              metadata (json/parse-string (slurp (str (fs/path dir "metadata/probe-feature.json"))) true)]
          (should= [file] (:generated_files metadata))
          (binding [*out* (java.io.StringWriter.)] (should= 1 (:fail (acceptance/execute dir))))
          (should= ["pass" "fail"] @events))
        (finally (fs/delete-tree dir))))))

(describe "Mutation scenario selection"
  (it "selects only an isolated scenario change and falls back for ambiguity"
    (let [original {"name" "probe" "scenarios" [{"name" "first"} {"name" "second"}]}]
      (should= 0 (runtime/changed-scenario original (assoc-in original ["scenarios" 0 "name"] "changed")))
      (should= 1 (runtime/changed-scenario original (assoc-in original ["scenarios" 1 "name"] "changed")))
      (should-be-nil (runtime/changed-scenario original original))
      (should-be-nil (runtime/changed-scenario original {"name" "probe" "scenarios" [{"name" "x"} {"name" "y"}]}))
      (should-be-nil (runtime/changed-scenario original {"name" "other" "scenarios" [{"name" "x"} {"name" "second"}]}))
      (should-be-nil (runtime/changed-scenario original {"name" "probe" "scenarios" [{"name" "x"}]}))))
  (it "runs the changed generated scenario and retains full fallback without metadata"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "selected-scenario"}))
          ir (str (fs/path dir "input.json"))
          scenario (fn [name] {"name" name "steps" [{"keyword" "Then" "text" "value <value>" "parameters" ["value"]}]
                              "examples" [{"value" "pass"}]})
          original {"scenarios" [(scenario "first") (scenario "second")]}]
      (try
        (spit ir (json/generate-string original))
        (generator/generate ir dir {:feature-path "selected.feature" :namespace-prefix "generated."
                                   :steps-ns "sfjava.acceptance-worker-spec"})
        (doseq [index [0 1]]
          (reset! events [])
          (binding [*out* (java.io.StringWriter.) runtime/*feature* (assoc-in original ["scenarios" index "examples" 0 "value"] "fail")]
            (should= 1 (:fail (acceptance/execute dir)))
            (should= ["fail"] @events)))
        (fs/delete-tree (fs/path dir "metadata"))
        (reset! events [])
        (binding [*out* (java.io.StringWriter.) runtime/*feature* (assoc-in original ["scenarios" 1 "examples" 0 "value"] "fail")]
          (should= 1 (:fail (acceptance/execute dir)))
          (should= ["pass" "fail"] @events))
        (finally (fs/delete-tree dir))))))
