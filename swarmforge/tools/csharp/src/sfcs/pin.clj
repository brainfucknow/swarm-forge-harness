(ns sfcs.pin
  "Where the project pins a local dotnet tool, read from
  `dotnet tool list --local <package> --format json`, and why sf-cs refuses a
  pin. Only a committed manifest at the repository root counts: it is the one
  `<wrapper> tools` restores from there."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(def stryker
  "The pin `mutate` needs; `probe` is the dotnet command that must start."
  {:package "dotnet-stryker" :probe ["stryker" "--help"] :restored "Stryker"})

(def dotnet-coverage
  "The pin `coverage` needs."
  {:package "dotnet-coverage" :probe ["dotnet-coverage" "--version"] :restored "dotnet-coverage"})

(defn list-args [{:keys [package]}]
  ["tool" "list" "--local" package "--format" "json"])

(def root-manifests
  "Manifest paths, relative to the repository root, that hold the project's pin."
  #{"dotnet-tools.json" ".config/dotnet-tools.json"})

(defn- parse-json [text]
  (try (json/parse-string text) (catch Exception _ nil)))

(defn listed-manifest
  "The manifest path list names for the tool's package when it exited 0, else nil."
  [{:keys [package]} {:keys [exit out]}]
  (when (zero? exit)
    (let [parsed (parse-json out)
          entries (when (map? parsed) (get parsed "data"))]
      (when (sequential? entries)
        (some (fn [entry]
                (when (and (map? entry) (= package (get entry "packageId")))
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

(defn- pin-instructions [{:keys [package]} root]
  (str "in " root ": dotnet new tool-manifest (when the root has none), then dotnet tool install " package
       " --version <version>, then commit the manifest"))

(defn- pin-at-root [tool root]
  (str "a committed tool manifest at the repository root (" (pin-instructions tool root) ")"))

(defn not-found [{:keys [package] :as tool} root exit]
  (str "dotnet " (str/join " " (take 3 (list-args tool))) " " package " found no " package " (exit " exit
       "); ask the operator to pin one in " (pin-at-root tool root)))

(defn outside [{:keys [package] :as tool} root manifest]
  (str package " is pinned in " manifest ", outside this repository; ask the operator to pin it in "
       (pin-at-root tool root)))

(defn below-root [{:keys [package] :as tool} wrapper root manifest]
  (str package " is pinned in " manifest ", not at the repository root, so " wrapper " tools does not restore it;"
       " ask the operator to pin it in " (pin-at-root tool root) " and remove this pin"))

(defn not-committed [{:keys [package]} manifest]
  (str package " is pinned in " manifest ", which is not committed; ask the operator to commit or remove it"))

(defn not-runnable [{:keys [probe restored]} wrapper exit]
  (str "dotnet " (str/join " " probe) " exited " exit "; run " wrapper " tools to restore the pinned " restored))
