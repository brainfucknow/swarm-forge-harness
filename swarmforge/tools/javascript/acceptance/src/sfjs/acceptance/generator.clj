(ns sfjs.acceptance.generator
  "acceptance-entrypoint-generator <json-ir> <generated-test-output-dir>:
  writes one clojure.test file per feature that embeds the IR and runs each
  scenario through the runtime and the sf-js step handlers, plus its
  metadata. The feature path is swarmforge/tools/javascript/features/<IR
  stem>.feature unless SFJS_FEATURE_PATH names it."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def features-dir "swarmforge/tools/javascript/features")

(defn slug
  "The APS metadata name: lowercase, each run of other characters one hyphen."
  [path]
  (-> (str/lower-case path)
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-+|-+$" "")))

(defn feature-path [ir-path]
  (or (System/getenv "SFJS_FEATURE_PATH")
      (str features-dir "/" (str/replace (fs/file-name ir-path) #"\.json$" "") ".feature")))

(defn entry-point
  "The generated test source for one feature's IR."
  [feature-path ir-json]
  (let [feature (json/parse-string ir-json)
        ns-name (str "sfjs.generated." (slug feature-path))]
    (str ";; Generated from " feature-path " by sfjs.acceptance.generator. Do not edit.\n"
         "(ns " ns-name "\n"
         "  (:require [clojure.test :refer [deftest]]\n"
         "            [sfjs.acceptance.runtime :as runtime]\n"
         "            [sfjs.acceptance.steps :as steps]))\n\n"
         "(def ir-json " (pr-str ir-json) ")\n"
         (str/join (for [[i scenario] (map-indexed vector (get feature "scenarios"))]
                     (str "\n;; " (get scenario "name") "\n"
                          "(deftest scenario-" (inc i) "\n"
                          "  (runtime/run-scenario steps/handlers ir-json " i "))\n"))))))

(defn- sha256 [text]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes ^String text "UTF-8"))]
    (str "sha256:" (str/join (map #(format "%02x" %) digest)))))

(defn generate
  "Writes the entry point and its metadata; returns the entry point's path."
  [ir-path out-dir]
  (let [feature (feature-path ir-path)
        name (slug feature)
        test-file (str (fs/path out-dir (str name ".clj")))
        source (entry-point feature (slurp ir-path))]
    (fs/create-dirs (fs/path out-dir "metadata"))
    (spit test-file source)
    (spit (str (fs/path out-dir "metadata" (str name ".json")))
          (json/generate-string (array-map :schema_version 1
                                           :feature_path feature
                                           :ir_path (str ir-path)
                                           :implementation_hash (sha256 source)
                                           :hash_scope "generated_files"
                                           :generated_files [test-file])
                                {:pretty true}))
    test-file))

(defn -main [& args]
  (System/exit
   (if (not= 2 (count args))
     (do (binding [*out* *err*] (println "usage: acceptance-entrypoint-generator <json-ir> <generated-test-output>")) 2)
     (try
       (apply generate args)
       0
       (catch Exception e
         (binding [*out* *err*] (println (ex-message e)))
         1)))))
