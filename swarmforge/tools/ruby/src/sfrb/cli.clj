(ns sfrb.cli
  "sf-rb: Ruby tool wrapper for SwarmForge roles. Tests, property tests and
  the security scanners are plain commands in swarmforge/languages/ruby.md;
  sf-rb owns the commands that need guardrails or report joining."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [sfcore.command :as command]
            [sfcore.crap :as crap]
            [sfcore.git :as git]
            [sfcore.jscpd :as jscpd]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]
            [sfrb.commands :as commands]
            [sfrb.crap :as rbcrap]
            [sfrb.flay :as flay]
            [sfrb.flog :as flog]
            [sfrb.mutant :as mutant]
            [sfrb.rubocop :as rubocop]))

(def usage
  (str "usage: sf-rb <command> [options]

  coverage [rspec args]       rspec without *_property_spec.rb; SimpleCov JSON in " commands/coverage-dir "/
  crap [--complexity cyclomatic|perceived|flog] [--all] [--base REF] [--report DIR]
                              CRAP per changed method (default cyclomatic); exit 1 over " crap/crap-limit "
  dry [--tool jscpd|flay] [--all] [--base REF] [--min-tokens N] [--mass N] [--report-only]
                              clones touching changed lines (default jscpd); exit 1 if any
  mutate [--base REF] [--all] [-- mutant args]
                              mutant on subjects touched since the merge base; exit 1 on alive mutants

Every command runs from the task worktree beside Gemfile; gems run through
bundle exec. --base defaults to SWARMFORGE_BASE, main, or master."))

(def option-spec
  {:coerce {:all :boolean :report-only :boolean :help :boolean :min-tokens :long :mass :long
            :base :string :report :string :complexity :string :tool :string}
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

(defn- require-gemfile []
  (when-not (exists? "Gemfile")
    (throw (outcome/usage-error "no Gemfile in the current directory"))))

(defn- resource
  "The path of one of sf-rb's Ruby files, which Ruby loads by path."
  [name]
  (str (fs/path (.toURI (io/resource (str "sfrb/" name))))))

(defn- bundle [args]
  (require-gemfile)
  (shell/run "bundle" args))

(defn- capture-bundle [args]
  (require-gemfile)
  (shell/capture "bundle" args))

(defn- known-ruby-files
  "Every Ruby file git tracks or would add, relative to the current directory."
  []
  (filter exists?
          (str/split-lines (git/git (into ["ls-files" "--cached" "--others" "--exclude-standard" "--"]
                                          commands/source-pathspecs)))))

(defn- cmd-coverage [{:keys [args]}]
  (require-gemfile)
  (let [setup (resource "simplecov.rb")]
    (when (re-find #"\s" setup)
      (throw (outcome/usage-error (str "RUBYOPT cannot require " setup ": its path has whitespace"))))
    (fs/delete-tree (shell/in-dir commands/coverage-dir))
    (let [status (shell/run "bundle" (commands/rspec-args args)
                            (commands/coverage-env (System/getenv "RUBYOPT") setup))]
      (cond
        (not (zero? status)) status
        (not (exists? commands/coverage-report))
        (throw (outcome/usage-error (str "no " commands/coverage-report " after the coverage run; is simplecov in the bundle?")))
        :else (do (println (str "coverage written to " commands/coverage-report)) outcome/exit-ok)))))

(defn- analysis-failed [tool status]
  (outcome/usage-error (str tool " exited " status " without a report")))

(defn- rubocop-methods [source files root]
  (let [cop (rubocop/cops source)
        config (rubocop/config-path source)]
    (write-file config (rubocop/config cop))
    (let [{:keys [exit out]} (capture-bundle (rubocop/args config files))
          report (when-not (= rubocop/exit-failed exit)
                   (try (json/parse-string out) (catch Exception _ nil)))]
      (when-not report
        (throw (analysis-failed "rubocop" exit)))
      (when-let [errors (seq (rubocop/analysis-errors report root))]
        (throw (outcome/usage-error (str/join "\n" errors))))
      (rubocop/parse-report report cop root))))

(defn- flog-methods [files root]
  (let [{:keys [exit out]} (capture-bundle (flog/args files))]
    (when-not (zero? exit)
      (throw (analysis-failed "flog" exit)))
    (flog/parse-report out root)))

(defn- complexity-source [opts]
  (let [source (or (:complexity opts) "cyclomatic")]
    (when-not (or (rubocop/cops source) (= "flog" source))
      (throw (outcome/usage-error (str "unknown --complexity '" source "'; use cyclomatic, perceived or flog"))))
    source))

(defn- methods-of [source files root]
  (cond
    (empty? files) []
    (= "flog" source) (flog-methods files root)
    :else (rubocop-methods source files root)))

(defn- cmd-crap [{:keys [opts]}]
  (let [source (complexity-source opts)
        dir (or (:report opts) commands/coverage-dir)
        coverage (str (fs/path dir commands/coverage-file))]
    (when-not (exists? coverage)
      (throw (outcome/usage-error (str "no " commands/coverage-file " under " dir "; run sf-rb coverage first"))))
    (let [root (git/repo-root)
          changed (changed-for opts)
          files (commands/production-sources (if changed (keys changed) (known-ruby-files)))
          scored (rbcrap/score (methods-of source files root) (read-json coverage) root)]
      (shell/emit (crap/review (crap/select scored changed) crap/format-row "method" changed)))))

(def ^:private dry-tools
  {"jscpd" {:report commands/jscpd-report :parse jscpd/parse-clones}
   "flay" {:report commands/flay-report :parse flay/parse-clones}})

(defn- run-jscpd [opts]
  (fs/create-dirs (shell/in-dir commands/jscpd-dir))
  (let [[command & args] (commands/jscpd-args (or (:min-tokens opts) 50))]
    (shell/run command args)))

(defn- run-flay [opts]
  (fs/create-dirs (shell/in-dir commands/flay-dir))
  (bundle (commands/flay-args (resource "flay_report.rb") (or (:mass opts) 16)
                              (commands/ruby-sources (known-ruby-files)))))

(defn- refresh-report
  "Discards the tool's last report and runs it again; fails when the run
  left no report."
  [tool report opts]
  (fs/delete-if-exists (shell/in-dir report))
  (let [status (if (= "flay" tool) (run-flay opts) (run-jscpd opts))]
    (when-not (exists? report)
      (throw (outcome/usage-error (str tool " exited " status " without " report))))))

(defn- cmd-dry [{:keys [opts]}]
  (let [tool (or (:tool opts) "jscpd")
        {:keys [report parse]} (or (dry-tools tool)
                                   (throw (outcome/usage-error (str "unknown --tool '" tool "'; use jscpd or flay"))))]
    (if (:report-only opts)
      (when-not (exists? report)
        (throw (outcome/usage-error (str report " not found"))))
      (refresh-report tool report opts))
    (let [clones (parse (read-json report))
          changed (changed-for opts)]
      (shell/emit (jscpd/review clones changed (when changed (git/repo-root)))))))

(defn- usage-declared? []
  (some #(and (exists? %) (mutant/declares-usage? (slurp (shell/in-dir %)))) mutant/config-files))

(defn- cmd-mutate [{:keys [opts extra]}]
  (require-gemfile)
  (when (mutant/sets-usage? extra)
    (throw (outcome/usage-error "--usage is the project's choice; set usage in its mutant configuration")))
  (when-not (usage-declared?)
    (throw (outcome/usage-error mutant/no-usage)))
  (let [since (when-not (:all opts)
                (str/trim (git/git ["merge-base" (git/base-ref opts) "HEAD"])))]
    (shell/run "bundle" (mutant/args since extra))))

(def ^:private commands
  {"coverage" cmd-coverage "crap" cmd-crap "dry" cmd-dry "mutate" cmd-mutate})

(defn main [argv]
  (command/main {:usage usage :commands commands :option-spec option-spec :help-option? true} argv))

(defn -main [& argv]
  (shell/run-main "sf-rb" main argv))
