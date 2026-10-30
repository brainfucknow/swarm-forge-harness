(ns sfcs.cli
  "Shared .NET tool wrapper for SwarmForge roles."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.command :as command]
            [sfcore.crap :as crap]
            [sfcore.git :as git]
            [sfcore.jscpd :as jscpd]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]
            [sfcore.stryker :as stryker]
            [sfcs.cobertura :as cob]))

(def tmp "tmp")
(def coverage-dir (str (fs/path tmp "coverage")))
(def jscpd-dir (str (fs/path tmp "jscpd")))
(def stryker-dir (str (fs/path tmp "stryker")))
(def property-filter "Category!=Property")

(def languages
  {:csharp {:name "sf-cs" :extension ".cs" :format "csharp" :mutation? true :signature? true}
   :fsharp {:name "sf-fs" :extension ".fs" :format "fsharp" :mutation? false :signature? false}})
(def ^:dynamic *language* (:csharp languages))

(defn- usage []
  (str "usage: " (:name *language*) " <command> [options]

  tools                       " (if (:mutation? *language*) "install dotnet-stryker locally and check node, npx, dotnet" "check dotnet, node, npx and prepare jscpd") "
  test [dotnet test args]     unit and acceptance tests (Category!=Property)
  property                    property tests only (Category=Property)
  coverage [dotnet test args] tests with coverlet; cobertura files under " coverage-dir "/
  crap [--all] [--base REF]   CRAP per changed method from " coverage-dir "/; exit 1 over " crap/crap-limit "
  dry [--all] [--base REF] [--min-tokens N]
                              jscpd clones touching changed lines; exit 1 if any
  mutate [--base REF] [--all] [--report-only] [-- stryker args]
                              " (if (:mutation? *language*) "Stryker on changed files (--since); exit 1 on survivors" "unsupported for F#; exit 2") "

Every command runs from the task worktree. --base defaults to SWARMFORGE_BASE, main, or master."))

(def option-spec
  {:coerce {:all :boolean :report-only :boolean :help :boolean :min-tokens :long
            :base :string :report :string}
   :alias {:h :help}})

(defn- changed-for [opts]
  (git/changed-since-base opts [(str "*" (:extension *language*))]))

(defn- exists? [path]
  (fs/exists? (shell/in-dir path)))

(defn- read-json [path]
  (json/parse-string (slurp (shell/in-dir path))))

(defn- require-tool-run [command args]
  (let [status (shell/run command args)]
    (when-not (zero? status)
      (throw (outcome/usage-error (str command " " (str/join " " args) " exited " status))))))

(defn- cmd-tools [_]
  (let [missing (remove shell/available? ["dotnet" "node" "npx"])]
    (when (seq missing)
      (throw (outcome/usage-error (str "missing on PATH: " (str/join ", " missing))))))
  (when (:mutation? *language*)
    (let [manifest (str (fs/path ".config" "dotnet-tools.json"))]
      (when-not (exists? manifest)
        (require-tool-run "dotnet" ["new" "tool-manifest"]))
      (when-not (get-in (read-json manifest) ["tools" "dotnet-stryker"])
        (require-tool-run "dotnet" ["tool" "install" "dotnet-stryker"]))
      (require-tool-run "dotnet" ["tool" "restore"])))
  (shell/run "npx" ["--yes" "jscpd@4" "--version"]))

(defn- cmd-test [{:keys [args]}]
  (shell/run "dotnet" (into ["test" "--filter" property-filter] args)))

(defn- cmd-property [{:keys [args]}]
  (shell/run "dotnet" (into ["test" "--filter" "Category=Property"] args)))

(defn- cmd-coverage [{:keys [args]}]
  (fs/create-dirs (shell/in-dir coverage-dir))
  (doseq [stale (cob/find-cobertura-files (shell/in-dir coverage-dir))]
    (fs/delete-tree (fs/parent stale)))
  (let [status (shell/run "dotnet" (-> ["test" "--filter" property-filter
                                        "--collect" "XPlat Code Coverage"
                                        "--results-directory" coverage-dir]
                                       (into args)
                                       (conj "--" "DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura")))]
    (if-not (zero? status)
      status
      (let [files (cob/find-cobertura-files (shell/in-dir coverage-dir))]
        (when (empty? files)
          (throw (ex-info (str "no coverage.cobertura.xml under " coverage-dir
                               "; is coverlet.collector referenced by the test projects?") {})))
        (println (count files) "coverage file(s) under" coverage-dir)
        outcome/exit-ok))))

(defn- merge-methods
  "Keeps the best-covered entry when several test runs cover the same method."
  [files]
  (->> files
       (mapcat #(cob/parse-cobertura (slurp %) (when (= ".fs" (:extension *language*)) {:extension ".fs" :strict? true})))
       (reduce (fn [best method]
                 (let [key ((juxt :file :class-name :name :signature) method)
                       seen (get best key)]
                   (if (or (nil? seen) (> (:coverage method) (:coverage seen)))
                     (assoc best key method)
                     best)))
               {})
       vals))

(defn- source-lines [root file]
  (let [full (str (fs/path root file))]
    (when (fs/exists? full)
      (str/split (slurp full) #"\r?\n" -1))))

(defn- select-method [root changed read-source method]
  (let [reported (:file method)
        file (if changed
               (changes/to-changed-path reported changed root)
               (if (fs/absolute? reported)
                 (str (fs/relativize root reported))
                 reported))]
    (when file
      (let [source (when (:signature? *language*) (read-source file))
            signature-line (if source
                             (cob/signature-start source (:first-line method))
                             (:first-line method))]
        (when (or (nil? changed) (changes/overlaps? file signature-line (:last-line method) changed))
          (assoc method :file file :signature-line signature-line))))))

(defn- cmd-crap [{:keys [opts]}]
  (let [dir (or (:report opts) coverage-dir)
        files (cob/find-cobertura-files (shell/in-dir dir))]
    (when (empty? files)
      (throw (outcome/usage-error (str "no coverage.cobertura.xml under " dir "; run " (:name *language*) " coverage first"))))
    (let [root (git/repo-root)
          changed (changed-for opts)
          read-source (memoize (partial source-lines root))
          methods (merge-methods files)
          _ (when (and (= ".fs" (:extension *language*)) (empty? methods))
              (throw (outcome/usage-error "no usable compiled .fs method data in coverage report")))
          selected (->> methods
                        (keep (partial select-method root changed read-source))
                        (sort-by (juxt (comp - :crap) :file :first-line)))]
      (shell/emit (crap/review selected cob/format-method "method" changed)))))

(defn- cmd-dry [{:keys [opts]}]
  (let [report-path (str (fs/path jscpd-dir "jscpd-report.json"))
        status (if (:report-only opts)
                 outcome/exit-ok
                 (do (fs/create-dirs (shell/in-dir jscpd-dir))
                     (fs/delete-if-exists (shell/in-dir report-path))
                     (shell/run "npx" ["--yes" "jscpd@4" "--format" (:format *language*)
                                       "--min-tokens" (str (or (:min-tokens opts) 50))
                                       "--reporters" "json" "--output" jscpd-dir
                                       "--ignore" "**/bin/**,**/obj/**,**/*.g.cs,**/*.Designer.cs,**/Migrations/**,**/tmp/**"
                                       "."])))]
    (cond
      (and (not (zero? status)) (not (exists? report-path))) status
      (not (exists? report-path)) (throw (outcome/usage-error (str report-path " not found")))
      :else (let [clones (jscpd/parse-clones (read-json report-path))
                  changed (changed-for opts)]
              (shell/emit (jscpd/review clones changed (when changed (git/repo-root))))))))

(defn- cmd-mutate [{:keys [opts extra]}]
  (when-not (:mutation? *language*)
    (throw (outcome/usage-error "F# mutation is unsupported; Stryker.NET supports C# only")))
  (let [report-path (str (fs/path stryker-dir "reports" "mutation-report.json"))
        status (if (:report-only opts)
                 outcome/exit-ok
                 (do (fs/create-dirs (shell/in-dir stryker-dir))
                     (fs/delete-if-exists (shell/in-dir report-path))
                     (shell/run "dotnet"
                                (into (cond-> ["stryker" "--reporter" "json" "--reporter" "progress" "--output" stryker-dir]
                                        (not (:all opts)) (conj (str "--since:" (git/base-ref opts))))
                                      extra))))]
    (cond
      (and (not (zero? status)) (not (exists? report-path))) status
      (not (exists? report-path)) (throw (outcome/usage-error (str report-path " not found")))
      :else (shell/emit (stryker/review (stryker/parse-mutants (read-json report-path)))))))

(def ^:private commands
  {"tools" cmd-tools "test" cmd-test "property" cmd-property "coverage" cmd-coverage
   "crap" cmd-crap "dry" cmd-dry "mutate" cmd-mutate})

(defn main
  ([argv]
   (command/main {:usage (usage) :commands commands :option-spec option-spec :help-option? true} argv))
  ([language argv]
   (binding [*language* (get languages language)]
     (main argv))))

(defn -main [& argv]
  (shell/run-main "sf-cs" main argv))
