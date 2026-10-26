(ns sfjs.acceptance.pipeline
  "The normal acceptance run: gherkin-parser, the entrypoint generator, and
  the generated tests, for every sf-js feature or the ones named."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :as t]
            [sfjs.acceptance.generator :as generator]
            [sfjs.acceptance.world :as world]))

(def features-dir (str (fs/path world/javascript-dir "features")))
(def work-dir (str (fs/normalize (fs/path world/swarmforge-dir ".." "tmp" "sf-js" "acceptance"))))

(defn- git-out [& args]
  (str/trim (:out (apply p/shell {:dir world/javascript-dir :out :string} "git" args))))

(defn parser-command
  "The parser found on PATH, else the wrapper `wrapper-path` returns when it
  is executable."
  [on-path wrapper-path]
  (or (some-> on-path str)
      (let [wrapper (wrapper-path)]
        (when (fs/executable? wrapper) (str wrapper)))
      (throw (ex-info "gherkin-parser not found; run: swarmforge/bin/sf tool ensure gherkin-parser" {}))))

(defn gherkin-parser
  "gherkin-parser from PATH, else the wrapper `sf tool ensure gherkin-parser`
  writes at the repository root."
  []
  (parser-command (fs/which "gherkin-parser")
                  #(fs/path (git-out "rev-parse" "--path-format=absolute" "--git-common-dir")
                            ".." ".swarmforge" "bin" "gherkin-parser")))

(defn- stem [feature]
  (str/replace (fs/file-name feature) #"\.feature$" ""))

(defn prepare
  "Parses one feature and generates its entry point under
  tmp/sf-js/acceptance/generated/<stem>/; returns the entry point."
  [parser feature]
  (let [ir (str (fs/path work-dir "ir" (str (stem feature) ".json")))]
    (fs/create-dirs (fs/parent ir))
    (p/shell {:out :string} parser (str feature) ir)
    (generator/generate ir (str (fs/path work-dir "generated" (stem feature))))))

(defn generated-namespace [entry-point]
  (symbol (second (re-find #"\(ns (\S+)" (slurp entry-point)))))

(defn feature-files
  "The named features, or every feature in sorted order."
  [named]
  (or (seq named) (sort (map str (fs/glob features-dir "*.feature")))))

(defn run-features
  "Prepares and runs the named features, or every feature; returns the
  clojure.test summary."
  [& features]
  (let [parser (gherkin-parser)
        features (feature-files features)
        namespaces (doall (for [feature features
                                :let [entry-point (prepare parser feature)]]
                            (do (load-file entry-point) (generated-namespace entry-point))))]
    (apply t/run-tests namespaces)))

(defn exit-status [{:keys [fail error]}]
  (if (zero? (+ fail error)) 0 1))

(defn run [& features]
  (System/exit (exit-status (apply run-features features))))
