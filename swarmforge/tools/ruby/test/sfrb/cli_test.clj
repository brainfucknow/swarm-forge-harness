(ns sfrb.cli-test
  "sf-rb commands run in-process against a scratch git project, with Bundler,
  npx and the gems replaced by scripted fakes that record their command lines."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sfcore.shell :as shell]
            [sfrb.cli :as cli]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(def ^:dynamic *project* nil)
(def ^:dynamic *calls* nil)

(defn- git [& args]
  (apply p/shell {:dir *project* :out :string :err :string} "git" args))

(defn- project-file [path]
  (str (fs/path *project* path)))

(defn- write! [path content]
  (fs/create-dirs (fs/parent (project-file path)))
  (spit (project-file path) content))

(defn- fixture [name]
  (slurp (str (fs/path fixtures name))))

(defn- commit! [message]
  (git "add" ".")
  (git "commit" "--quiet" "-m" message))

(defn- scratch-project [f]
  (let [dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "sfrb-cli"})))]
    (try
      (binding [*project* dir
                *calls* (atom [])]
        (git "init" "--quiet" "--initial-branch=main")
        (git "config" "user.email" "test@example.com")
        (git "config" "user.name" "Test")
        (git "config" "commit.gpgsign" "false")
        (write! "Gemfile" "source \"https://rubygems.org\"\n")
        (write! "lib/ledger.rb" (fixture "ledger.rb"))
        (write! "spec/ledger_spec.rb" "require \"ledger\"\n")
        (commit! "base")
        (git "switch" "--quiet" "-c" "swarm/task")
        (f))
      (finally (fs/delete-tree dir)))))

(use-fixtures :each scratch-project)

(defn- change-balance! []
  (write! "lib/ledger.rb" (str/replace (fixture "ledger.rb") "total -= entry[:amount]" "total -= entry[:amount].abs"))
  (commit! "change balance"))

(defn- tool-line [command args env]
  (str/join " " (concat (map (fn [[k v]] (str k "=" v)) env) (cons command args))))

(defn- answer
  "What `tool` answers for a command line: an exit status, or {:exit :out}
  for a tool whose stdout sf-rb reads."
  [tool command args env]
  (let [line (tool-line command args env)
        answered (tool line)]
    (swap! *calls* conj line)
    (if (map? answered) answered {:exit answered :out ""})))

(defn- run-sf-rb
  "Runs `argv` in the scratch project; `tool` answers each tool command line.
  Returns the exit status, the printed text and the tool command lines."
  [tool & argv]
  (reset! *calls* [])
  (binding [shell/*dir* *project*]
    (with-redefs [shell/run (fn [command args & [env]] (:exit (answer tool command args env)))
                  shell/capture (fn [command args] (answer tool command args nil))]
      (let [out (java.io.StringWriter.)
            status (binding [*out* out] (cli/main (vec argv)))]
        {:status status :out (str out) :calls @*calls*}))))

(defn- usage-failure [tool & argv]
  (try (apply run-sf-rb tool argv)
       nil
       (catch Exception e (ex-message e))))

(defn- ok [_] 0)

(deftest help-and-unknown-commands
  (is (str/starts-with? (:out (run-sf-rb ok)) "usage: sf-rb"))
  (is (str/starts-with? (:out (run-sf-rb ok "crap" "--help")) "usage: sf-rb"))
  (is (str/includes? (usage-failure ok "frobnicate") "unknown command 'frobnicate'")))

;; coverage

(defn- writes-coverage [line]
  (when (str/includes? line "rspec")
    (write! "tmp/coverage/coverage.json" "{}"))
  0)

(deftest coverage-runs-rspec-with-simplecov-required-first
  (write! "tmp/coverage/stale.json" "{}")
  (let [{:keys [status out calls]} (run-sf-rb writes-coverage "coverage" "--seed" "3")]
    (is (= 0 status))
    (is (= 1 (count calls)))
    (is (re-matches #"RUBYOPT=-r/\S+/sfrb/simplecov\.rb BUNDLE_DISABLE_EXEC_LOAD=true bundle exec rspec --exclude-pattern \*\*/\*_property_spec\.rb --seed 3"
                    (first calls)))
    (is (fs/exists? (second (re-find #"-r(\S+)" (first calls)))))
    (is (str/includes? out "coverage written to tmp/coverage/coverage.json"))
    (is (not (fs/exists? (project-file "tmp/coverage/stale.json"))))))

(deftest coverage-keeps-rspecs-status-and-wants-a-report
  (is (= 1 (:status (run-sf-rb (constantly 1) "coverage"))))
  (is (str/includes? (usage-failure ok "coverage") "no tmp/coverage/coverage.json after the coverage run")))

(deftest bundled-commands-need-a-gemfile
  (fs/delete (project-file "Gemfile"))
  (is (str/includes? (usage-failure ok "coverage") "no Gemfile"))
  (is (str/includes? (usage-failure ok "mutate") "no Gemfile")))

;; crap

(defn- with-coverage! []
  (write! "tmp/coverage/coverage.json" (fixture "coverage.json")))

(defn- analyser [line]
  (cond
    (str/includes? line "rubocop") {:exit 1 :out (fixture (if (str/includes? line "perceived")
                                                             "rubocop-perceived.json"
                                                             "rubocop-cyclomatic.json"))}
    (str/includes? line "flog") {:exit 0 :out (fixture "flog.txt")}
    :else 0))

(deftest crap-scores-changed-methods-with-rubocop-cyclomatic-complexity-by-default
  (with-coverage!)
  (change-balance!)
  (let [{:keys [status out calls]} (run-sf-rb analyser "crap")]
    (is (= 0 status))
    (is (= ["bundle exec rubocop --config tmp/rubocop/cyclomatic.yml --ignore-disable-comments --cache false --format json -- lib/ledger.rb"]
           calls))
    (is (= ["ok   crap    5.6  cc   5  cov  71%  lib/ledger.rb:6 balance"
            "1 changed method(s), 0 over CRAP 10"]
           (str/split-lines out)))
    (is (str/includes? (slurp (project-file "tmp/rubocop/cyclomatic.yml")) "Metrics/CyclomaticComplexity:"))))

(deftest crap-takes-each-complexity-source
  (with-coverage!)
  (change-balance!)
  (testing "perceived"
    (is (str/includes? (:out (run-sf-rb analyser "crap" "--complexity" "perceived")) "cc   6  cov  71%  lib/ledger.rb:6 balance")))
  (testing "flog, whose score can push a method over"
    (let [{:keys [status out calls]} (run-sf-rb analyser "crap" "--complexity" "flog")]
      (is (= 1 status))
      (is (= ["bundle exec flog -a -- lib/ledger.rb"] calls))
      (is (str/starts-with? out "OVER crap   20.8  cc 15.3  cov  71%  lib/ledger.rb:6 Ledger#balance")))))

(deftest crap-lists-unchanged-methods-only-with-all
  (with-coverage!)
  (change-balance!)
  (is (= 2 (count (str/split-lines (:out (run-sf-rb analyser "crap"))))))
  (let [{:keys [out calls]} (run-sf-rb analyser "crap" "--all")]
    (is (str/includes? (first calls) "-- lib/ledger.rb"))
    (is (str/ends-with? (str/trim out) "7 method(s), 0 over CRAP 10"))))

(deftest crap-never-analyses-specs
  (with-coverage!)
  (write! "spec/ledger_spec.rb" "require \"ledger\"\nit { }\n")
  (commit! "change spec")
  (let [{:keys [out calls]} (run-sf-rb analyser "crap")]
    (is (= [] calls))
    (is (= "0 changed method(s), 0 over CRAP 10\n" out)))
  (is (not-any? #(str/includes? % "spec/") (:calls (run-sf-rb analyser "crap" "--all")))))

(deftest crap-input-errors-print-no-table
  (change-balance!)
  (is (str/includes? (usage-failure analyser "crap") "no coverage.json under tmp/coverage; run sf-rb coverage first"))
  (with-coverage!)
  (is (str/includes? (usage-failure analyser "crap" "--complexity" "skunk") "unknown --complexity 'skunk'"))
  (is (str/includes? (usage-failure (constantly {:exit 1 :out (fixture "rubocop-syntax-error.json")}) "crap")
                     "rubocop could not parse lib/broken.rb:1"))
  (testing "a gem missing from the bundle"
    (is (str/includes? (usage-failure (constantly {:exit 127 :out ""}) "crap") "rubocop exited 127 without a report"))
    (is (str/includes? (usage-failure (constantly {:exit 1 :out ""}) "crap" "--complexity" "flog") "flog exited 1")))
  (is (str/includes? (usage-failure (constantly {:exit 2 :out "{}"}) "crap") "rubocop exited 2")))

(deftest crap-reads-coverage-from-another-directory
  (write! "out/cov/coverage.json" (fixture "coverage.json"))
  (change-balance!)
  (is (= 0 (:status (run-sf-rb analyser "crap" "--report" "out/cov")))))

;; dry

(defn- writes-report [path content]
  (fn [line]
    (when (or (str/includes? line "jscpd@4 --format") (str/includes? line "flay_report.rb"))
      (write! path content))
    0))

(defn- add-clones! []
  (doseq [name ["a" "b" "c" "d"]]
    (write! (str "lib/" name ".rb") (str/join (repeat 12 "# line\n"))))
  (commit! "clones"))

(deftest dry-reports-jscpd-clones-touching-changed-lines
  (add-clones!)
  (let [{:keys [status out calls]} (run-sf-rb (writes-report "tmp/jscpd/jscpd-report.json"
                                                             (fixture "jscpd-report.json"))
                                              "dry" "--min-tokens" "40")]
    (is (= 1 status))
    (is (= ["npx --yes jscpd@4 --format ruby --min-tokens 40 --reporters json --output tmp/jscpd --ignore **/node_modules/**,**/dist/**,**/build/**,**/coverage/**,**/tmp/**,**/vendor/** ."]
           calls))
    (is (= ["166 tokens  lib/a.rb:2-12 (changed)  ==  lib/b.rb:2-12 (changed)" "1 of 1 clone(s) touch changed lines"]
           (str/split-lines out)))))

(deftest dry-matches-absolute-report-paths-to-the-changes
  (add-clones!)
  (let [report (-> (fixture "jscpd-report.json")
                   (str/replace "\"lib/a.rb\"" (str "\"" (project-file "lib/a.rb") "\""))
                   (str/replace "\"lib/b.rb\"" (str "\"" (project-file "lib/b.rb") "\"")))
        {:keys [status out]} (run-sf-rb (writes-report "tmp/jscpd/jscpd-report.json" report) "dry")]
    (is (= 1 status))
    (is (str/includes? out "1 of 1 clone(s) touch changed lines"))))

(deftest dry-with-flay-runs-the-reporter-over-every-ruby-file
  (add-clones!)
  (write! "vendor/bundle/gem.rb" "x\n")
  (let [{:keys [status out calls]} (run-sf-rb (writes-report "tmp/flay/flay-report.json" (fixture "flay-report.json"))
                                              "dry" "--tool" "flay" "--mass" "20")]
    (is (= 1 status))
    (is (re-matches #"bundle exec ruby /\S+/sfrb/flay_report\.rb 20 tmp/flay/flay-report\.json lib/a\.rb lib/b\.rb lib/c\.rb lib/d\.rb lib/ledger\.rb spec/ledger_spec\.rb"
                    (first calls)))
    (is (= ["585 mass  lib/a.rb:2-11 (changed)  ==  lib/b.rb:2-11 (changed)  ==  lib/c.rb:2-11 (changed)"
            "34 mass  lib/d.rb:2-4 (changed)  ==  lib/d.rb:5-7 (changed)"
            "2 of 2 clone(s) touch changed lines"]
           (str/split-lines out)))))

(deftest dry-passes-when-no-clone-touches-a-change
  (let [{:keys [status out]} (run-sf-rb (writes-report "tmp/flay/flay-report.json" (fixture "flay-report.json"))
                                        "dry" "--tool" "flay")]
    (is (= 0 status))
    (is (str/includes? out "0 of 2 clone(s) touch changed lines"))))

(deftest dry-report-only-rereads-the-last-report-of-the-same-tool
  (add-clones!)
  (write! "tmp/flay/flay-report.json" (fixture "flay-report.json"))
  (let [{:keys [status calls]} (run-sf-rb ok "dry" "--tool" "flay" "--report-only")]
    (is (= 1 status))
    (is (= [] calls)))
  (is (str/includes? (usage-failure ok "dry" "--report-only") "tmp/jscpd/jscpd-report.json not found")))

(deftest dry-discards-the-old-report-before-a-run
  (write! "tmp/jscpd/jscpd-report.json" (fixture "jscpd-report.json"))
  (is (str/includes? (usage-failure (constantly 3) "dry") "jscpd exited 3 without tmp/jscpd/jscpd-report.json"))
  (is (str/includes? (usage-failure ok "dry" "--tool" "pmd") "unknown --tool 'pmd'")))

;; mutate

(defn- merge-base []
  (str/trim (:out (git "merge-base" "main" "HEAD"))))

(deftest mutate-runs-mutant-since-the-merge-base
  (write! "mutant.yml" "usage: opensource\n")
  (change-balance!)
  (let [{:keys [status calls]} (run-sf-rb (constantly 1) "mutate" "--" "--jobs" "16" "Ledger*")]
    (is (= 1 status))
    (is (= [(str "bundle exec mutant run --jobs 4 --since " (merge-base)
                 " --integration-argument --fail-fast --integration-argument --exclude-pattern"
                 " --integration-argument **/*_property_spec.rb --integration-argument spec Ledger*")]
           calls)))
  (is (not (str/includes? (first (:calls (run-sf-rb ok "mutate" "--all"))) "--since"))))

(deftest mutate-finds-the-usage-in-any-mutant-configuration
  (write! "config/mutant.yml" "usage: commercial\n")
  (is (= 0 (:status (run-sf-rb ok "mutate")))))

(deftest mutate-without-a-usage-never-starts-mutant
  (write! "mutant.yml" "integration:\n  name: rspec\n")
  (reset! *calls* [])
  (is (str/includes? (usage-failure ok "mutate") "mutation is optional and needs a mutant usage"))
  (is (= [] @*calls*)))

(deftest mutate-leaves-the-usage-to-the-project
  (write! "mutant.yml" "usage: opensource\n")
  (is (str/includes? (usage-failure ok "mutate" "--" "--usage" "commercial") "--usage is the project's choice")))
