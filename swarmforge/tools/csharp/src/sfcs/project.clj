(ns sfcs.project
  "What a .NET project file says about testing, read as MSBuild XML: comments
  ignored, entities decoded, element names matched without their namespace and
  case."
  (:require [clojure.data.xml :as xml]
            [clojure.string :as str]))

(def property-list-element "SwarmForgePropertyTestList")

(defn- parse-elements
  "Every element of the project, or nil when `text` is not well-formed XML."
  [text]
  (try
    (->> (xml/parse-str (str/replace text #"^\uFEFF" ""))
         (tree-seq map? :content)
         (filter map?)
         doall)
    (catch Exception _ nil)))

(defn- named? [element-name element]
  (.equalsIgnoreCase ^String element-name (name (:tag element))))

(defn- elements-named [elements element-name]
  (filter (partial named? element-name) elements))

(defn- text [element]
  (str/trim (apply str (filter string? (:content element)))))

(defn- references-package? [elements package]
  (some #(.equalsIgnoreCase ^String package (str/trim (get-in % [:attrs :Include] "")))
        (elements-named elements "PackageReference")))

(defn- declared-property-list [elements]
  (some-> (first (elements-named elements property-list-element)) text not-empty))

(defn test-project
  "{:expecto? :property-list} for a test project's `project-text`, nil for any
  other project. Only an F# project (`fsharp?`) is an Expecto one; an
  unreadable project is no test project."
  [project-text fsharp?]
  (when-let [elements (parse-elements project-text)]
    (let [expecto? (boolean (and fsharp? (references-package? elements "YoloDev.Expecto.TestSdk")))]
      (when (or expecto?
                (references-package? elements "Microsoft.NET.Test.Sdk")
                (some #(.equalsIgnoreCase "true" (text %)) (elements-named elements "IsTestProject")))
        {:expecto? expecto?
         :property-list (when expecto? (declared-property-list elements))}))))
