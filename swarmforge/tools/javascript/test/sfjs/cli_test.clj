(ns sfjs.cli-test
  "sf-js commands run in-process against a scratch git project, with the
  JavaScript tools replaced by scripted fakes that record their command lines."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sfcore.shell :as shell]
            [sfjs.cli :as cli]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(def ^:dynamic *project* nil)
(def ^:dynamic *calls* nil)

(defn- git [& args]
  (apply p/shell {:dir *project* :out :string :err :string} "git" args))

(defn- numbered [n]
  (str (str/join (for [i (range 1 (inc n))] (str "// line " i "\n")))))

(defn- project-file [path]
  (str (fs/path *project* path)))

(defn- write! [path content]
  (fs/create-dirs (fs/parent (project-file path)))
  (spit (project-file path) content))

(defn- write-package! [package]
  (write! "package.json" (str (json/generate-string package))))

(defn- fixture-for-project [name]
  (str/replace (slurp (str (fs/path fixtures name))) "/work" *project*))

(defn- scratch-project [f]
  (let [dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "sfjs-cli"})))]
    (try
      (binding [*project* dir
                *calls* (atom [])]
        (git "init" "--quiet" "--initial-branch=main")
        (git "config" "user.email" "test@example.com")
        (git "config" "user.name" "Test")
        (git "config" "commit.gpgsign" "false")
        (write! "src/ledger.ts" (numbered 30))
        (write! "src/other.js" (numbered 30))
        (git "add" ".")
        (git "commit" "--quiet" "-m" "base")
        (git "switch" "--quiet" "-c" "swarm/task")
        (write-package! {"devDependencies" {"vitest" "*"}})
        (write! "package-lock.json" "")
        (f))
      (finally (fs/delete-tree dir)))))

(use-fixtures :each scratch-project)

(defn- change-ledger! []
  (write! "src/ledger.ts" (str/replace (numbered 30) "// line 11\n" "// rewritten\n"))
  (git "add" ".")
  (git "commit" "--quiet" "-m" "change ledger"))

(defn- tool-line [line]
  (str/join " " line))

(defn- answer
  "What `tool` answers for a command line, as an exit status and stdout; a
  bare number is an exit status with no output."
  [tool command args]
  (let [line (vec (cons command args))
        answered (tool line)]
    (swap! *calls* conj line)
    (if (map? answered) answered {:exit answered :out ""})))

(defn- run-sf-js
  "Runs `argv` in the scratch project; `tool` answers each tool command line
  with an exit status, or with {:exit :out} for a tool whose stdout sf-js
  reads. Returns the exit status, the printed text, the error text and the
  tool command lines."
  [tool & argv]
  (reset! *calls* [])
  (binding [shell/*dir* *project*]
    (with-redefs [shell/run (fn [command args] (:exit (answer tool command args)))
                  shell/capture (fn [command args] (answer tool command args))
                  shell/available? (constantly true)]
      (let [out (java.io.StringWriter.)
            err (java.io.StringWriter.)
            status (binding [*out* out *err* err] (cli/main (vec argv)))]
        {:status status :out (str out) :err (str err) :calls (mapv tool-line @*calls*)}))))

(defn- usage-failure [tool & argv]
  (try (apply run-sf-js tool argv)
       nil
       (catch Exception e (ex-message e))))

(defn- ok [_] 0)

(defn- fails-tool [name]
  (fn [line] (if (some #{name} line) 1 0)))

(deftest help-and-unknown-commands
  (is (str/starts-with? (:out (run-sf-js ok)) "usage: sf-js"))
  (is (= 0 (:status (run-sf-js ok "--help"))))
  (is (str/includes? (usage-failure ok "frobnicate") "unknown command 'frobnicate'")))

(deftest test-runs-the-detected-runner
  (let [result (run-sf-js ok "test" "--reporter=dot")]
    (is (= 0 (:status result)))
    (is (= ["npx --no-install vitest run --exclude **/*.property.test.* --reporter=dot"] (:calls result))))
  (is (= 1 (:status (run-sf-js (fails-tool "vitest") "test")))))

(deftest test-typechecks-first-when-tsconfig-exists
  (write! "tsconfig.json" "{}")
  (let [result (run-sf-js ok "test")]
    (is (= ["npx --no-install tsc --noEmit" "npx --no-install vitest run --exclude **/*.property.test.*"]
           (:calls result))))
  (let [result (run-sf-js (fails-tool "tsc") "test")]
    (is (= 1 (:status result)))
    (is (= ["npx --no-install tsc --noEmit"] (:calls result)))))

(deftest test-needs-a-runner-and-a-project
  (write-package! {})
  (is (str/includes? (usage-failure ok "test") "no test runner"))
  (fs/delete (project-file "package.json"))
  (is (str/includes? (usage-failure ok "test") "no package.json")))

(deftest property-runs-only-property-tests
  (is (= ["npx --no-install vitest run .property.test."] (:calls (run-sf-js ok "property"))))
  (is (= 1 (:status (run-sf-js (fails-tool "vitest") "property")))))

(defn- writes-coverage [line]
  (when (some #{"--coverage.enabled"} line)
    (write! "tmp/coverage/coverage-final.json" "{}"))
  0)

(deftest coverage-reports-where-the-istanbul-file-landed
  (let [result (run-sf-js writes-coverage "coverage")]
    (is (= 0 (:status result)))
    (is (str/includes? (:out result) "coverage written to tmp/coverage/coverage-final.json")))
  (is (= 1 (:status (run-sf-js (fails-tool "vitest") "coverage"))))
  (is (str/includes? (usage-failure ok "coverage") "no tmp/coverage/coverage-final.json after the coverage run")))

(defn- crap-tool [line]
  (when (some #{"eslint"} line)
    (write! "tmp/eslint/eslint.json" (fixture-for-project "eslint-report.json")))
  0)

(defn- write-coverage! []
  (write! "tmp/coverage/coverage-final.json" (fixture-for-project "coverage-final.json")))

(defn- use-eslint! []
  (write-package! {"devDependencies" {"vitest" "*" "eslint" "*"}}))

(deftest crap-lists-changed-functions-and-fails-over-the-limit
  (use-eslint!)
  (change-ledger!)
  (write-coverage!)
  (let [result (run-sf-js crap-tool "crap")]
    (is (= 1 (:status result)))
    (is (str/includes? (:out result) "OVER crap"))
    (is (str/includes? (:out result) "src/ledger.ts:10 withdraw"))
    (is (str/includes? (str/join " " (:calls result)) "src/ledger.ts"))
    (is (str/includes? (slurp (project-file "tmp/eslint/eslint.config.mjs")) "complexity"))))

(deftest crap-all-scores-every-function
  (use-eslint!)
  (write-coverage!)
  (let [result (run-sf-js crap-tool "crap" "--all")]
    (is (= 1 (:status result)))
    (is (= ["npx --no-install eslint --config tmp/eslint/eslint.config.mjs --no-inline-config --format json --output-file tmp/eslint/eslint.json ."]
           (:calls result)))))

(deftest crap-with-nothing-changed-runs-no-eslint
  (use-eslint!)
  (write-coverage!)
  (git "add" ".")
  (git "commit" "--quiet" "-m" "no source change")
  (let [result (run-sf-js crap-tool "crap")]
    (is (= 0 (:status result)))
    (is (= [] (:calls result)))))

(deftest crap-reads-coverage-from-the-report-directory
  (use-eslint!)
  (change-ledger!)
  (write! "elsewhere/coverage-final.json" (fixture-for-project "coverage-final.json"))
  (is (= 1 (:status (run-sf-js crap-tool "crap" "--report" "elsewhere"))))
  (is (str/includes? (usage-failure crap-tool "crap") "run sf-js coverage first"))
  (is (str/includes? (usage-failure crap-tool "crap" "--report" "nowhere") "no coverage-final.json under nowhere")))

(defn- eslint-reporting [messages status]
  (fn [line]
    (when (some #{"eslint"} line)
      (write! "tmp/eslint/eslint.json"
              (json/generate-string [{"filePath" (project-file "src/ledger.ts") "messages" messages}])))
    status))

(deftest crap-fails-when-eslint-could-not-analyze-a-file
  (use-eslint!)
  (change-ledger!)
  (write-coverage!)
  (let [tool (eslint-reporting [{"fatal" true "severity" 2 "line" 3 "message" "Parsing error: Unexpected token )"}
                                {"fatal" true "severity" 2 "line" 9 "message" "Parsing error: ';' expected."}]
                               1)
        message (usage-failure tool "crap")]
    (is (= (str "eslint could not analyze src/ledger.ts:3: Parsing error: Unexpected token )\n"
                "eslint could not analyze src/ledger.ts:9: Parsing error: ';' expected.")
           message))))

(deftest crap-fails-when-eslint-exits-2
  (use-eslint!)
  (change-ledger!)
  (write-coverage!)
  (is (= "eslint exited 2" (usage-failure (eslint-reporting [] 2) "crap"))))

(deftest crap-accepts-eslint-exit-1-from-complexity-messages
  (use-eslint!)
  (change-ledger!)
  (write-coverage!)
  (let [tool (eslint-reporting [{"ruleId" "complexity" "severity" 2 "line" 10 "column" 8
                                 "message" "Function 'withdraw' has a complexity of 1. Maximum allowed is 0."}]
                               1)]
    (is (= 0 (:status (run-sf-js tool "crap"))))))

(deftest crap-fails-when-eslint-writes-no-report
  (use-eslint!)
  (write-coverage!)
  (is (str/includes? (usage-failure ok "crap" "--all") "eslint exited 0 without tmp/eslint/eslint.json")))

(defn- oxlint-report [& diagnostics]
  (json/generate-string {"diagnostics" (vec diagnostics) "number_of_rules" 1}))

(defn- oxlint-complexity [file line message]
  {"code" "eslint(complexity)" "message" message "filename" file
   "labels" [{"span" {"offset" 0 "length" 1 "line" line "column" 1}}]})

(def ^:private withdraw-report
  (oxlint-report (oxlint-complexity "src/ledger.ts" 10 "function `withdraw` has a complexity of 4. Maximum allowed is 0.")))

(defn- oxlint-tool
  "A fake oxlint that prints `stdout` and exits `status`; other tools exit 0."
  ([stdout] (oxlint-tool 0 stdout))
  ([status stdout]
   (fn [line] (if (some #{"oxlint"} line) {:exit status :out stdout} 0))))

(deftest crap-takes-complexity-from-oxlint-by-default
  (change-ledger!)
  (write-coverage!)
  (let [result (run-sf-js (oxlint-tool withdraw-report) "crap")
        [call] (:calls result)]
    (is (= 1 (:status result)))
    (is (str/includes? (:out result) "OVER crap   10.8  cc   4  cov  25%  src/ledger.ts:10 withdraw"))
    (is (str/starts-with? call "npx --no-install oxlint --config tmp/oxlint/oxlintrc.json --disable-nested-config --format json"))
    (is (str/ends-with? call " src/ledger.ts"))
    (is (= 1 (count (:calls result))))
    (is (not (str/includes? (:out result) "diagnostics")))
    (is (str/includes? (slurp (project-file "tmp/oxlint/oxlintrc.json")) "eslint/complexity"))))

(deftest crap-all-runs-oxlint-over-the-project
  (write-coverage!)
  (let [result (run-sf-js (oxlint-tool withdraw-report) "crap" "--all")]
    (is (= 1 (:status result)))
    (is (str/ends-with? (first (:calls result)) " ."))))

(deftest crap-uses-oxlint-when-both-linters-are-listed
  (write-package! {"devDependencies" {"vitest" "*" "eslint" "*" "oxlint" "*"}})
  (change-ledger!)
  (write-coverage!)
  (let [result (run-sf-js (oxlint-tool withdraw-report) "crap")]
    (is (= 1 (:status result)))
    (is (not-any? #(str/includes? % "eslint ") (:calls result)))))

(deftest crap-fails-when-oxlint-prints-no-report
  (change-ledger!)
  (write-coverage!)
  (is (= "oxlint exited 1 without a report; sf-js needs oxlint 1.62.0 or later"
         (usage-failure (oxlint-tool 1 "Failed to parse oxlint configuration file.\n") "crap")))
  (is (= "oxlint ran no complexity rule; sf-js needs oxlint 1.62.0 or later"
         (usage-failure (oxlint-tool (json/generate-string {"diagnostics" [] "number_of_rules" 0})) "crap"))))

(deftest crap-shows-what-oxlint-printed-instead-of-a-report
  (change-ledger!)
  (write-coverage!)
  (let [err (java.io.StringWriter.)]
    (binding [*err* err]
      (try (with-redefs [shell/available? (constantly true)]
             (binding [shell/*dir* *project*]
               (with-redefs [shell/capture (fn [_ _] {:exit 1 :out "unknown field `options`\n"})]
                 (with-out-str (cli/main ["crap"])))))
           (catch Exception _)))
    (is (= "unknown field `options`\n" (str err)))))

(defn- change-ledger-line! [n text]
  (let [lines (str/split-lines (numbered 30))]
    (write! "src/ledger.ts" (str (str/join "\n" (assoc lines (dec n) text)) "\n"))
    (git "add" ".")
    (git "commit" "--quiet" "-m" "directive")))

(def ^:private directive-warning
  "sf-js: warning: src/ledger.ts:11 has an oxlint-disable directive that can hide a function from CRAP")

(deftest crap-warns-about-oxlint-directives-that-can-hide-complexity
  (change-ledger-line! 11 "// oxlint-disable-next-line complexity")
  (write-coverage!)
  (let [result (run-sf-js (oxlint-tool withdraw-report) "crap")]
    (is (= 1 (:status result)))
    (is (= (str directive-warning "\n") (:err result))))
  (testing "over every linted source with --all"
    (write! "src/new.js" "/* oxlint-disable */\n")
    (write! "src/new.test.js" "/* oxlint-disable */\n")
    (let [result (run-sf-js (oxlint-tool withdraw-report) "crap" "--all")]
      (is (= [directive-warning
              "sf-js: warning: src/new.js:1 has an oxlint-disable directive that can hide a function from CRAP"]
             (str/split-lines (:err result)))))))

(deftest crap-does-not-warn-about-directives-with-eslint
  (use-eslint!)
  (change-ledger-line! 11 "// oxlint-disable-next-line complexity")
  (write-coverage!)
  (is (= "" (:err (run-sf-js crap-tool "crap")))))

(deftest crap-does-not-warn-about-other-directives
  (change-ledger-line! 11 "// oxlint-disable-next-line no-debugger")
  (write-coverage!)
  (is (= "" (:err (run-sf-js (oxlint-tool withdraw-report) "crap")))))

(defn- jscpd-tool [line]
  (when-not (some #{"--version"} line)
    (write! "tmp/jscpd/jscpd-report.json"
            (json/generate-string
             {"duplicates" [{"tokens" 64 "lines" 3
                             "firstFile" {"name" "src/ledger.ts" "start" 10 "end" 12}
                             "secondFile" {"name" "src/other.js" "start" 20 "end" 22}}]})))
  0)

(deftest dry-lists-clones-touching-changed-lines
  (change-ledger!)
  (let [result (run-sf-js jscpd-tool "dry" "--min-tokens" "30")]
    (is (= 1 (:status result)))
    (is (str/includes? (:out result) "64 tokens  src/ledger.ts:10-12 (changed)  ==  src/other.js:20-22"))
    (is (str/includes? (first (:calls result)) "--min-tokens 30")))
  (is (= 1 (:status (run-sf-js jscpd-tool "dry" "--all"))))
  (is (= 0 (:status (run-sf-js ok "dry" "--report-only" "--base" "swarm/task")))))

(deftest dry-needs-a-report
  (is (str/includes? (usage-failure ok "dry" "--report-only") "tmp/jscpd/jscpd-report.json not found"))
  (is (str/includes? (usage-failure (fails-tool "npx") "dry" "--all") "jscpd exited 1 without")))

(deftest dry-does-not-review-a-stale-report
  (jscpd-tool ["npx"])
  (is (str/includes? (usage-failure (fails-tool "npx") "dry" "--all") "jscpd exited 1 without")))

(defn- stryker-tool [line]
  (when (some #{"stryker"} line)
    (write! "tmp/stryker/mutation.json" (slurp (str (fs/path fixtures "mutation.json")))))
  0)

(deftest mutate-passes-changed-sources-and-reports-survivors
  (change-ledger!)
  (let [result (run-sf-js stryker-tool "mutate" "--" "--concurrency" "2")]
    (is (= 1 (:status result)))
    (is (str/includes? (first (:calls result)) "--mutate src/ledger.ts"))
    (is (str/includes? (first (:calls result)) "--concurrency 2"))
    (is (str/includes? (:out result) "Survived   src/ledger.ts:14"))
    (is (str/includes? (slurp (project-file "tmp/stryker/stryker.config.mjs")) "jsonReporter"))))

(deftest mutate-all-leaves-files-to-stryker
  (let [result (run-sf-js stryker-tool "mutate" "--all")]
    (is (= 1 (:status result)))
    (is (not (str/includes? (first (:calls result)) "--mutate")))))

(deftest mutate-with-nothing-changed-skips-stryker
  (let [result (run-sf-js stryker-tool "mutate")]
    (is (= 0 (:status result)))
    (is (= [] (:calls result)))
    (is (str/includes? (:out result) "no changed source files to mutate"))))

(deftest mutate-report-only-rereads-the-last-report
  (change-ledger!)
  (write! "tmp/stryker/mutation.json" (slurp (str (fs/path fixtures "mutation.json"))))
  (is (= 1 (:status (run-sf-js ok "mutate" "--report-only"))))
  (fs/delete (project-file "tmp/stryker/mutation.json"))
  (is (str/includes? (usage-failure ok "mutate" "--report-only") "tmp/stryker/mutation.json not found")))

(defn- other-file-survivor [mutation]
  (assoc-in mutation ["files" "src/other.js"]
            {"language" "javascript" "source" ""
             "mutants" [{"id" "9" "mutatorName" "BooleanLiteral" "replacement" "true" "status" "Survived"
                         "location" {"start" {"line" 5 "column" 1} "end" {"line" 5 "column" 2}}}]}))

(deftest mutate-judges-only-changed-files-without-all
  (write! "src/ledger.ts" (str (numbered 30) "// added\n"))
  (git "add" ".")
  (git "commit" "--quiet" "-m" "extend ledger")
  (let [killed-only (update-in (json/parse-string (slurp (str (fs/path fixtures "mutation.json"))))
                               ["files" "src/ledger.ts" "mutants"]
                               (fn [mutants] (filterv #(= "Killed" (get % "status")) mutants)))
        report (json/generate-string (other-file-survivor killed-only))
        tool (fn [line] (when (some #{"stryker"} line) (write! "tmp/stryker/mutation.json" report)) 0)]
    (doseq [argv [["mutate"] ["mutate" "--report-only"]]]
      (let [result (apply run-sf-js tool argv)]
        (is (= 0 (:status result)) (pr-str argv))
        (is (not (str/includes? (:out result) "src/other.js")) (pr-str argv))
        (is (str/includes? (:out result) "mutation score 100.0% (Killed 1)") (pr-str argv))))
    (doseq [argv [["mutate" "--all"] ["mutate" "--report-only" "--all"]]]
      (let [result (apply run-sf-js tool argv)]
        (is (= 1 (:status result)) (pr-str argv))
        (is (str/includes? (:out result) "Survived   src/other.js:5  BooleanLiteral  true") (pr-str argv))))))

(deftest mutate-refuses-file-names-with-commas
  (write! "src/a,b.ts" (numbered 3))
  (write! "src/c.ts" (numbered 3))
  (git "add" ".")
  (git "commit" "--quiet" "-m" "comma")
  (let [message (usage-failure stryker-tool "mutate")]
    (is (= "cannot mutate src/a,b.ts: Stryker splits --mutate on commas" message))
    (is (= [] @*calls*))
    (is (not (fs/exists? (project-file "tmp/stryker/stryker.config.mjs"))))))

(deftest mutate-fails-when-stryker-writes-no-report
  (is (str/includes? (usage-failure ok "mutate" "--all") "stryker exited 0 without")))

(deftest tools-adds-missing-packages-and-checks-jscpd
  (let [result (run-sf-js ok "tools")]
    (is (= 0 (:status result)))
    (is (str/starts-with? (first (:calls result)) "npm install --save-dev "))
    (is (= "npx --yes jscpd@4 --version" (last (:calls result)))))
  (is (= "npm install --save-dev failed" (usage-failure (fails-tool "npm") "tools")))
  (is (str/includes? (usage-failure (fails-tool "jscpd@4") "tools") "npx --yes jscpd@4 failed")))

(deftest tools-notes-when-everything-is-installed
  (let [package {"devDependencies" (zipmap ["vitest" "typescript" "eslint" "typescript-eslint" "fast-check"
                                            "@stryker-mutator/core" "@stryker-mutator/vitest-runner"
                                            "@vitest/coverage-v8"]
                                           (repeat "*"))}]
    (write-package! package)
    (let [result (run-sf-js ok "tools")]
      (is (= 0 (:status result)))
      (is (str/includes? (:out result) "all JavaScript tools present"))
      (is (= ["npx --yes jscpd@4 --version"] (:calls result))))))

(deftest tools-reports-commands-missing-from-path
  (binding [shell/*dir* *project*]
    (with-redefs [shell/available? (constantly false)]
      (is (thrown-with-msg? Exception #"missing on PATH: node, npx, npm" (cli/main ["tools"]))))))

(defn- failing-jscpd [line]
  (when-not (some #{"--version"} line)
    (write! "tmp/jscpd/jscpd-report.json" (json/generate-string {"duplicates" []})))
  1)

(deftest dry-reads-the-report-when-jscpd-exits-nonzero
  (let [result (run-sf-js failing-jscpd "dry" "--all")]
    (is (= 0 (:status result)))
    (is (str/includes? (:out result) "0 of 0 clone(s)"))))

(deftest dry-over-everything-needs-no-git
  (fs/delete-tree (project-file ".git"))
  (is (= 1 (:status (run-sf-js jscpd-tool "dry" "--all")))))

(deftest extra-arguments-follow-the-double-dash
  (let [result (run-sf-js stryker-tool "mutate" "--all" "--" "--concurrency" "2" "--logLevel" "debug")]
    (is (str/ends-with? (first (:calls result)) "--concurrency 2 --logLevel debug"))
    (is (not (str/includes? (first (:calls result)) "-- --concurrency")))))

(deftest help-after-a-command-reaches-the-runner
  (is (= ["npx --no-install vitest run --exclude **/*.property.test.* --help"]
         (:calls (run-sf-js ok "test" "--help")))))
