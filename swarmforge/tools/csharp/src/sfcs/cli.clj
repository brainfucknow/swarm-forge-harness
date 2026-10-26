(ns sfcs.cli
  "sf-cs: C# tool wrapper for SwarmForge roles. One subcommand per constitution tool."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.changes :as changes]
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

(def usage
  (str "usage: sf-cs <command> [options]

  tools                       install dotnet-stryker locally and check node, npx, dotnet
  test [dotnet test args]     unit and acceptance tests (Category!=Property)
  property                    property tests only (Category=Property)
  coverage [dotnet test args] tests with coverlet; cobertura files under " coverage-dir "/
  crap [--all] [--base REF]   CRAP per changed method from " coverage-dir "/; exit 1 over " crap/crap-limit "
  dry [--all] [--base REF] [--min-tokens N]
                              jscpd clones touching changed lines; exit 1 if any
  mutate [--base REF] [--all] [--report-only] [-- stryker args]
                              Stryker on changed files (--since); exit 1 on survivors

Every command runs from the task worktree. --base defaults to SWARMFORGE_BASE, main, or master."))

(def option-spec
  {:coerce {:all :boolean :report-only :boolean :help :boolean :min-tokens :long
            :base :string :report :string}
   :alias {:h :help}})

(defn- changed-for [opts]
  (git/changed-since-base opts ["*.cs"]))

(defn- cmd-tools []
  (let [missing (remove shell/available? ["dotnet" "node" "npx"])]
    (when (seq missing)
      (throw (outcome/usage-error (str "missing on PATH: " (str/join ", " missing))))))
  (let [manifest (fs/path ".config" "dotnet-tools.json")]
    (when-not (fs/exists? manifest)
      (shell/run "dotnet" ["new" "tool-manifest"]))
    (when-not (get-in (json/parse-string (slurp (str manifest))) ["tools" "dotnet-stryker"])
      (shell/run "dotnet" ["tool" "install" "dotnet-stryker"]))
    (shell/run "dotnet" ["tool" "restore"])
    (shell/run "npx" ["--yes" "jscpd@4" "--version"])))

(defn- cmd-test [args]
  (shell/run "dotnet" (into ["test" "--filter" property-filter] args)))

(defn- cmd-property [args]
  (shell/run "dotnet" (into ["test" "--filter" "Category=Property"] args)))

(defn- cmd-coverage [args]
  (fs/create-dirs coverage-dir)
  (doseq [stale (cob/find-cobertura-files coverage-dir)]
    (fs/delete-tree (fs/parent stale)))
  (let [status (shell/run "dotnet" (-> ["test" "--filter" property-filter
                                        "--collect" "XPlat Code Coverage"
                                        "--results-directory" coverage-dir]
                                       (into args)
                                       (conj "--" "DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura")))]
    (if-not (zero? status)
      status
      (let [files (cob/find-cobertura-files coverage-dir)]
        (when (empty? files)
          (throw (ex-info (str "no coverage.cobertura.xml under " coverage-dir
                               "; is coverlet.collector referenced by the test projects?") {})))
        (println (count files) "coverage file(s) under" coverage-dir)
        outcome/exit-ok))))

(defn- merge-methods
  "Keeps the best-covered entry when several test runs cover the same method."
  [files]
  (->> files
       (mapcat #(cob/parse-cobertura (slurp %)))
       (reduce (fn [best method]
                 (let [key ((juxt :file :class-name :name :signature) method)
                       seen (get best key)]
                   (if (or (nil? seen) (> (:coverage method) (:coverage seen)))
                     (assoc best key method)
                     best)))
               {})
       vals))

(defn- source-lines [root file]
  (let [full (if (fs/absolute? file) file (str (fs/path root file)))]
    (when (fs/exists? full)
      (str/split (slurp full) #"\r?\n" -1))))

(defn- select-method [root changed read-source method]
  (let [reported (:file method)
        file (if changed
               (changes/to-changed-path reported changed root)
               (if (fs/absolute? reported)
                 (let [relative (str (fs/relativize root reported))]
                   (if (str/blank? relative) reported relative))
                 reported))]
    (when file
      (let [source (read-source file)
            signature-line (if source (cob/signature-start source (:first-line method)) (:first-line method))]
        (when (or (nil? changed) (changes/overlaps? file signature-line (:last-line method) changed))
          (assoc method :file file :signature-line signature-line))))))

(defn- cmd-crap [opts]
  (let [dir (or (:report opts) coverage-dir)
        files (cob/find-cobertura-files dir)]
    (when (empty? files)
      (throw (outcome/usage-error (str "no coverage.cobertura.xml under " dir "; run sf-cs coverage first"))))
    (let [root (git/repo-root)
          changed (changed-for opts)
          read-source (memoize (partial source-lines root))
          selected (->> (merge-methods files)
                        (keep (partial select-method root changed read-source))
                        (sort-by (juxt (comp - :crap) :file :first-line)))]
      (shell/emit (crap/review selected cob/format-method "method" changed)))))

(defn- read-json [path]
  (json/parse-string (slurp path)))

(defn- cmd-dry [opts]
  (let [report-path (str (fs/path jscpd-dir "jscpd-report.json"))]
    (when-not (:report-only opts)
      (fs/create-dirs jscpd-dir)
      (let [status (shell/run "npx" ["--yes" "jscpd@4" "--format" "csharp"
                                     "--min-tokens" (str (or (:min-tokens opts) 50))
                                     "--reporters" "json" "--output" jscpd-dir
                                     "--ignore" "**/bin/**,**/obj/**,**/*.g.cs,**/*.Designer.cs,**/Migrations/**,**/tmp/**"
                                     "."])]
        (when (and (not (zero? status)) (not (fs/exists? report-path)))
          (System/exit status))))
    (when-not (fs/exists? report-path)
      (throw (outcome/usage-error (str report-path " not found"))))
    (let [clones (jscpd/parse-clones (read-json report-path))
          changed (changed-for opts)]
      (shell/emit (jscpd/review clones changed (when changed (git/repo-root)))))))

(defn- cmd-mutate [opts extra]
  (let [report-path (str (fs/path stryker-dir "reports" "mutation-report.json"))]
    (when-not (:report-only opts)
      (fs/create-dirs stryker-dir)
      (let [args (cond-> ["stryker" "--reporter" "json" "--reporter" "progress" "--output" stryker-dir]
                   (not (:all opts)) (conj (str "--since:" (git/base-ref opts))))
            status (shell/run "dotnet" (into args extra))]
        (when-not (fs/exists? report-path)
          (System/exit status))))
    (when-not (fs/exists? report-path)
      (throw (outcome/usage-error (str report-path " not found"))))
    (shell/emit (stryker/review (stryker/parse-mutants (read-json report-path))))))

(defn main [argv]
  (let [[command & args] argv]
    (if (or (nil? command) (#{"help" "--help" "-h"} command))
      (do (println usage) outcome/exit-ok)
      (let [[before after] (split-with #(not= "--" %) args)
            extra (vec (rest after))
            {:keys [opts]} (cli/parse-args (vec before) option-spec)]
        (if (:help opts)
          (do (println usage) outcome/exit-ok)
          (case command
            "tools" (cmd-tools)
            "test" (cmd-test (vec args))
            "property" (cmd-property (vec args))
            "coverage" (cmd-coverage (vec args))
            "crap" (cmd-crap opts)
            "dry" (cmd-dry opts)
            "mutate" (cmd-mutate opts extra)
            (throw (outcome/usage-error (str "unknown command '" command "'\n" usage)))))))))

(defn -main [& argv]
  (shell/run-main "sf-cs" main argv))
