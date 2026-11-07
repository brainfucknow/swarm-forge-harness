(ns sfjava.acceptance
  "Java contracts use the existing parser, generator and runtime with Java CLI steps."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [sfjs.acceptance.runtime :as runtime]
            [sfjs.acceptance.pipeline :as pipeline]
            [sfjs.acceptance.worker :as worker]))

(def java-dir (str (fs/canonicalize (fs/path (fs/parent *file*) ".." ".." ".."))))
(def generated-dir (str (fs/path java-dir ".." ".." ".." "tmp" "sf-java" "acceptance")))

(defn feature-paths [named]
  (or (seq named)
      (sort (map #(str (fs/relativize (fs/cwd) %)) (fs/glob (fs/path java-dir "features") "*.feature")))))

(defn run [& named]
  (fs/delete-tree (fs/path generated-dir "generated"))
  (let [features (feature-paths named)]
    (doseq [feature features]
      (pipeline/prepare (pipeline/gherkin-parser) feature generated-dir
                        {:feature-path (str feature) :steps-ns "sfjava.steps" :namespace-prefix "sfjava.generated."}))
    (System/exit (apply (requiring-resolve 'speclj.cli/run) ["-c" (str (fs/path generated-dir "generated"))]))))

(defn scenario-tag [generated-dir]
  (when runtime/*feature*
    (try
      (let [metadata (first (fs/glob (fs/path generated-dir "metadata") "*.json"))
            original (-> metadata str slurp (json/parse-string true) :ir_path slurp json/parse-string)]
        (when-let [index (runtime/changed-scenario original runtime/*feature*)]
          (str "scenario-" index)))
      (catch Exception _ nil))))

(defn execute [generated-dir]
  (let [args (concat ["-c"] (when-let [tag (scenario-tag generated-dir)] ["--tag" tag]) [generated-dir])]
    {:fail (apply (requiring-resolve 'speclj.cli/run) args) :error 0}))

(defn run-worker []
  (binding [worker/*execute* execute]
    (worker/run)))
