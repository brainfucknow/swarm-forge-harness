(ns sfcs.pin
  "Where the project pins dotnet-stryker, read from
  `dotnet tool list --local dotnet-stryker --format json`, and why sf-cs mutate
  refuses a pin. Only a committed manifest at the repository root counts: it is
  the one `sf-cs tools` restores from there."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def list-args ["tool" "list" "--local" "dotnet-stryker" "--format" "json"])

(def root-manifests
  "Manifest paths, relative to the repository root, that hold the project's pin."
  #{"dotnet-tools.json" ".config/dotnet-tools.json"})

(defn- parse-json [text]
  (try (json/parse-string text) (catch Exception _ nil)))

(defn listed-manifest
  "The manifest path list names for dotnet-stryker when it exited 0, else nil."
  [{:keys [exit out]}]
  (when (zero? exit)
    (let [parsed (parse-json out)
          entries (when (map? parsed) (get parsed "data"))]
      (when (sequential? entries)
        (some (fn [entry]
                (when (and (map? entry) (= "dotnet-stryker" (get entry "packageId")))
                  (let [manifest (get entry "manifest")]
                    (when (string? manifest) manifest))))
              entries)))))

(defn relative-to-root
  "`manifest` relative to `root` with forward slashes, or nil when it is outside;
  both are real paths."
  [root manifest]
  (let [root-path (java.nio.file.Path/of root (make-array String 0))
        manifest-path (java.nio.file.Path/of manifest (make-array String 0))]
    (when (.startsWith manifest-path root-path)
      (str/replace (str (.relativize root-path manifest-path)) "\\" "/"))))

(defn- pin-instructions [root]
  (str "in " root ": dotnet new tool-manifest (when the root has none), then dotnet tool install dotnet-stryker"
       " --version <version>, then commit the manifest"))

(defn- pin-at-root [root]
  (str "a committed tool manifest at the repository root (" (pin-instructions root) ")"))

(defn not-found [root exit]
  (str "dotnet " (str/join " " (take 4 list-args)) " found no dotnet-stryker (exit " exit "); ask the operator to pin one in "
       (pin-at-root root)))

(defn outside [root manifest]
  (str "dotnet-stryker is pinned in " manifest ", outside this repository; ask the operator to pin it in "
       (pin-at-root root)))

(defn below-root [root manifest]
  (str "dotnet-stryker is pinned in " manifest ", not at the repository root, so sf-cs tools does not restore it;"
       " ask the operator to pin it in " (pin-at-root root) " and remove this pin"))

(defn not-committed [manifest]
  (str "dotnet-stryker is pinned in " manifest ", which is not committed; ask the operator to commit or remove it"))

(defn not-runnable [exit]
  (str "dotnet stryker --help exited " exit "; run sf-cs tools to restore the pinned Stryker"))
