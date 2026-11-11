(ns sfcs.coverage
  "What `coverage` asks MSBuild about each project, and the dotnet-coverage
  settings that keep only the project's own non-test sources in the report."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private test-project-properties ["IsTestProject" "IsTestingPlatformApplication"])

(defn evaluate-args
  "The `dotnet` arguments that evaluate `project`'s test-project properties."
  [project]
  (into ["msbuild" project] (map #(str "-getProperty:" %) test-project-properties)))

(defn test-project?
  "Whether a captured `dotnet msbuild -getProperty` evaluation reads \"true\" for
  either test-project property; a failed or unreadable evaluation is not one."
  [{:keys [exit out]}]
  (let [parsed (when (zero? exit) (try (json/parse-string out) (catch Exception _ nil)))
        properties (when (map? parsed) (get parsed "Properties"))]
    (boolean (and (map? properties)
                  (some #(= "true" (some-> (get properties %) str str/trim str/lower-case))
                        test-project-properties)))))

(def ^:private separator "[\\\\/]")

(defn- escape-segment
  "`segment` with each .NET regex metacharacter backslash-escaped."
  [segment]
  (str/replace segment #"[\\*+?|{}\[\]()^$.# ]" #(str "\\" %)))

(defn- dir-pattern
  "A .NET regex for every path under the absolute directory `dir`, either separator."
  [dir]
  (str "^" (str/join separator (map escape-segment (str/split dir #"[\\/]"))) separator ".*"))

(defn- xml-text [text]
  (-> text (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))

(defn- source-elements [patterns]
  (apply str (map #(str "\n        <Source>" (xml-text %) "</Source>") patterns)))

(defn settings-xml
  "dotnet-coverage settings: sources under `root`, outside obj/ and the `test-dirs`."
  [root test-dirs]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
       "<Configuration>\n  <CodeCoverage>\n    <Sources>\n      <Include>"
       (source-elements [(dir-pattern root)])
       "\n      </Include>\n      <Exclude>"
       (source-elements (cons (str ".*" separator "obj" separator ".*") (map dir-pattern test-dirs)))
       "\n      </Exclude>\n    </Sources>\n  </CodeCoverage>\n</Configuration>\n"))

(defn run-passed?
  "Whether a wrapped `dotnet test` status passes: 8 is MTP's zero tests ran."
  [status]
  (contains? #{0 8} status))
