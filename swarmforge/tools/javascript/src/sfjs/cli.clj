(ns sfjs.cli
  "sf-js: JavaScript and TypeScript tool wrapper for SwarmForge roles. One
  subcommand per constitution tool."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.command :as command]
            [sfcore.crap :as crap]
            [sfcore.git :as git]
            [sfcore.jscpd :as jscpd]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]
            [sfcore.stryker :as stryker]
            [sfjs.commands :as commands]
            [sfjs.crap :as jscrap]
            [sfjs.eslint :as eslint]
            [sfjs.mutate :as mutate]
            [sfjs.oxlint :as oxlint]
            [sfjs.package :as pkg]))

(def usage
  (str "usage: sf-js <command> [options]

  tools                       add missing tool packages with the package manager; check node and jscpd
  test [runner args]          tsc --noEmit when tsconfig.json exists, then tests without *.property.test.*
  property [runner args]      property tests only (*.property.test.*)
  coverage [runner args]      tests with Istanbul JSON coverage in " commands/coverage-report "
  crap [--all] [--base REF] [--report DIR]
                              CRAP per changed function (oxlint or ESLint complexity + coverage); exit 1 over " crap/crap-limit "
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

(defn- changed-for [opts]
  (git/changed-since-base opts commands/source-pathspecs))

(defn- exists? [path]
  (fs/exists? (shell/in-dir path)))

(defn- read-json [path]
  (json/parse-string (slurp (shell/in-dir path))))

(defn- write-file [path content]
  (fs/create-dirs (fs/parent (shell/in-dir path)))
  (spit (shell/in-dir path) content))

(defn- delete-file [path]
  (fs/delete-if-exists (shell/in-dir path)))

(defn- read-package []
  (when-not (exists? "package.json")
    (throw (outcome/usage-error "no package.json in the current directory")))
  (read-json "package.json"))

(defn- project
  "package.json and the package manager it settles on."
  []
  (let [package (read-package)]
    {:package package
     :manager (pkg/detect-manager package (filter exists? pkg/lockfiles))
     :tsconfig? (exists? "tsconfig.json")}))

(defn- run-tool [{:keys [manager]} tool-args]
  (let [[command & args] (pkg/exec-command manager tool-args)]
    (shell/run command args)))

(defn- capture-tool [{:keys [manager]} tool-args]
  (let [[command & args] (pkg/exec-command manager tool-args)]
    (shell/capture command args)))

(defn- verdict [status]
  (if (zero? status) outcome/exit-ok outcome/exit-findings))

(defn- cmd-tools [_]
  (let [{:keys [package manager tsconfig?]} (project)
        missing (remove shell/available? (pkg/required-commands manager))]
    (when (seq missing)
      (throw (outcome/usage-error (str "missing on PATH: " (str/join ", " missing)))))
    (if-let [packages (seq (pkg/missing-packages package tsconfig?))]
      (let [[command & args :as add] (pkg/add-command manager packages)]
        (when-not (zero? (shell/run command args))
          (throw (outcome/usage-error (str (str/join " " (take 3 add)) " failed")))))
      (println "all JavaScript tools present"))
    (let [[command & args] commands/jscpd-version-args]
      (when-not (zero? (shell/run command args))
        (throw (outcome/usage-error "npx --yes jscpd@4 failed"))))
    outcome/exit-ok))

(defn- cmd-test [{:keys [args]}]
  (let [{:keys [package tsconfig?] :as proj} (project)
        runner (pkg/require-runner package)
        typecheck (if tsconfig? (run-tool proj commands/typecheck-args) 0)]
    (if (zero? typecheck)
      (verdict (run-tool proj (commands/test-args runner args)))
      outcome/exit-findings)))

(defn- cmd-property [{:keys [args]}]
  (let [{:keys [package] :as proj} (project)]
    (verdict (run-tool proj (commands/property-args (pkg/require-runner package) args)))))

(defn- cmd-coverage [{:keys [args]}]
  (let [{:keys [package] :as proj} (project)
        runner (pkg/require-runner package)]
    (delete-file commands/coverage-report)
    (let [status (run-tool proj (commands/coverage-args runner args))]
      (cond
        (not (zero? status)) outcome/exit-findings
        (not (exists? commands/coverage-report))
        (throw (outcome/usage-error (str "no " commands/coverage-report " after the coverage run")))
        :else (do (println (str "coverage written to " commands/coverage-report)) outcome/exit-ok)))))

(defn- lint-eslint
  "Runs ESLint's complexity rule with sf-js's own config over `files`; fails
  when ESLint could not analyze them."
  [proj files root]
  (write-file eslint/config-path (eslint/config))
  (delete-file eslint/report-path)
  (let [status (run-tool proj (eslint/args eslint/config-path eslint/report-path files))]
    (when (= eslint/exit-failed status)
      (throw (outcome/usage-error (str "eslint exited " status))))
    (when-not (exists? eslint/report-path)
      (throw (outcome/usage-error (str "eslint exited " status " without " eslint/report-path))))
    (let [report (read-json eslint/report-path)]
      (when-let [fatal (seq (eslint/fatal-messages report root))]
        (throw (outcome/usage-error (str/join "\n" (map #(str "eslint could not analyze " %) fatal)))))
      report)))

(defn- linted-sources
  "The sources a lint of `files` covers: every one git knows for \".\"."
  [files]
  (commands/linted-sources
   (if (= ["."] files)
     (str/split-lines (git/git (into ["ls-files" "--cached" "--others" "--exclude-standard" "--"]
                                     commands/source-pathspecs)))
     files)))

(defn- warn-hiding-directives
  "Warns about each oxlint-disable directive in the linted sources that can
  hide a function from CRAP."
  [files]
  (doseq [file (linted-sources files)
          :when (exists? file)
          line (oxlint/hiding-lines (slurp (shell/in-dir file)))]
    (shell/warn "sf-js" (str file ":" line " has an oxlint-disable directive that can hide a function from CRAP"))))

(defn- lint-oxlint
  "Runs oxlint's complexity rule with sf-js's own config over `files`;
  oxlint's stdout goes to stderr when it is not a report."
  [proj files]
  (write-file oxlint/config-path (oxlint/config))
  (write-file oxlint/ignore-path "")
  (let [{:keys [exit out]} (capture-tool proj (oxlint/args oxlint/config-path files))]
    (when-not (oxlint/report out)
      (binding [*out* *err*] (print out) (flush)))
    (oxlint/read-report exit out)))

(defn- complexity
  "Per-function complexity of `files` from the project's complexity linter."
  [{:keys [package] :as proj} files root]
  (if (= "eslint" (pkg/complexity-linter package))
    (eslint/parse-report (lint-eslint proj files root) root)
    (let [functions (oxlint/parse-report (lint-oxlint proj files) root)]
      (warn-hiding-directives files)
      functions)))

(defn- cmd-crap [{:keys [opts]}]
  (let [dir (or (:report opts) commands/coverage-dir)
        coverage (str (fs/path dir commands/coverage-file))]
    (when-not (exists? coverage)
      (throw (outcome/usage-error (str "no " commands/coverage-file " under " dir "; run sf-js coverage first"))))
    (let [proj (project)
          root (git/repo-root)
          changed (changed-for opts)
          files (if changed (commands/production-sources (keys changed)) ["."])
          functions (if (seq files)
                      (jscrap/score (complexity proj files root) (read-json coverage) root)
                      [])]
      (shell/emit (crap/review (jscrap/select functions changed) crap/format-row "function" changed)))))

(defn- cmd-dry [{:keys [opts]}]
  (when-not (:report-only opts)
    (fs/create-dirs (shell/in-dir commands/jscpd-dir))
    (delete-file commands/jscpd-report)
    (let [[command & args] (commands/jscpd-args (or (:min-tokens opts) 50))
          status (shell/run command args)]
      (when (and (not (zero? status)) (not (exists? commands/jscpd-report)))
        (throw (outcome/usage-error (str "jscpd exited " status " without " commands/jscpd-report))))))
  (when-not (exists? commands/jscpd-report)
    (throw (outcome/usage-error (str commands/jscpd-report " not found"))))
  (let [clones (jscpd/parse-clones (read-json commands/jscpd-report))
        changed (changed-for opts)]
    (shell/emit (jscpd/review clones changed (when changed (git/repo-root))))))

(defn- mutate-scope
  "The changed source files to mutate and judge, or nil with --all."
  [opts]
  (when-let [changed (changed-for opts)]
    (commands/production-sources (keys changed))))

(defn- run-stryker
  "Runs Stryker on `files`; false when there was nothing to mutate."
  [files extra]
  (let [{:keys [package tsconfig?] :as proj} (project)
        runner (pkg/require-stryker-runner package)]
    (when-let [errors (seq (mutate/comma-errors files))]
      (throw (outcome/usage-error (str/join "\n" errors))))
    (if (and files (empty? files))
      (do (println "no changed source files to mutate") false)
      (do
        (write-file mutate/config-path (mutate/stryker-config))
        (delete-file mutate/report-path)
        (let [status (run-tool proj (mutate/stryker-args {:runner runner :tsconfig? tsconfig?
                                                           :files files :extra extra}))]
          (when-not (exists? mutate/report-path)
            (throw (outcome/usage-error (str "stryker exited " status " without " mutate/report-path)))))
        true))))

(defn- cmd-mutate [{:keys [opts extra]}]
  (let [files (mutate-scope opts)]
    (if (and (not (:report-only opts)) (not (run-stryker files extra)))
      outcome/exit-ok
      (do
        (when-not (exists? mutate/report-path)
          (throw (outcome/usage-error (str mutate/report-path " not found"))))
        (let [mutants (stryker/parse-mutants (read-json mutate/report-path))]
          (shell/emit (stryker/review (mutate/in-scope mutants files (when files (git/repo-root))))))))))

(def ^:private commands
  {"tools" cmd-tools "test" cmd-test "property" cmd-property "coverage" cmd-coverage
   "crap" cmd-crap "dry" cmd-dry "mutate" cmd-mutate})

(defn main [argv]
  (command/main {:usage usage :commands commands :option-spec option-spec :help-option? false} argv))

(defn -main [& argv]
  (shell/run-main "sf-js" main argv))
