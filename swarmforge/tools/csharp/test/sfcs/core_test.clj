(ns sfcs.core-test
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sfcore.changes :as changes]
            [sfcore.crap :as crap]
            [sfcore.shell :as shell]
            [sfcs.cli :as cli]
            [sfcs.cobertura :as cob]
            [sfcore.jscpd :as jscpd]
            [sfcore.stryker :as stryker]))

(def here (str (fs/parent (fs/parent (fs/parent *file*)))))
(def fixtures (str (fs/path here "fixtures")))
(defn fixture [& parts] (slurp (str (apply fs/path fixtures parts))))

(deftest diff-parsing
  (let [diff (str/join "\n" ["diff --git a/Bank/Ledger.cs b/Bank/Ledger.cs"
                             "--- a/Bank/Ledger.cs"
                             "+++ b/Bank/Ledger.cs"
                             "@@ -7 +7 @@"
                             "-            total += amount;"
                             "+            total = total + amount;"
                             "@@ -12,2 +12,0 @@"
                             "-old"
                             "-older"
                             "@@ -30,0 +29,2 @@"
                             "+new one"
                             "+new two"
                             "diff --git a/Bank/Gone.cs b/Bank/Gone.cs"
                             "--- a/Bank/Gone.cs"
                             "+++ /dev/null"
                             "@@ -1 +0,0 @@"
                             "-bye"])
        changed (changes/changed-lines-from-diff diff)]
    (is (= ["Bank/Ledger.cs"] (keys changed)))
    (is (= [7 13 29 30] (vec (get changed "Bank/Ledger.cs"))) "the deletion at 12 changes line 13 after it")))

(deftest path-matching
  (let [changed {"Bank/Ledger.cs" (sorted-set 7)}]
    (is (= "Bank/Ledger.cs" (changes/to-changed-path "Bank/Ledger.cs" changed "/repo")))
    (is (= "Bank/Ledger.cs" (changes/to-changed-path "/repo/Bank/Ledger.cs" changed "/repo")))
    (is (= "Bank/Ledger.cs" (changes/to-changed-path "/elsewhere/Bank/Ledger.cs" changed "/repo")))
    (is (= "Bank/Ledger.cs" (changes/to-changed-path "C:\\w\\Bank\\Ledger.cs" changed "/repo")))
    (is (nil? (changes/to-changed-path "/repo/Other/Ledger.cs" changed "/repo")))
    (is (true? (changes/overlaps? "Bank/Ledger.cs" 5 7 changed)))
    (is (false? (changes/overlaps? "Bank/Ledger.cs" 8 9 changed)))))

(deftest crap-formula
  (is (= 1.0 (crap/crap-score 1 1)))
  (is (= 20.0 (crap/crap-score 4 0)))
  (is (= "10.750" (crap/fmt "%.3f" (crap/crap-score 4 0.25)))))

(deftest cobertura-methods
  (let [methods (cob/parse-cobertura (fixture "coverage" "coverage.cobertura.xml"))]
    (is (= [["Bank.Ledger" "Deposit" "Bank/Ledger.cs" 1.0 1.0 6 8]
            ["Bank.Ledger" "Withdraw" "Bank/Ledger.cs" 4.0 0.25 13 17]
            ["Bank.Ledger" "Balance" "Bank/Ledger.cs" 1.0 1.0 20 22]
            ["Bank.Audit<T>" "Record" "Bank/Audit.cs" 2.0 0.0 4 5]]
           (map (juxt :class-name :name :file :complexity :coverage :first-line :last-line) methods)))))

(defn- cobertura-xml [sources filename]
  (str "<coverage><sources>" (apply str (map #(str "<source>" % "</source>") sources)) "</sources>"
       "<packages><package><classes><class name=\"A.B\" filename=\"" filename "\"><methods>"
       "<method name=\"M\" signature=\"(System.Int32)\" line-rate=\"1\" complexity=\"2\">"
       "<lines><line number=\"3\" hits=\"1\"/></lines></method>"
       "</methods></class></classes></package></packages></coverage>"))

(deftest cobertura-file-resolution
  (let [base (str (fs/create-temp-dir {:prefix "sfcs-src"}))]
    (try
      (fs/create-dirs (fs/path base "Bank"))
      (spit (str (fs/path base "Bank" "L.cs")) "x")
      (spit (str (fs/path base "C:\\w\\L.cs")) "x")
      (let [file-of (fn [sources filename] (:file (first (cob/parse-cobertura (cobertura-xml sources filename)))))]
        (is (= (str (fs/path base "Bank" "L.cs")) (file-of ["/nonexistent" base] "Bank/L.cs")))
        (is (= "Bank/Missing.cs" (file-of ["/nonexistent" base] "Bank/Missing.cs")))
        (is (= "/abs/L.cs" (file-of [base] "/abs/L.cs")))
        (is (= "C:\\w\\L.cs" (file-of [base] "C:\\w\\L.cs")))
        (is (= "" (file-of [base] ""))))
      (finally (fs/delete-tree base)))))

(deftest cobertura-signatures
  (is (= ["(System.Int32)" 3 3]
         ((juxt :signature :first-line :last-line) (first (cob/parse-cobertura (cobertura-xml [] "A.cs")))))))

(deftest cobertura-after-coverlet-byte-order-mark
  (is (= (cob/parse-cobertura (cobertura-xml [] "A.vb"))
         (cob/parse-cobertura (str "\uFEFF<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" (cobertura-xml [] "A.vb"))))))

(deftest cobertura-files-newest-first
  (let [dir (fs/create-temp-dir {:prefix "sfcs-cov"})
        write (fn [path millis]
                (let [f (fs/path dir path)]
                  (fs/create-dirs (fs/parent f))
                  (spit (str f) "x")
                  (fs/set-last-modified-time f (java.nio.file.attribute.FileTime/fromMillis millis))
                  (str f)))]
    (try
      (let [old (write "run1/coverage.cobertura.xml" 1000000)
            newer (write "run2/coverage.cobertura.xml" 2000000)]
        (write "run2/other.xml" 3000000)
        (is (= [newer old] (cob/find-cobertura-files (str dir)))))
      (is (= [] (cob/find-cobertura-files (str (fs/path dir "missing")))))
      (finally (fs/delete-tree dir)))))

(deftest method-rows-name-the-signature-line-when-known
  (let [method {:class-name "A.B" :name "M" :first-line 7 :crap 1.0 :complexity 1.0 :coverage 1.0 :file "a.cs"}]
    (is (= "ok   crap    1.0  cc   1  cov 100%  a.cs:7 B.M" (cob/format-method method)))
    (is (= "ok   crap    1.0  cc   1  cov 100%  a.cs:5 B.M" (cob/format-method (assoc method :signature-line 5))))))

(deftest signature-start
  (let [source (str/split-lines (fixture "Ledger.cs.txt"))]
    (is (= 10 (cob/signature-start source 13)))
    (is (= 5 (cob/signature-start source 6)))
    (is (= 19 (cob/signature-start source 20)))))

(deftest jscpd-clones
  (let [clones (jscpd/parse-clones (json/parse-string (fixture "jscpd-report.json")))
        changed {"Bank/Ledger.cs" (sorted-set 14) "Bank/Audit.cs" (sorted-set 4)}
        touching (jscpd/clones-touching-changes clones changed "/nonexistent/checkout")]
    (is (= [["Bank/Ledger.cs*" "Bank/Legacy.cs"]
            ["Bank/Audit.cs*" "Bank\\Older.cs"]]
           (map (fn [c] (map #(str (:file %) (when (:changed %) "*")) (:sides c))) touching)))
    (is (= [] (jscpd/clones-touching-changes clones {"Bank/Ledger.cs" (sorted-set 1)} "/r")))))

(deftest stryker-survivors
  (let [mutants (stryker/parse-mutants (json/parse-string (fixture "mutation-report.json")))
        {:keys [counts score]} (stryker/summarize mutants)]
    (is (= [["Bank/Ledger.cs" 14 "Survived"] ["Bank/Ledger.cs" 14 "NoCoverage"]]
           (map (juxt :file :line :status) (stryker/survivors mutants))))
    (is (= {"Timeout" 1 "Killed" 1 "Survived" 1 "NoCoverage" 1 "Ignored" 1} counts))
    (is (= 50.0 score))))

;; Command line, in a scratch task worktree with a fake dotnet on PATH.

(def ^:dynamic *repo* nil)
(def ^:dynamic *bin* nil)

(defn- git [& args]
  (apply p/shell {:dir *repo* :out :string :err :string} "git" args))

(defn- fake-run
  "shell/run against the fake tools in *bin*, with `env` for the fakes."
  [env]
  (fn [command args]
    (println (str "$ " (str/join " " (cons command args))))
    (:exit (apply p/shell {:dir *repo* :out :string :err :string :continue true :extra-env env}
                  (str (fs/path *bin* command)) args))))

(defn- sfcs
  "Runs sf-cs in-process in the scratch repository. `env` feeds the fakes;
  MISSING lists commands to report as absent from PATH."
  [env & args]
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        missing (set (str/split (get env "MISSING" "") #","))
        status (binding [shell/*dir* *repo* *out* out *err* err]
                 (with-redefs [shell/run (fake-run env)
                               shell/available? (complement missing)]
                   (try (if-let [language (get env "LANGUAGE")]
                          (cli/main (keyword language) (vec args))
                          (cli/main (vec args)))
                        (catch Exception e
                          (binding [*out* err] (println (str "sf-cs: " (ex-message e))))
                          2))))]
    {:status status :lines (vec (str/split-lines (str/trimr (str out)))) :err (str err)}))

(def wrappers {"fsharp" "sf-fs" "vbnet" "sf-vb"})

(defn- sfcs-process [env & args]
  (let [result (apply p/shell {:dir *repo* :out :string :err :string :continue true
                               :extra-env (merge {"PATH" (str *bin* fs/path-separator (System/getenv "PATH"))} env)}
                      "bash" (str (fs/path here ".." ".." "bin" (get wrappers (get env "LANGUAGE") "sf-cs"))) args)]
    {:status (:exit result) :lines (vec (str/split-lines (str/trimr (:out result)))) :err (:err result)}))

(defn- seed-repo [f]
  (let [repo (str (fs/create-temp-dir {:prefix "sfcs"}))
        bin (str (fs/path repo "fakebin"))]
    (fs/create-dirs bin)
    (spit (str (fs/path bin "dotnet"))
          (str "#!/usr/bin/env bash\n"
               "[ -n \"$FAKE_DOTNET_LOG\" ] && printf '%s\\n' \"$@\" > \"$FAKE_DOTNET_LOG\"\n"
               "if [ \"$1 $2\" = \"$FAKE_FAIL_STEP\" ]; then exit 7; fi\n"
               "if [ \"$1\" = test ] && [ -n \"$FAKE_COVERAGE\" ]; then mkdir -p tmp/coverage/run && cp \"$FAKE_COVERAGE\" tmp/coverage/run/coverage.cobertura.xml; fi\n"
               "for pair in ${FAKE_PROJECT_EXITS//,/ }; do if [ \"$2\" = \"${pair%%=*}\" ]; then exit \"${pair#*=}\"; fi; done\n"
               "if [ \"$1\" = stryker ] && [ -n \"$FAKE_REPORT\" ]; then mkdir -p tmp/stryker/reports && cp \"$FAKE_REPORT\" tmp/stryker/reports/mutation-report.json; fi\n"
               "if [ \"$1\" = new ]; then mkdir -p .config && echo '{\"tools\":{}}' > .config/dotnet-tools.json; fi\n"
               "exit ${FAKE_EXIT:-0}\n"))
    (doseq [tool ["node" "npx"]]
      (spit (str (fs/path bin tool))
            (str "#!/usr/bin/env bash\n"
                 "if [ -n \"$FAKE_JSCPD_REPORT\" ]; then mkdir -p tmp/jscpd && cp \"$FAKE_JSCPD_REPORT\" tmp/jscpd/jscpd-report.json; fi\n"
                 "exit ${FAKE_EXIT:-0}\n"))
      (fs/set-posix-file-permissions (fs/path bin tool) "rwxr-xr-x"))
    (fs/set-posix-file-permissions (fs/path bin "dotnet") "rwxr-xr-x")
    (binding [*repo* repo *bin* bin]
      (git "init" "--quiet" "--initial-branch=main")
      (git "config" "user.email" "test@example.com")
      (git "config" "user.name" "Test")
      (fs/create-dirs (fs/path repo "Bank"))
      (spit (str (fs/path repo "Bank" "Ledger.cs")) (fixture "Ledger.cs.txt"))
      (spit (str (fs/path repo "Bank" "Legacy.cs")) "namespace Bank { }\n")
      (git "add" ".")
      (git "commit" "--quiet" "-m" "base")
      (git "switch" "--quiet" "-c" "swarm/abc1234")
      (spit (str (fs/path repo "Bank" "Ledger.cs"))
            (str/replace (fixture "Ledger.cs.txt") "if (amount > total" "if (amount >= total"))
      (git "commit" "--quiet" "-am" "task work")
      (spit (str (fs/path repo "Bank" "Audit.cs"))
            "namespace Bank\n{\n    public class Audit<T> { public void Record(T item) { if (item == null) return; } }\n}\n")
      (try (f) (finally (fs/delete-tree repo))))))

(use-fixtures :once seed-repo)

(deftest crap-gate
  (testing "only changed methods, failing over the limit"
    (let [{:keys [status lines err]} (sfcs {} "crap" "--base" "main" "--report" (str (fs/path fixtures "coverage")))]
      (is (= 1 status) err)
      (is (= ["OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw"
              "ok   crap    6.0  cc   2  cov   0%  Bank/Audit.cs:4 Audit<T>.Record"
              "2 changed method(s), 1 over CRAP 10"]
             lines))))
  (testing "--all reports every method"
    (let [{:keys [status lines err]} (sfcs {} "crap" "--all" "--report" (str (fs/path fixtures "coverage")))]
      (is (= 1 status) err)
      (is (= "4 method(s), 1 over CRAP 10" (last lines))))))

(deftest dry-report-only
  (fs/create-dirs (fs/path *repo* "tmp" "jscpd"))
  (fs/copy (fs/path fixtures "jscpd-report.json") (fs/path *repo* "tmp" "jscpd" "jscpd-report.json") {:replace-existing true})
  (let [{:keys [status lines err]} (sfcs {} "dry" "--report-only" "--base" "main")]
    (is (= 1 status) err)
    (is (= ["64 tokens  Bank/Ledger.cs:13-15 (changed)  ==  Bank/Legacy.cs:30-32"
            "52 tokens  Bank/Audit.cs:4-5 (changed)  ==  Bank\\Older.cs:9-10"
            "2 of 3 clone(s) touch changed lines"]
           lines))))

(deftest mutate-report-only
  (fs/create-dirs (fs/path *repo* "tmp" "stryker" "reports"))
  (fs/copy (fs/path fixtures "mutation-report.json") (fs/path *repo* "tmp" "stryker" "reports" "mutation-report.json") {:replace-existing true})
  (let [{:keys [status lines err]} (sfcs {} "mutate" "--report-only")]
    (is (= 1 status) err)
    (is (= "Survived   Bank/Ledger.cs:14  Equality mutation  > replaced with >=" (nth lines 0)))
    (is (= "NoCoverage Bank/Ledger.cs:14  Boolean mutation  true" (nth lines 1)))
    (is (str/starts-with? (nth lines 2) "mutation score 50.0% ("))))

(deftest dotnet-command-lines
  (let [log (str (fs/path *repo* "dotnet.log"))
        calls (fn [env & args]
                (let [result (apply sfcs (assoc env "FAKE_DOTNET_LOG" log) args)]
                  (assoc result :args (str/split-lines (str/trimr (slurp log))))))]
    (is (= ["test" "--filter" "Category!=Property" "--no-build"] (:args (calls {} "test" "--no-build"))))
    (is (= ["test" "--filter" "Category=Property"] (:args (calls {} "property"))))
    (is (= ["stryker" "--reporter" "json" "--reporter" "progress" "--output" "tmp/stryker" "--since:main" "--project" "Bank.csproj"]
           (:args (calls {} "mutate" "--base" "main" "--" "--project" "Bank.csproj"))))
    (let [coverage (calls {"FAKE_COVERAGE" (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))} "coverage")]
      (is (= 0 (:status coverage)) (:err coverage))
      (is (= ["test" "--filter" "Category!=Property" "--collect" "XPlat Code Coverage" "--results-directory" "tmp/coverage"]
             (take 7 (:args coverage))))
      (is (fs/exists? (fs/path *repo* "tmp" "coverage" "run" "coverage.cobertura.xml"))))))

(deftest usage-errors
  (is (= 2 (:status (sfcs {} "nope"))))
  (is (= 2 (:status (sfcs {} "crap" "--report" (str (fs/path *repo* "missing")))))))

(deftest path-and-ref-options-stay-strings
  (testing "a report directory named like a boolean is still a directory"
    (let [{:keys [status err]} (sfcs {} "crap" "--all" "--report" "false")]
      (is (= 2 status))
      (is (str/includes? err "no coverage.cobertura.xml under false"))))
  (testing "a base ref named like a number is still a ref"
    (let [{:keys [status err]} (sfcs {} "crap" "--base" "123" "--report" (str (fs/path fixtures "coverage")))]
      (is (= 2 status))
      (is (str/includes? err "123")))))

(deftest tools-needs-dotnet-node-and-npx
  (let [{:keys [status err]} (sfcs {"MISSING" "node,npx"} "tools")]
    (is (= 2 status))
    (is (= "sf-cs: missing on PATH: node, npx\n" err))))

(deftest tools-installs-what-is-missing
  (let [run-tools (fn []
                    (fs/delete-tree (fs/path *repo* ".config"))
                    (sfcs {} "tools"))]
    (testing "no manifest: create it, install Stryker, restore, check jscpd"
      (let [{:keys [status lines err]} (run-tools)]
        (is (= 0 status) err)
        (is (= ["$ dotnet new tool-manifest" "$ dotnet tool install dotnet-stryker"
                "$ dotnet tool restore" "$ npx --yes jscpd@4 --version"]
               lines))))
    (testing "a manifest that lists Stryker is only restored"
      (fs/create-dirs (fs/path *repo* ".config"))
      (spit (str (fs/path *repo* ".config" "dotnet-tools.json")) "{\"tools\":{\"dotnet-stryker\":{}}}")
      (let [{:keys [status lines]} (sfcs {} "tools")]
        (is (= 0 status))
        (is (= ["$ dotnet tool restore" "$ npx --yes jscpd@4 --version"] lines))))
    (testing "a manifest without Stryker installs it"
      (spit (str (fs/path *repo* ".config" "dotnet-tools.json")) "{\"tools\":{}}")
      (is (= ["$ dotnet tool install dotnet-stryker" "$ dotnet tool restore" "$ npx --yes jscpd@4 --version"]
             (:lines (sfcs {} "tools")))))
    (fs/delete-tree (fs/path *repo* ".config"))))

(deftest coverage-passes-on-a-failing-test-run
  (is (= 3 (:status (sfcs {"FAKE_EXIT" "3"} "coverage")))))

(deftest help-without-a-command
  (is (str/starts-with? (first (:lines (sfcs {}))) "usage: sf-cs"))
  (is (= 0 (:status (sfcs {}))))
  (is (= 0 (:status (sfcs {} "help")))))

(deftest dry-runs-jscpd-and-reads-its-report
  (let [report (str (fs/path fixtures "jscpd-report.json"))
        fresh #(fs/delete-tree (fs/path *repo* "tmp" "jscpd"))]
    (testing "a non-zero jscpd exit with a report still reports the clones"
      (fresh)
      (let [{:keys [status lines]} (sfcs {"FAKE_EXIT" "1" "FAKE_JSCPD_REPORT" report} "dry" "--base" "main")]
        (is (= 1 status))
        (is (= "$ npx --yes jscpd@4 --format csharp --min-tokens 50" (subs (first lines) 0 (count "$ npx --yes jscpd@4 --format csharp --min-tokens 50"))))))
    (testing "a non-zero jscpd exit without a report is that exit"
      (fresh)
      (is (= 5 (:status (sfcs {"FAKE_EXIT" "5"} "dry" "--all")))))
    (testing "a clean jscpd exit without a report is an error"
      (fresh)
      (let [{:keys [status err]} (sfcs {} "dry" "--all")]
        (is (= 2 status))
        (is (str/includes? err "jscpd-report.json not found"))))
    (testing "--min-tokens reaches jscpd"
      (fresh)
      (is (str/includes? (first (:lines (sfcs {"FAKE_JSCPD_REPORT" report} "dry" "--all" "--min-tokens" "80")))
                         "--min-tokens 80")))))

(deftest dry-over-everything-needs-no-git
  (let [plain (str (fs/create-temp-dir {:prefix "sfcs-plain"}))]
    (try
      (fs/create-dirs (fs/path plain "tmp" "jscpd"))
      (fs/copy (fs/path fixtures "jscpd-report.json") (fs/path plain "tmp" "jscpd" "jscpd-report.json"))
      (binding [*repo* plain]
        (let [{:keys [status err]} (sfcs {} "dry" "--report-only" "--all")]
          (is (= 1 status) err)))
      (finally (fs/delete-tree plain)))))

(deftest mutate-exits-with-stryker-when-it-leaves-no-report
  (fs/delete-tree (fs/path *repo* "tmp" "stryker"))
  (is (= 4 (:status (sfcs {"FAKE_EXIT" "4"} "mutate" "--all"))))
  (testing "with a report the survivors decide"
    (let [{:keys [status lines]} (sfcs {"FAKE_REPORT" (str (fs/path fixtures "mutation-report.json"))} "mutate" "--all")]
      (is (= 1 status))
      (is (str/starts-with? (first lines) "$ dotnet stryker"))
      (is (some #(str/starts-with? % "Survived") lines)))))

(deftest crap-reads-sources-named-by-absolute-path
  (let [dir (fs/path *repo* "abs-coverage")
        xml (str/replace (fixture "coverage" "coverage.cobertura.xml") "filename=\"Bank/Ledger.cs\""
                         (str "filename=\"" (fs/path *repo* "Bank" "Ledger.cs") "\""))]
    (fs/create-dirs dir)
    (spit (str (fs/path dir "coverage.cobertura.xml")) xml)
    (doseq [flags [["--all"] ["--base" "main"]]]
      (let [{:keys [lines err]} (apply sfcs {} "crap" "--report" (str dir) flags)]
        (is (= "OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw" (first lines)) err)))))

(deftest the-newest-coverage-run-wins-a-tie
  (let [dir (fs/path *repo* "tie-coverage")
        xml (fn [complexity] (str/replace (fixture "coverage" "coverage.cobertura.xml")
                                          "complexity=\"4\"" (str "complexity=\"" complexity "\"")))
        write (fn [run complexity millis]
                (let [f (fs/path dir run "coverage.cobertura.xml")]
                  (fs/create-dirs (fs/parent f))
                  (spit (str f) (xml complexity))
                  (fs/set-last-modified-time f (java.nio.file.attribute.FileTime/fromMillis millis))))]
    (write "old" 7 1000000)
    (write "new" 5 2000000)
    (let [{:keys [lines]} (sfcs {} "crap" "--all" "--report" (str dir))]
      (is (some #(str/includes? % "cc   5") lines))
      (is (not-any? #(str/includes? % "cc   7") lines)))))

(deftest the-wrapper-script-runs-the-cli
  (let [{:keys [status lines]} (sfcs-process {} "help")]
    (is (= 0 status))
    (is (str/starts-with? (first lines) "usage: sf-cs")))
  (is (= 2 (:status (sfcs-process {} "nope")))))

(deftest help-after-a-command-prints-the-usage
  (let [{:keys [status lines]} (sfcs {} "test" "--help")]
    (is (= 0 status))
    (is (str/starts-with? (first lines) "usage: sf-cs"))))

(deftest tools-stop-at-the-first-failed-step
  (doseq [[step manifest calls] [["new tool-manifest" nil 1]
                               ["tool install" "{\"tools\":{}}" 1]
                               ["tool restore" "{\"tools\":{\"dotnet-stryker\":{}}}" 1]]]
    (fs/delete-tree (fs/path *repo* ".config"))
    (when manifest
      (fs/create-dirs (fs/path *repo* ".config"))
      (spit (str (fs/path *repo* ".config" "dotnet-tools.json")) manifest))
    (let [{:keys [status lines err]} (sfcs {"FAKE_FAIL_STEP" step} "tools")]
      (is (= 2 status) step)
      (is (= calls (count lines)) step)
      (is (str/includes? err "exited 7") err)))
  (fs/delete-tree (fs/path *repo* ".config")))

(deftest fresh-report-required-after-running-a-tool
  (doseq [[command report] [["dry" "tmp/jscpd/jscpd-report.json"]
                            ["mutate" "tmp/stryker/reports/mutation-report.json"]]
          exit ["0" "5"]]
    (let [path (fs/path *repo* report)]
      (fs/create-dirs (fs/parent path))
      (spit (str path) (if (= command "dry") "{\"duplicates\":[]}" "{\"files\":{}}"))
      (let [{:keys [status lines err]} (sfcs {"FAKE_EXIT" exit} command "--all")]
        (is (= (if (= "0" exit) 2 5) status) (str command " exit " exit))
        (is (not (fs/exists? path)))
        (is (= 1 (count lines)))
        (when (= "0" exit) (is (str/includes? err "not found")))))))

(def compiled-languages
  "Languages scored from compiled methods and not mutable by Stryker.NET."
  [{:language "fsharp" :label "F#" :extension "fs"
    :jscpd "--format fsharp --min-tokens"
    :source "module Bank.Account\nlet calculate value =\n    value + 1\n"
    :signature-edit ["value =" "value = // signature-only edit"]}
   {:language "vbnet" :label "VB.NET" :extension "vb"
    :jscpd "--format visual-basic --formats-exts visual-basic:vb --min-tokens"
    :source "Public Module Account\n    Public Function Calculate(value As Integer) As Integer\n        Return value + 1\n    End Function\nEnd Module\n"
    :signature-edit ["As Integer) As Integer" "As Integer) As Integer ' signature-only edit"]}])

(defn- in-language [{:keys [language]} env & args]
  (apply sfcs (assoc env "LANGUAGE" language) args))

(defn- source-file [{:keys [extension]} stem]
  (str "Bank/" stem "." extension))

(deftest help-describes-the-language-capabilities
  (let [{:keys [status lines]} (sfcs {} "help")
        help (str/join "\n" lines)]
    (is (= 0 status))
    (is (str/includes? help "install dotnet-stryker locally"))
    (is (str/includes? help "Stryker on changed files")))
  (doseq [{:keys [label] :as profile} compiled-languages]
    (let [{:keys [status lines]} (in-language profile {} "help")
          help (str/join "\n" lines)]
      (is (= 0 status))
      (is (str/includes? help "check dotnet, node, npx and prepare jscpd"))
      (is (str/includes? help (str "unsupported for " label "; exit 2"))))))

(deftest compiled-language-tool-boundary
  (doseq [{:keys [label] :as profile} compiled-languages]
    (let [{:keys [status lines err]} (in-language profile {} "tools")]
      (is (= 0 status) err)
      (is (= ["$ npx --yes jscpd@4 --version"] lines)))
    (is (= 2 (:status (in-language profile {"MISSING" "dotnet"} "tools"))))
    (let [{:keys [status lines err]} (in-language profile {} "mutate" "--all" "--report-only")]
      (is (= 2 status))
      (is (every? str/blank? lines))
      (is (str/includes? err (str label " mutation is unsupported"))))))

(deftest compiled-language-test-selection-and-failure
  (let [log (str (fs/path *repo* "compiled-dotnet.log"))]
    (doseq [profile compiled-languages
            [command category] [["test" "Category!=Property"] ["property" "Category=Property"]]]
      (is (= 7 (:status (in-language profile {"FAKE_DOTNET_LOG" log "FAKE_EXIT" "7"} command "--no-build"))))
      (is (= ["test" "--filter" category "--no-build"] (str/split-lines (slurp log)))))))

(deftest compiled-methods-use-report-spans
  (doseq [{:keys [language source] :as profile} compiled-languages]
    (let [dir (fs/path *repo* (str language "-coverage"))
          report (fs/path dir "coverage.cobertura.xml")
          file (source-file profile "Account")
          xml (cobertura-xml [] file)
          crap-all #(:status (in-language profile {} "crap" "--all" "--report" (str dir)))]
      (fs/create-dirs dir)
      (spit (str (fs/path *repo* file)) source)
      (spit (str report) xml)
      (let [{:keys [status lines err]} (in-language profile {} "crap" "--base" "main" "--report" (str dir))]
        (is (= 0 status) err)
        (is (str/includes? (first lines) (str file ":3 B.M"))))
      (spit (str report) (str/replace xml "</classes>"
                                     "<class name=\"Other\" filename=\"Other.cs\"><methods><method name=\"Ignored\"/></methods></class></classes>"))
      (is (= 0 (crap-all)))
      (spit (str report) (str/replace xml "complexity=\"2\"" "complexity=\"0\""))
      (is (= 0 (crap-all)))
      (doseq [invalid [(str/replace xml " complexity=\"2\"" "")
                       (str/replace xml " line-rate=\"1\"" "")
                       (str/replace xml "number=\"3\"" "number=\"0\"")
                       (str/replace xml "<line number=\"3\" hits=\"1\"/>"
                                    "<line number=\"3\" hits=\"1\"/><line number=\"0\" hits=\"0\"/>")
                       (str/replace xml "<line number=\"3\" hits=\"1\"/>" "")
                       (str/replace xml "line-rate=\"1\"" "line-rate=\"2\"")
                       (str/replace xml "complexity=\"2\"" "complexity=\"NaN\"")
                       (cobertura-xml [] "Bank/Account.cs")]]
        (spit (str report) invalid)
        (is (= 2 (crap-all)) invalid))
      (spit (str report) (-> xml (str/replace "complexity=\"2\"" "complexity=\"4\"")
                             (str/replace "line-rate=\"1\"" "line-rate=\"0.25\"")))
      (is (= 1 (crap-all))))))

(deftest compiled-language-dry-format-and-freshness
  (let [report (str (fs/path fixtures "jscpd-report.json"))
        path (fs/path *repo* "tmp" "jscpd" "jscpd-report.json")]
    (doseq [{:keys [jscpd] :as profile} compiled-languages]
      (let [{:keys [status lines]} (in-language profile {"FAKE_JSCPD_REPORT" report} "dry" "--all")]
        (is (= 1 status))
        (is (str/includes? (first lines) jscpd))
        (is (str/includes? (first lines) "**/*.Designer.cs,**/*.Designer.vb")))
      (is (= 1 (:status (in-language profile {} "dry" "--all" "--report-only"))))
      (is (= 5 (:status (in-language profile {"FAKE_EXIT" "5"} "dry" "--all"))))
      (is (not (fs/exists? path))))))

(deftest compiled-scope-does-not-expand-into-signatures
  (doseq [{:keys [language source signature-edit] :as profile} compiled-languages]
    (let [file (source-file profile "Unchanged")
          dir (fs/path *repo* (str language "-scope"))]
      (spit (str (fs/path *repo* file)) source)
      (git "add" file)
      (git "commit" "--quiet" "-m" "compiled method baseline")
      (let [base (str/trim (:out (git "rev-parse" "HEAD")))]
        (spit (str (fs/path *repo* file)) (apply str/replace source signature-edit))
        (fs/create-dirs dir)
        (spit (str (fs/path dir "coverage.cobertura.xml")) (cobertura-xml [] file))
        (let [{:keys [status lines err]} (in-language profile {} "crap" "--base" base "--report" (str dir))]
          (is (= 0 status) err)
          (is (= ["0 changed method(s), 0 over CRAP 10"] lines)))))))

(deftest compiled-language-coverage-requires-fresh-output
  (doseq [profile compiled-languages]
    (let [path (fs/path *repo* "tmp" "coverage" "stale" "coverage.cobertura.xml")]
      (fs/create-dirs (fs/parent path))
      (spit (str path) (cobertura-xml [] (source-file profile "Account")))
      (is (= 7 (:status (in-language profile {"FAKE_EXIT" "7"} "coverage"))))
      (is (not (fs/exists? path)))
      (is (= 2 (:status (in-language profile {} "coverage"))))
      (is (= 0 (:status (in-language profile {"FAKE_COVERAGE" (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))}
                                     "coverage")))))))

(deftest compiled-language-wrappers-dispatch-their-profile
  (doseq [{:keys [language]} compiled-languages]
    (let [{:keys [status lines]} (sfcs-process {"LANGUAGE" language} "help")]
      (is (= 0 status))
      (is (str/starts-with? (first lines) (str "usage: " (wrappers language)))))
    (is (= 2 (:status (sfcs-process {"LANGUAGE" language} "nope"))))))

;; Expecto test projects: sf-fs selects properties by each project's declared list.

(def xunit-fsproj
  "<Project Sdk=\"Microsoft.NET.Sdk\"><ItemGroup>
    <PackageReference Include=\"Microsoft.NET.Test.Sdk\" Version=\"17.12.0\" />
    <PackageReference Include=\"xunit\" Version=\"2.9.2\" />
  </ItemGroup></Project>")

(defn- expecto-fsproj [declaration]
  (str "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup>" declaration "</PropertyGroup><ItemGroup>
    <PackageReference Include=\"Expecto\" Version=\"10.2.1\" />
    <PackageReference Include=\"YoloDev.Expecto.TestSdk\" Version=\"0.15.3\" />
  </ItemGroup></Project>"))

(defn- declared [list-name]
  (expecto-fsproj (str "<SwarmForgePropertyTestList>" list-name "</SwarmForgePropertyTestList>")))

(defn- in-tree
  "Calls `f` with *repo* bound to a fresh directory holding `files` (path -> text)."
  [files f]
  (let [dir (str (fs/create-temp-dir {:prefix "sffs-tree"}))]
    (try
      (doseq [[path text] files]
        (fs/create-dirs (fs/parent (fs/path dir path)))
        (spit (str (fs/path dir path)) text))
      (binding [*repo* dir] (f))
      (finally (fs/delete-tree dir)))))

(defn- dotnet-lines [lines]
  (filterv #(str/starts-with? % "$ dotnet") lines))

(def mixed-tree
  {"src/Domain/Domain.fsproj" "<Project Sdk=\"Microsoft.NET.Sdk\"></Project>"
   "tests/Unit/Unit.fsproj" xunit-fsproj
   "tests/Laws/Laws.fsproj" (declared "  Laws  ")
   "tests/Csharp/Csharp.csproj" "<Project><PropertyGroup><IsTestProject>true</IsTestProject></PropertyGroup></Project>"
   "bin/Old/Old.fsproj" (declared "Old")
   "obj/Gen/Gen.fsproj" (declared "Gen")
   "tmp/Copy/Copy.fsproj" (declared "Copy")
   "node_modules/pkg/Pkg.fsproj" (declared "Pkg")
   ".worktrees/other/Other.fsproj" (declared "Other")})

(deftest expecto-trees-run-each-test-project-with-its-own-filter
  (in-tree mixed-tree
    #(doseq [[command filters] [["test" ["FullyQualifiedName!~Laws." "Category!=Property" "Category!=Property"]]
                                ["property" ["FullyQualifiedName~Laws." "Category=Property" "Category=Property"]]]]
       (let [{:keys [status lines err]} (sfcs {"LANGUAGE" "fsharp"} command "--no-build")]
         (is (= 0 status) err)
         (is (= (mapv (fn [project f] (str "$ dotnet test " project " --filter " f " --no-build"))
                      ["tests/Csharp/Csharp.csproj" "tests/Laws/Laws.fsproj" "tests/Unit/Unit.fsproj"]
                      [(nth filters 1) (nth filters 0) (nth filters 2)])
                lines))))))

(deftest expecto-list-names-match-literally
  (in-tree {"T/T.fsproj" (declared "a\\b(c)&amp;d|e=f!g~h")}
    #(is (= ["$ dotnet test T/T.fsproj --filter FullyQualifiedName~a\\\\b\\(c\\)\\&d\\|e\\=f\\!g\\~h."]
            (:lines (sfcs {"LANGUAGE" "fsharp"} "property"))))))

(deftest expecto-detection-reads-the-package-reference
  (doseq [reference ["<PackageReference Include=\"YoloDev.Expecto.TestSdk\"/>"
                     "<PackageReference Include=\"yolodev.expecto.testsdk\"><Version>0.15.3</Version></PackageReference>"
                     "<PackageReference\n  Version=\"0.15.3\"\n  Include=\"YoloDev.Expecto.TestSdk\" />"]]
    (in-tree {"T/T.fsproj" (str "<Project><PropertyGroup><SwarmForgePropertyTestList>P</SwarmForgePropertyTestList>"
                                "</PropertyGroup><ItemGroup>" reference "</ItemGroup></Project>")}
      #(is (= ["$ dotnet test T/T.fsproj --filter FullyQualifiedName~P."]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))
           reference)))
  (testing "only an F# project is an Expecto test project"
    (in-tree {"T/T.csproj" (declared "P") "U/U.vbproj" (declared "P")}
      #(is (= ["$ dotnet test --filter Category=Property"]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))))))

(deftest expecto-list-names-are-xml-values
  (doseq [[declaration filter] [["Laws&amp;Properties" "Laws\\&Properties."]
                                ["<![CDATA[A&B]]>" "A\\&B."]
                                ["&#76;aws<!-- note -->" "Laws."]]]
    (in-tree {"T/T.fsproj" (declared declaration)}
      #(is (= [(str "$ dotnet test T/T.fsproj --filter FullyQualifiedName~" filter)]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))
           declaration)))
  (testing "a commented-out declaration is no declaration"
    (in-tree {"T/T.fsproj" (expecto-fsproj "<!-- <SwarmForgePropertyTestList>Old</SwarmForgePropertyTestList> -->")}
      #(is (= 2 (:status (sfcs {"LANGUAGE" "fsharp"} "property")))))
    (in-tree {"T/T.fsproj" (expecto-fsproj (str "<!-- <SwarmForgePropertyTestList>Old</SwarmForgePropertyTestList> -->"
                                                "<SwarmForgePropertyTestList>New</SwarmForgePropertyTestList>"))}
      #(is (= ["$ dotnet test T/T.fsproj --filter FullyQualifiedName~New."]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))))))

(def commented-expecto-reference
  "<!-- <PackageReference Include=\"YoloDev.Expecto.TestSdk\" Version=\"0.15.3\" /> -->")

(deftest commented-out-elements-do-not-make-a-test-project
  (testing "an xUnit project with a commented-out Expecto reference keeps the trait run"
    (in-tree {"T/T.fsproj" (str/replace xunit-fsproj "<ItemGroup>" (str "<ItemGroup>" commented-expecto-reference))}
      #(is (= ["$ dotnet test --filter Category=Property"]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property"))))))
  (testing "commented-out test markers leave a project out of the per-project runs"
    (in-tree {"a/A.fsproj" (declared "P")
              "b/B.fsproj" (str "<Project><ItemGroup>" commented-expecto-reference
                                "<!-- <PackageReference Include=\"Microsoft.NET.Test.Sdk\" /> --></ItemGroup></Project>")
              "c/C.csproj" "<Project><PropertyGroup><!-- <IsTestProject>true</IsTestProject> --></PropertyGroup></Project>"}
      #(is (= ["$ dotnet test a/A.fsproj --filter FullyQualifiedName~P."]
              (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))))))

(deftest project-files-are-read-as-msbuild-xml
  (in-tree {"a/A.fsproj" (str "﻿<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                              "<Project ToolsVersion=\"15.0\" xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">"
                              "<PropertyGroup><SwarmForgePropertyTestList>Laws</SwarmForgePropertyTestList></PropertyGroup>"
                              "<ItemGroup><PackageReference Include=\" YoloDev.Expecto.TestSdk \" /></ItemGroup></Project>")
            "b/B.csproj" (str "<Project xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">"
                              "<PropertyGroup><IsTestProject> True </IsTestProject></PropertyGroup></Project>")
            "c/C.fsproj" "<Project><ItemGroup><PackageReference Include=\"YoloDev.Expecto.TestSdk\"></ItemGroup>"}
    #(is (= ["$ dotnet test a/A.fsproj --filter FullyQualifiedName~Laws."
             "$ dotnet test b/B.csproj --filter Category=Property"]
            (:lines (sfcs {"LANGUAGE" "fsharp"} "property"))))))

(deftest trees-without-expecto-keep-the-single-trait-run
  (in-tree {"tests/Unit/Unit.fsproj" xunit-fsproj
            "bin/Old/Old.fsproj" (declared "Old")
            ".hidden/Other.fsproj" (declared "Other")}
    #(doseq [[command filter] [["test" "Category!=Property"] ["property" "Category=Property"]]]
       (is (= [(str "$ dotnet test --filter " filter " --no-build")]
              (:lines (sfcs {"LANGUAGE" "fsharp"} command "--no-build")))))))

(deftest only-sf-fs-looks-for-expecto
  (in-tree mixed-tree
    #(doseq [language [nil "vbnet"]
             [command filter] [["test" "Category!=Property"] ["property" "Category=Property"]]]
       (is (= [(str "$ dotnet test --filter " filter)]
              (:lines (sfcs (if language {"LANGUAGE" language} {}) command)))
           (str language " " command)))))

(deftest undeclared-expecto-projects-stop-before-dotnet
  (doseq [declaration ["" "<SwarmForgePropertyTestList>  </SwarmForgePropertyTestList>" "<SwarmForgePropertyTestList/>"]]
    (in-tree {"tests/A/A.fsproj" (expecto-fsproj declaration) "tests/B/B.fsproj" (declared "Laws")}
      #(doseq [command ["test" "property" "coverage"]]
         (let [{:keys [status lines err]} (sfcs-process {"LANGUAGE" "fsharp"} command)]
           (is (= 2 status) (str declaration " " command))
           (is (empty? (dotnet-lines lines)))
           (is (str/starts-with? err "sf-fs: "))
           (is (str/includes? err "tests/A/A.fsproj"))
           (is (not (str/includes? err "tests/B/B.fsproj")))
           (is (str/includes? err "SwarmForgePropertyTestList")))))))

(deftest every-project-runs-and-the-first-failure-is-the-status
  (in-tree {"a/A.fsproj" (declared "P") "b/B.fsproj" xunit-fsproj "c/C.fsproj" (declared "Q")}
    #(doseq [[exits status] [["b/B.fsproj=5,c/C.fsproj=3" 5] ["c/C.fsproj=3" 3] ["" 0]]]
       (let [result (sfcs {"LANGUAGE" "fsharp" "FAKE_PROJECT_EXITS" exits} "test")]
         (is (= status (:status result)) exits)
         (is (= 3 (count (dotnet-lines (:lines result)))) exits)))))

(deftest expecto-coverage-collects-per-project
  (let [report (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))
        collector "--collect XPlat Code Coverage --results-directory tmp/coverage --no-build -- DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura"
        stale #(let [path (fs/path *repo* "tmp" "coverage" "stale" "coverage.cobertura.xml")]
                 (fs/create-dirs (fs/parent path))
                 (spit (str path) "x")
                 path)]
    (in-tree {"a/A.fsproj" (declared "P") "b/B.fsproj" xunit-fsproj}
      #(do
         (testing "one collector run per project, a fresh report required"
           (let [old (stale)
                 {:keys [status lines err]} (sfcs {"LANGUAGE" "fsharp" "FAKE_COVERAGE" report} "coverage" "--no-build")]
             (is (= 0 status) err)
             (is (= [(str "$ dotnet test a/A.fsproj --filter FullyQualifiedName!~P. " collector)
                     (str "$ dotnet test b/B.fsproj --filter Category!=Property " collector)]
                    (dotnet-lines lines)))
             (is (not (fs/exists? old)))
             (is (fs/exists? (fs/path *repo* "tmp" "coverage" "run" "coverage.cobertura.xml")))))
         (testing "a failing project is the status after every project ran"
           (let [{:keys [status lines]} (sfcs {"LANGUAGE" "fsharp" "FAKE_COVERAGE" report "FAKE_PROJECT_EXITS" "a/A.fsproj=4"} "coverage")]
             (is (= 4 status))
             (is (= 2 (count (dotnet-lines lines))))))
         (testing "runs that leave no report are an error"
           (stale)
           (is (= 2 (:status (sfcs {"LANGUAGE" "fsharp"} "coverage")))))))))

(deftest only-sf-fs-help-mentions-expecto
  (is (str/includes? (str/join "\n" (:lines (sfcs {"LANGUAGE" "fsharp"} "help"))) "Expecto"))
  (doseq [env [{} {"LANGUAGE" "vbnet"}]]
    (let [help (str/join "\n" (:lines (sfcs env "help")))]
      (is (not (str/includes? help "Expecto")))
      (is (str/includes? help "unit and acceptance tests (Category!=Property)\n"))
      (is (str/includes? help "property tests only (Category=Property)\n")))))
