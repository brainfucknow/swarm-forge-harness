(ns acceptance.runner
  "Runner adapter for gherkin-mutator: a persistent worker. Reads one JSON job
  per line from stdin, runs the generated acceptance tests in the job's
  generated_dir against the job's feature_json, and writes one JSON response per
  line to stdout. Run it with: bb --config test/acceptance/bb.edn -m acceptance.runner"
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private config
  (str (fs/canonicalize (fs/path (fs/parent *file*) ".." ".." "bb.edn"))))

(defn- run-generated
  "The outcome of running every generated test against ir, as {:outcome :output :error}."
  [generated-dir ir]
  (let [tests (sort (map str (fs/glob generated-dir "*_acceptance.clj")))]
    (if (empty? tests)
      {:outcome "infrastructure_error" :output "" :error (str "no generated tests in " generated-dir)}
      (let [runs (for [test tests]
                   @(p/process {:cmd ["bb" "--config" config test] :out :string :err :string
                                :extra-env {"ACCEPTANCE_IR" (str (fs/absolutize ir))}}))
            output (str/join "\n" (map :out runs))]
        (cond
          (every? (comp zero? :exit) runs) {:outcome "test_success" :output output :error ""}
          (some (comp #{1} :exit) runs) {:outcome "test_failure" :output output :error ""}
          :else {:outcome "infrastructure_error" :output output
                 :error (str/join "\n" (map :err runs))})))))

(defn respond
  "The response to one job request."
  [{:strs [id feature_json generated_dir]}]
  (let [start (System/nanoTime)
        result (try (run-generated generated_dir feature_json)
                    (catch Exception e {:outcome "infrastructure_error" :output "" :error (ex-message e)}))]
    {:id id :outcome (:outcome result) :output (:output result) :error (:error result)
     :duration (- (System/nanoTime) start)}))

(defn -main [& _]
  (doseq [line (line-seq (java.io.BufferedReader. *in*))
          :when (not (str/blank? line))]
    (println (json/generate-string (respond (json/parse-string line))))
    (flush)))
