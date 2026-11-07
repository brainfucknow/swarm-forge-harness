(ns sfjava.acceptance
  "Java contracts use the existing parser, generator and runtime with Java CLI steps."
  (:require [babashka.fs :as fs]
            [sfjs.acceptance.pipeline :as pipeline]))

(def java-dir (str (fs/canonicalize (fs/path (fs/parent *file*) ".." ".." ".."))))
(def generated-dir (str (fs/path java-dir ".." ".." ".." "tmp" "sf-java" "acceptance")))

(defn run [& named]
  (fs/delete-tree (fs/path generated-dir "generated"))
  (let [features (or (seq named) (sort (map str (fs/glob (fs/path java-dir "features") "*.feature"))))]
    (doseq [feature features]
      (pipeline/prepare (pipeline/gherkin-parser) feature generated-dir
                        {:feature-path (str feature) :steps-ns "sfjava.steps" :namespace-prefix "sfjava.generated."}))
    (System/exit (apply (requiring-resolve 'speclj.cli/run) ["-c" (str (fs/path generated-dir "generated"))]))))
