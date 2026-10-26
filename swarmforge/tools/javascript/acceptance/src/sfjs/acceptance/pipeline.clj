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

(defn gherkin-parser
  "gherkin-parser from PATH, else the wrapper `sf tool ensure gherkin-parser`
  writes at the repository root."
  []
  (let [common (git-out "rev-parse" "--path-format=absolute" "--git-common-dir")
        wrapper (fs/path common ".." ".swarmforge" "bin" "gherkin-parser")]
    (or (some-> (fs/which "gherkin-parser") str)
        (when (fs/executable? wrapper) (str wrapper))
        (throw (ex-info "gherkin-parser not found; run: swarmforge/bin/sf tool ensure gherkin-parser" {})))))

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

(defn run-features
  "Prepares and runs the named features, or every feature; returns the
  clojure.test summary."
  [& features]
  (let [parser (gherkin-parser)
        features (or (seq features) (sort (map str (fs/glob features-dir "*.feature"))))
        namespaces (doall (for [feature features
                                :let [entry-point (prepare parser feature)]]
                            (do (load-file entry-point) (generated-namespace entry-point))))]
    (apply t/run-tests namespaces)))

(defn run [& features]
  (let [{:keys [fail error]} (apply run-features features)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
