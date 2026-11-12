(ns sfcs.coverage
  "What `coverage` asks MSBuild about each project, and the dotnet-coverage
  settings that keep the project's sources and leave its test assemblies out."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private test-project-properties ["IsTestProject" "IsTestingPlatformApplication"])

(defn evaluate-args
  "The `dotnet` arguments that evaluate `project`'s test-project properties and AssemblyName."
  [project]
  (into ["msbuild" project] (map #(str "-getProperty:" %) (conj test-project-properties "AssemblyName"))))

(defn- evaluated-properties
  "The Properties map of a captured `dotnet msbuild -getProperty` evaluation, or nil."
  [{:keys [exit out]}]
  (let [parsed (when (zero? exit) (try (json/parse-string out) (catch Exception _ nil)))
        properties (when (map? parsed) (get parsed "Properties"))]
    (when (map? properties) properties)))

(defn test-project?
  "Whether a captured evaluation reads \"true\" for either test-project property;
  a failed or unreadable evaluation is not one."
  [evaluation]
  (let [properties (evaluated-properties evaluation)]
    (boolean (some #(= "true" (some-> (get properties %) str str/trim str/lower-case))
                   test-project-properties))))

(defn test-assembly
  "The AssemblyName of a captured evaluation of a test project, or nil."
  [evaluation]
  (when (test-project? evaluation)
    (some-> (get (evaluated-properties evaluation) "AssemblyName") str str/trim not-empty)))

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

(defn- elements [tag values]
  (apply str (map #(str "\n        <" tag ">" (xml-text %) "</" tag ">") values)))

(defn- module-pattern
  "A .NET regex for the managed module, dll or exe, named `assembly` under any directory."
  [assembly]
  (str ".*" separator (escape-segment assembly) "\\.(dll|exe)$"))

(defn- module-excludes [test-assemblies]
  (when (seq test-assemblies)
    (str "\n    <ModulePaths>\n      <Exclude>"
         (elements "ModulePath" (map module-pattern test-assemblies))
         "\n      </Exclude>\n    </ModulePaths>")))

(defn settings-xml
  "dotnet-coverage settings: sources under `root` outside obj/, and no module of the `test-assemblies`."
  [root test-assemblies]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
       "<Configuration>\n  <CodeCoverage>"
       (module-excludes test-assemblies)
       "\n    <Sources>\n      <Include>"
       (elements "Source" [(dir-pattern root)])
       "\n      </Include>\n      <Exclude>"
       (elements "Source" [(str ".*" separator "obj" separator ".*")])
       "\n      </Exclude>\n    </Sources>\n  </CodeCoverage>\n</Configuration>\n"))

(defn run-passed?
  "Whether a wrapped `dotnet test` status passes: 8 is MTP's zero tests ran."
  [status]
  (contains? #{0 8} status))
