(ns sfjs.acceptance.worker
  "The gherkin-mutator runner adapter: newline-delimited JSON jobs on stdin,
  one response per job on stdout. Each job runs the generated entry points
  in its generated_dir against its mutated feature_json."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :as t]
            [sfjs.acceptance.pipeline :as pipeline]
            [sfjs.acceptance.runtime :as runtime]))

(def ^:private loaded (atom {}))

(defn- entry-points [generated-dir]
  (or (get @loaded generated-dir)
      (let [files (sort (map str (fs/glob generated-dir "*.clj")))]
        (when (empty? files)
          (throw (ex-info (str "no generated entry points in " generated-dir) {})))
        (let [namespaces (mapv (fn [file] (load-file file) (pipeline/generated-namespace file)) files)]
          (swap! loaded assoc generated-dir namespaces)
          namespaces))))

(defn run-job [{:strs [id feature_json generated_dir]}]
  (let [started (System/nanoTime)
        output (java.io.StringWriter.)]
    (try
      (when-not generated_dir
        (throw (ex-info "no generated_dir; pass --generated-dir to gherkin-mutator" {})))
      (let [namespaces (entry-points generated_dir)
            {:keys [fail error]} (binding [*out* output
                                           t/*test-out* output
                                           runtime/*feature* (json/parse-string (slurp feature_json))]
                                   (apply t/run-tests namespaces))]
        {:id id :outcome (if (zero? (+ fail error)) "test_success" "test_failure")
         :output (str output) :error "" :duration (- (System/nanoTime) started)})
      (catch Exception e
        {:id id :outcome "infrastructure_error" :output (str output) :error (ex-message e)
         :duration (- (System/nanoTime) started)}))))

(defn run []
  (doseq [line (line-seq (java.io.BufferedReader. *in*))
          :when (seq line)]
    (println (json/generate-string (run-job (json/parse-string line))))
    (flush)))
