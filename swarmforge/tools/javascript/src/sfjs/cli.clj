(ns sfjs.cli
  "sf-js: JavaScript and TypeScript tool wrapper for SwarmForge roles. One
  subcommand per constitution tool."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.crap :as crap]
            [sfcore.jscpd :as jscpd]
            [sfcore.shell :as shell]
            [sfcore.stryker :as stryker]
            [sfjs.commands :as commands]
            [sfjs.crap :as jscrap]
            [sfjs.mutate :as mutate]
            [sfjs.package :as pkg]))

(def coverage-file "coverage-final.json")
(def coverage-report (str commands/coverage-dir "/" coverage-file))
(def jscpd-report (str commands/jscpd-dir "/jscpd-report.json"))
(def eslint-config (str commands/eslint-dir "/eslint.config.mjs"))
(def eslint-report (str commands/eslint-dir "/eslint.json"))

(def usage
  (str "usage: sf-js <command> [options]

  tools                       add missing tool packages with the package manager; check node and jscpd
  test [runner args]          tsc --noEmit when tsconfig.json exists, then tests without *.property.test.*
  property [runner args]      property tests only (*.property.test.*)
  coverage [runner args]      tests with Istanbul JSON coverage in " coverage-report "
  crap [--all] [--base REF] [--report DIR]
                              CRAP per changed function (ESLint complexity + coverage); exit 1 over " crap/crap-limit "
  dry [--all] [--base REF] [--min-tokens N] [--report-only]
                              jscpd clones touching changed lines; exit 1 if any
  mutate [--base REF] [--all] [--report-only] [-- stryker args]
                              StrykerJS on changed source files (incremental); exit 1 on survivors

Every command runs from the task worktree, next to package.json. The package
manager comes from packageManager in package.json, else from the lockfile.
--base defaults to SWARMFORGE_BASE, main, or master."))

(def option-spec
  {:coerce {:all :boolean :report-only :boolean :help :boolean :min-tokens :long
            :base :string :report :string}
   :alias {:h :help}})

(defn- base-of [opts]
  (or (:base opts) (changes/default-base nil)))

(defn- changed-for [opts]
  (when-not (:all opts)
    (changes/collect-changed-lines {:base (base-of opts) :pathspecs commands/source-pathspecs})))

(defn- read-json [path]
  (json/parse-string (slurp (str path))))

(defn- read-package []
  (when-not (fs/exists? "package.json")
    (throw (shell/usage-error "no package.json in the current directory")))
  (read-json "package.json"))

(defn- project
  "package.json and the package manager it settles on."
  []
  (let [package (read-package)]
    {:package package
     :manager (pkg/detect-manager package (filter fs/exists? pkg/lockfiles))
     :tsconfig? (fs/exists? "tsconfig.json")}))

(defn- run-tool [{:keys [manager]} tool-args]
  (let [[command & args] (pkg/exec-command manager tool-args)]
    (shell/run command args)))

(defn- verdict [status]
  (if (zero? status) shell/exit-ok shell/exit-findings))

(defn- cmd-tools []
  (let [{:keys [package manager tsconfig?]} (project)
        missing (remove shell/available? (pkg/required-commands manager))]
    (when (seq missing)
      (throw (shell/usage-error (str "missing on PATH: " (str/join ", " missing)))))
    (if-let [packages (seq (pkg/missing-packages package tsconfig?))]
      (let [[command & args :as add] (pkg/add-command manager packages)]
        (when-not (zero? (shell/run command args))
          (throw (shell/usage-error (str (str/join " " (take 3 add)) " failed")))))
      (println "all JavaScript tools present"))
    (let [[command & args] ["npx" "--yes" "jscpd@4" "--version"]]
      (when-not (zero? (shell/run command args))
        (throw (shell/usage-error "npx --yes jscpd@4 failed"))))
    shell/exit-ok))

(defn- cmd-test [args]
  (let [{:keys [package tsconfig?] :as proj} (project)
        runner (pkg/require-runner package)
        typecheck (if tsconfig? (run-tool proj commands/typecheck-args) 0)]
    (if (zero? typecheck)
      (verdict (run-tool proj (commands/test-args runner args)))
      shell/exit-findings)))

(defn- cmd-property [args]
  (let [{:keys [package] :as proj} (project)]
    (verdict (run-tool proj (commands/property-args (pkg/require-runner package) args)))))

(defn- cmd-coverage [args]
  (let [{:keys [package] :as proj} (project)
        runner (pkg/require-runner package)]
    (fs/delete-if-exists coverage-report)
    (let [status (run-tool proj (commands/coverage-args runner args))]
      (cond
        (not (zero? status)) shell/exit-findings
        (not (fs/exists? coverage-report))
        (throw (shell/usage-error (str "no " coverage-report " after the coverage run")))
        :else (do (println (str "coverage written to " coverage-report)) shell/exit-ok)))))

(defn- lint
  "Runs ESLint's complexity rule with sf-js's own config over `files`."
  [proj files]
  (fs/create-dirs commands/eslint-dir)
  (spit eslint-config (commands/eslint-config))
  (fs/delete-if-exists eslint-report)
  (let [status (run-tool proj (commands/eslint-args eslint-config eslint-report files))]
    (when-not (fs/exists? eslint-report)
      (throw (shell/usage-error (str "eslint exited " status " without " eslint-report))))
    (read-json eslint-report)))

(defn- cmd-crap [opts]
  (let [dir (or (:report opts) commands/coverage-dir)
        coverage (fs/path dir coverage-file)]
    (when-not (fs/exists? coverage)
      (throw (shell/usage-error (str "no " coverage-file " under " dir "; run sf-js coverage first"))))
    (let [proj (project)
          root (shell/repo-root)
          changed (changed-for opts)
          files (if changed (commands/production-sources (keys changed)) ["."])
          functions (if (seq files)
                      (jscrap/score (jscrap/parse-eslint (lint proj files) root) (read-json coverage) root)
                      [])
          selected (->> functions
                        (filter #(or (nil? changed) (changes/overlaps? (:file %) (:line %) (:last-line %) changed)))
                        crap/sort-worst-first)]
      (doseq [function selected] (println (crap/format-row function)))
      (println (crap/format-summary selected "function" changed))
      (if (some crap/over? selected) shell/exit-findings shell/exit-ok))))

(defn- cmd-dry [opts]
  (when-not (:report-only opts)
    (fs/create-dirs commands/jscpd-dir)
    (let [[command & args] (commands/jscpd-args (or (:min-tokens opts) 50))
          status (shell/run command args)]
      (when (and (not (zero? status)) (not (fs/exists? jscpd-report)))
        (throw (shell/usage-error (str "jscpd exited " status " without " jscpd-report))))))
  (when-not (fs/exists? jscpd-report)
    (throw (shell/usage-error (str jscpd-report " not found"))))
  (let [clones (jscpd/parse-clones (read-json jscpd-report))
        changed (changed-for opts)
        shown (if changed (jscpd/clones-touching-changes clones changed (shell/repo-root)) clones)]
    (doseq [clone shown] (println (jscpd/format-clone clone)))
    (println (jscpd/format-summary shown clones changed))
    (if (seq shown) shell/exit-findings shell/exit-ok)))

(defn- run-stryker
  "Runs Stryker; false when there was nothing to mutate."
  [opts extra]
  (let [{:keys [package tsconfig?] :as proj} (project)
        runner (pkg/require-stryker-runner package)
        files (when-let [changed (changed-for opts)] (commands/production-sources (keys changed)))]
    (if (and files (empty? files))
      (do (println "no changed source files to mutate") false)
      (do
        (fs/create-dirs commands/stryker-dir)
        (spit mutate/config-path (mutate/stryker-config))
        (fs/delete-if-exists mutate/report-path)
        (let [status (run-tool proj (mutate/stryker-args {:runner runner :tsconfig? tsconfig?
                                                           :files files :extra extra}))]
          (when-not (fs/exists? mutate/report-path)
            (throw (shell/usage-error (str "stryker exited " status " without " mutate/report-path)))))
        true))))

(defn- cmd-mutate [opts extra]
  (if (and (not (:report-only opts)) (not (run-stryker opts extra)))
    shell/exit-ok
    (do
      (when-not (fs/exists? mutate/report-path)
        (throw (shell/usage-error (str mutate/report-path " not found"))))
      (let [mutants (stryker/parse-mutants (read-json mutate/report-path))
            alive (stryker/survivors mutants)]
        (doseq [mutant alive] (println (stryker/format-mutant mutant)))
        (println (stryker/format-summary (stryker/summarize mutants)))
        (if (seq alive) shell/exit-findings shell/exit-ok)))))

(defn main [argv]
  (let [[command & args] argv]
    (if (or (nil? command) (#{"help" "--help" "-h"} command))
      (do (println usage) shell/exit-ok)
      (let [[before after] (split-with #(not= "--" %) args)
            extra (vec (rest after))
            {:keys [opts]} (cli/parse-args (vec before) option-spec)]
        (case command
          "tools" (cmd-tools)
          "test" (cmd-test (vec args))
          "property" (cmd-property (vec args))
          "coverage" (cmd-coverage (vec args))
          "crap" (cmd-crap opts)
          "dry" (cmd-dry opts)
          "mutate" (cmd-mutate opts extra)
          (throw (shell/usage-error (str "unknown command '" command "'\n" usage))))))))

(defn -main [& argv]
  (shell/run-main "sf-js" main argv))
