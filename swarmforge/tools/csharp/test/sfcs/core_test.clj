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
            [sfcs.coverage :as coverage]
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

(deftest cobertura-files-are-found-under-a-directory-with-glob-characters
  (let [dir (fs/create-temp-dir {:prefix "sfcs-cov"})
        results (fs/path dir "lab[x]{2}" "tmp" "coverage")
        report (fs/path results "run-1" "coverage.cobertura.xml")]
    (try
      (fs/create-dirs (fs/parent report))
      (spit (str report) "x")
      (is (= [(str report)] (cob/find-cobertura-files (str results))))
      (finally (fs/delete-tree dir)))))

(deftest method-rows-name-the-signature-line-when-known
  (let [method {:class-name "A.B" :name "M" :first-line 7 :crap 1.0 :complexity 1.0 :coverage 1.0 :file "a.cs"}]
    (is (= "ok   crap    1.0  cc   1  cov 100%  a.cs:7 B.M" (cob/format-method crap/crap-limit method)))
    (is (= "ok   crap    1.0  cc   1  cov 100%  a.cs:5 B.M" (cob/format-method crap/crap-limit (assoc method :signature-line 5))))))

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

(defn- fake-launch
  "Prints the command line, then runs `command` from the fake tools in *bin* with `env` for the fakes."
  [env command args]
  (println (str "$ " (str/join " " (cons command args))))
  (apply p/shell {:dir *repo* :out :string :err :string :continue true :extra-env env}
         (str (fs/path *bin* command)) args))

(defn- fake-run
  "shell/run against the fake tools in *bin*."
  [env]
  (fn [command args] (:exit (fake-launch env command args))))

(defn- fake-capture
  "shell/capture against the fake tools in *bin*; stderr goes to *err*."
  [env]
  (fn [command args]
    (let [result (fake-launch env command args)]
      (binding [*out* *err*] (print (:err result)))
      (select-keys result [:exit :out]))))

(defn- sfcs
  "Runs sf-cs in-process in the scratch repository. `env` feeds the fakes;
  MISSING lists commands to report as absent from PATH."
  [env & args]
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        missing (set (str/split (get env "MISSING" "") #","))
        _ (when-let [log (get env "FAKE_DOTNET_LOG")] (spit log ""))
        status (binding [shell/*dir* *repo* *out* out *err* err]
                 (with-redefs [shell/run (fake-run env)
                               shell/capture (fake-capture env)
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

(def stryker-manifest
  "{\"version\":1,\"isRoot\":true,\"tools\":{\"dotnet-stryker\":{\"version\":\"4.16.0\",\"commands\":[\"dotnet-stryker\"]}}}\n")

(def fake-dotnet
  "The fake dotnet. `dotnet-coverage collect` copies FAKE_COVERAGE to its -o file
  (into a directory sf must have created) and exits with the wrapped command's
  status; `restore <project>` fails for the projects in FAKE_RESTORE_FAILS and a
  bare `restore` exits FAKE_BARE_RESTORE_EXIT; `msbuild` reads FAKE_TEST_PROJECTS,
  FAKE_MTP_PROJECTS and FAKE_ASSEMBLY_NAMES (project=name; else the file name)."
  (str "#!/usr/bin/env bash\n"
       "if [ -n \"$FAKE_DOTNET_LOG\" ]; then (IFS=$'\\t'; printf '%s\\n' \"$*\") >> \"$FAKE_DOTNET_LOG\"; fi\n"
       "if [ \"$1 $2\" = \"$FAKE_FAIL_STEP\" ]; then exit 7; fi\n"
       "if [ \"$1 $2\" = 'tool list' ]; then\n"
       "  if [ -n \"${FAKE_LIST_JSON+set}\" ]; then printf '%s' \"$FAKE_LIST_JSON\"\n"
       "  else printf '{\"version\":1,\"data\":[{\"packageId\":\"%s\",\"version\":\"1.0.0\",\"commands\":[\"%s\"],\"manifest\":\"%s/dotnet-tools.json\"}]}' \"$4\" \"$4\" \"$(pwd -P)\"; fi\n"
       "  exit ${FAKE_LIST_EXIT:-0}\n"
       "fi\n"
       "if [ \"$1 $2\" = 'stryker --help' ]; then exit ${FAKE_STRYKER_EXIT:-0}; fi\n"
       "if [ \"$1 $2\" = 'dotnet-coverage --version' ]; then exit ${FAKE_COVERAGE_TOOL_EXIT:-0}; fi\n"
       "if [ \"$1\" = restore ] && [ -z \"$2\" ]; then exit ${FAKE_BARE_RESTORE_EXIT:-0}; fi\n"
       "if [ \"$1\" = restore ]; then case \",$FAKE_RESTORE_FAILS,\" in *\",$2,\"*) exit 1;; esac; exit 0; fi\n"
       "for pair in ${FAKE_PROJECT_EXITS//,/ }; do if [ \"$2\" = \"${pair%%=*}\" ]; then exit \"${pair#*=}\"; fi; done\n"
       "if [ \"$1\" = msbuild ]; then\n"
       "  test=; mtp=; name=$(basename \"$2\"); name=${name%.*}\n"
       "  case \",$FAKE_TEST_PROJECTS,\" in *\",$2,\"*) test=true;; esac\n"
       "  case \",$FAKE_MTP_PROJECTS,\" in *\",$2,\"*) mtp=true;; esac\n"
       "  for pair in ${FAKE_ASSEMBLY_NAMES//,/ }; do if [ \"$2\" = \"${pair%%=*}\" ]; then name=${pair#*=}; fi; done\n"
       "  printf '{\\n  \"Properties\": {\\n    \"IsTestProject\": \"%s\",\\n    \"IsTestingPlatformApplication\": \"%s\",\\n    \"AssemblyName\": \"%s\"\\n  }\\n}\\n' \"$test\" \"$mtp\" \"$name\"\n"
       "  exit 0\n"
       "fi\n"
       "if [ \"$1 $2\" = 'dotnet-coverage collect' ]; then\n"
       "  out=; shift 2\n"
       "  while [ $# -gt 0 ] && [ \"$1\" != -- ]; do if [ \"$1\" = -o ]; then out=$2; fi; shift; done\n"
       "  shift 2\n"
       "  if [ -n \"$FAKE_COVERAGE\" ]; then cp \"$FAKE_COVERAGE\" \"$out\" || exit 99; fi\n"
       "  FAKE_DOTNET_LOG= \"$0\" \"$@\"; exit $?\n"
       "fi\n"
       "if [ \"$1\" = stryker ] && [ -n \"$FAKE_REPORT\" ]; then mkdir -p tmp/stryker/reports && cp \"$FAKE_REPORT\" tmp/stryker/reports/mutation-report.json; fi\n"
       "exit ${FAKE_EXIT:-0}\n"))

(defn- seed-repo [f]
  (let [repo (str (fs/create-temp-dir {:prefix "sfcs"}))
        bin (str (fs/path repo "fakebin"))]
    (fs/create-dirs bin)
    (spit (str (fs/path bin "dotnet")) fake-dotnet)
    (doseq [tool ["node" "npx"]]
      (spit (str (fs/path bin tool))
            (str "#!/usr/bin/env bash\n"
                 "if [ -n \"$FAKE_JSCPD_REPORT\" ]; then mkdir -p tmp/jscpd && cp \"$FAKE_JSCPD_REPORT\" tmp/jscpd/jscpd-report.json; fi\n"
                 "exit ${FAKE_NPX_EXIT:-${FAKE_EXIT:-0}}\n"))
      (fs/set-posix-file-permissions (fs/path bin tool) "rwxr-xr-x"))
    (fs/set-posix-file-permissions (fs/path bin "dotnet") "rwxr-xr-x")
    (binding [*repo* repo *bin* bin]
      (git "init" "--quiet" "--initial-branch=main")
      (git "config" "user.email" "test@example.com")
      (git "config" "user.name" "Test")
      (fs/create-dirs (fs/path repo "Bank"))
      (spit (str (fs/path repo "Bank" "Ledger.cs")) (fixture "Ledger.cs.txt"))
      (spit (str (fs/path repo "Bank" "Legacy.cs")) "namespace Bank { }\n")
      (spit (str (fs/path repo "Bank" "Bank.csproj")) "<Project Sdk=\"Microsoft.NET.Sdk\"></Project>\n")
      (spit (str (fs/path repo "dotnet-tools.json")) stryker-manifest)
      (git "add" ".")
      (git "commit" "--quiet" "-m" "base")
      (git "switch" "--quiet" "-c" "swarm/abc1234")
      (spit (str (fs/path repo "Bank" "Ledger.cs"))
            (str/replace (fixture "Ledger.cs.txt") "if (amount > total" "if (amount >= total"))
      (git "commit" "--quiet" "-am" "task work")
      (spit (str (fs/path repo "Bank" "Audit.cs"))
            "namespace Bank\n{\n    public class Audit<T> { public void Record(T item) { if (item == null) return; } }\n}\n")
      (try (f) (finally (fs/delete-tree repo))))))

(use-fixtures :each seed-repo)

(deftest crap-gate
  (testing "only changed methods, failing over the limit"
    (let [{:keys [status lines err]} (sfcs {} "crap" "--base" "main" "--report" (str (fs/path fixtures "coverage")))]
      (is (= 1 status) err)
      (is (= ["OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw"
              "ok   crap    6.0  cc   2  cov   0%  Bank/Audit.cs:4 Audit<T>.Record"
              "2 changed method(s), 1 over CRAP 10"]
             lines))))
  (testing "--all reports every method"
    (let [{:keys [status lines err]} (sfcs {} "crap" "--all" "--no-executable-code" "Bank/Legacy.cs" "--report" (str (fs/path fixtures "coverage")))]
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

(defn- dotnet-calls
  "The fake dotnet's logged calls, one argument vector per call."
  [log]
  (mapv #(str/split % #"\t") (str/split-lines (slurp log))))

(deftest dotnet-command-lines
  (let [log (str (fs/path *repo* "dotnet.log"))
        calls (fn [env & args]
                (let [result (apply sfcs (assoc env "FAKE_DOTNET_LOG" log) args)]
                  (assoc result :calls (dotnet-calls log) :args (last (dotnet-calls log)))))]
    (is (= ["test" "--filter" "Category!=Property" "--no-build"] (:args (calls {} "test" "--no-build"))))
    (is (= ["test" "--filter" "Category=Property"] (:args (calls {} "property"))))
    (is (= [["tool" "list" "--local" "dotnet-stryker" "--format" "json"]
            ["stryker" "--help"]
            ["stryker" "--reporter" "json" "--reporter" "progress" "--output" "tmp/stryker" "--since:main" "--project" "Bank.csproj"]]
           (:calls (calls {} "mutate" "--base" "main" "--" "--project" "Bank.csproj"))))
    (is (= [["tool" "restore"]] (:calls (calls {} "tools"))) "tools never installs, updates or creates a manifest")
    (let [coverage (calls {"FAKE_COVERAGE" (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))
                           "FAKE_BARE_RESTORE_EXIT" "1"}
                          "coverage" "App.sln" "--no-build")]
      (is (= 0 (:status coverage)) (:err coverage))
      (is (= [["tool" "list" "--local" "dotnet-coverage" "--format" "json"]
              ["dotnet-coverage" "--version"]
              ["restore" "Bank/Bank.csproj"]
              ["msbuild" "Bank/Bank.csproj" "-getProperty:IsTestProject" "-getProperty:IsTestingPlatformApplication"
               "-getProperty:AssemblyName"]
              ["dotnet-coverage" "collect" "-s" "tmp/dotnet-coverage.xml" "-f" "cobertura"
               "-o" "tmp/coverage/run-1/coverage.cobertura.xml"
               "--" "dotnet" "test" "--filter" "Category!=Property" "App.sln" "--no-build"]]
             (:calls coverage)))
      (is (= "1 coverage file(s) under tmp/coverage" (last (:lines coverage))))
      (is (fs/exists? (fs/path *repo* "tmp" "coverage" "run-1" "coverage.cobertura.xml"))))))

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

(deftest tools-restores-the-pinned-tools
  (testing "restore, then the jscpd check, whose exit is the status"
    (let [{:keys [status lines err]} (sfcs {"FAKE_NPX_EXIT" "3"} "tools")]
      (is (= 3 status) err)
      (is (= ["$ dotnet tool restore" "$ npx --yes jscpd@4 --version"] lines))))
  (testing "no tool manifest is written"
    (is (= 0 (:status (sfcs {} "tools"))))
    (is (not (fs/exists? (fs/path *repo* ".config"))))
    (is (= stryker-manifest (slurp (str (fs/path *repo* "dotnet-tools.json"))))))
  (testing "a failed restore stops before npx"
    (let [{:keys [status lines err]} (sfcs {"FAKE_FAIL_STEP" "tool restore"} "tools")]
      (is (= 2 status))
      (is (= ["$ dotnet tool restore"] lines))
      (is (= "sf-cs: dotnet tool restore exited 7\n" err)))))

(deftest coverage-run-statuses
  (let [report (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))]
    (testing "0 and 8 (MTP ran zero tests somewhere) pass"
      (doseq [exit ["0" "8"]]
        (is (= 0 (:status (sfcs {"FAKE_COVERAGE" report "FAKE_EXIT" exit} "coverage"))) exit)))
    (testing "any other status, MTP's 2 for a failing test included, is 1"
      (doseq [exit ["1" "2" "3"]]
        (is (= 1 (:status (sfcs {"FAKE_COVERAGE" report "FAKE_EXIT" exit} "coverage"))) exit)))
    (testing "a failed project restore warns, and the run goes on and exits by the test status"
      (doseq [[exit status] [["0" 0] ["3" 1]]]
        (let [{:keys [lines err] :as result} (sfcs {"FAKE_COVERAGE" report "FAKE_RESTORE_FAILS" "Bank/Bank.csproj"
                                                    "FAKE_EXIT" exit}
                                                   "coverage")]
          (is (= status (:status result)) err)
          (is (str/includes? err "warning: dotnet restore Bank/Bank.csproj failed"))
          (is (not-any? #(str/starts-with? % "$ dotnet msbuild") lines))
          (is (some #(str/includes? % "collect") lines)))))
    (testing "passing runs that wrote no Cobertura file are an input error"
      (let [{:keys [status err]} (sfcs {} "coverage")]
        (is (= 2 status))
        (is (str/includes? err "no coverage.cobertura.xml under tmp/coverage"))
        (is (not (str/includes? err "coverlet")))))))

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

(defn- last-line [text]
  (last (str/split-lines text)))

(deftest mutate-without-a-report-after-a-failed-stryker-is-a-usage-error
  (fs/delete-tree (fs/path *repo* "tmp" "stryker"))
  (let [{:keys [status err]} (sfcs {"FAKE_EXIT" "4"} "mutate" "--all")]
    (is (= 2 status))
    (is (= "sf-cs: dotnet stryker exited 4 and wrote no report" (last-line err))))
  (testing "with a report the survivors decide"
    (let [{:keys [status lines]} (sfcs {"FAKE_REPORT" (str (fs/path fixtures "mutation-report.json"))} "mutate" "--all")]
      (is (= 1 status))
      (is (= ["$ dotnet tool list --local dotnet-stryker --format json"
              "$ dotnet stryker --help"
              "$ dotnet stryker --reporter json --reporter progress --output tmp/stryker"]
             (take 3 lines)))
      (is (some #(str/starts-with? % "Survived") lines)))))

(defn- real-root []
  (str (fs/real-path *repo*)))

(defn- list-answer
  "Fake `dotnet tool list` stdout naming `manifest` as the dotnet-stryker pin."
  [manifest]
  (json/generate-string {"version" 1 "data" [{"packageId" "dotnet-stryker" "version" "4.16.0"
                                             "commands" ["dotnet-stryker"] "manifest" (str manifest)}]}))

(defn- pin-instructions [root]
  (str "in " root ": dotnet new tool-manifest (when the root has none), then dotnet tool install dotnet-stryker"
       " --version <version>, then commit the manifest"))

(defn- refused-mutate
  "Runs mutate with `env`; asserts exit 2, `echoes` echo lines, an untouched tmp/stryker; returns stderr's last line."
  [env echoes]
  (let [sentinel (fs/path *repo* "tmp" "stryker" "keep")]
    (fs/create-dirs (fs/parent sentinel))
    (spit (str sentinel) "x")
    (let [{:keys [status lines err]} (sfcs env "mutate" "--all")]
      (is (= 2 status) err)
      (is (= echoes (count lines)) (str lines))
      (is (fs/exists? sentinel))
      (last-line err))))

(deftest mutate-needs-a-stryker-pin
  (doseq [[env exit] [[{"FAKE_LIST_EXIT" "1" "FAKE_LIST_JSON" "{\"version\":1,\"data\":[]}"} 1]
                      [{"FAKE_LIST_EXIT" "1" "FAKE_LIST_JSON" ""} 1]
                      [{"FAKE_LIST_JSON" "{\"version\":1,\"data\":[{\"packageId\":\"other\",\"manifest\":\"/x\"}]}"} 0]
                      [{"FAKE_LIST_JSON" "not json"} 0]
                      [{"FAKE_LIST_JSON" "[1]"} 0]]]
    (is (= (str "sf-cs: dotnet tool list --local dotnet-stryker found no dotnet-stryker (exit " exit "); "
                "ask the operator to pin one in a committed tool manifest at the repository root ("
                (pin-instructions (real-root)) ")")
           (refused-mutate env 1))
        (str env))))

(deftest mutate-refuses-a-pin-outside-the-repository
  (let [outside (fs/create-temp-dir {:prefix "sfcs-above"})
        manifest (fs/path outside "dotnet-tools.json")]
    (try
      (spit (str manifest) stryker-manifest)
      (is (= (str "sf-cs: dotnet-stryker is pinned in " (fs/real-path manifest) ", outside this repository; "
                  "ask the operator to pin it in a committed tool manifest at the repository root ("
                  (pin-instructions (real-root)) ")")
             (refused-mutate {"FAKE_LIST_JSON" (list-answer manifest)} 1)))
      (finally (fs/delete-tree outside)))))

(deftest mutate-refuses-a-pin-below-the-repository-root
  (doseq [relative ["Bank/dotnet-tools.json" "Bank/.config/dotnet-tools.json" ".config/sub/dotnet-tools.json"]]
    (let [manifest (fs/path *repo* relative)]
      (fs/create-dirs (fs/parent manifest))
      (spit (str manifest) stryker-manifest)
      (git "add" relative)
      (git "commit" "--quiet" "-m" "pin below the root")
      (is (= (str "sf-cs: dotnet-stryker is pinned in " (fs/real-path manifest) ", not at the repository root, "
                  "so sf-cs tools does not restore it; ask the operator to pin it in a committed tool manifest "
                  "at the repository root (" (pin-instructions (real-root)) ") and remove this pin")
             (refused-mutate {"FAKE_LIST_JSON" (list-answer manifest)} 1))
          relative)
      (git "rm" "--quiet" relative)
      (git "commit" "--quiet" "-m" "unpin below the root"))))

(deftest mutate-needs-the-pin-committed
  (let [relative ".config/dotnet-tools.json"
        manifest (fs/path *repo* relative)
        env {"FAKE_LIST_JSON" (list-answer manifest) "FAKE_REPORT" (str (fs/path fixtures "mutation-report.json"))}
        refusal (fn [] (str "sf-cs: dotnet-stryker is pinned in " (fs/real-path manifest)
                            ", which is not committed; ask the operator to commit or remove it"))]
    (fs/create-dirs (fs/parent manifest))
    (spit (str manifest) stryker-manifest)
    (testing "untracked"
      (is (= (refusal) (refused-mutate env 1))))
    (testing "committed under .config/ it is the pin"
      (git "add" relative)
      (git "commit" "--quiet" "-m" "pin under .config")
      (let [{:keys [status err]} (sfcs env "mutate" "--all")]
        (is (= 1 status) err)))
    (testing "committed, then changed"
      (spit (str manifest) (str/replace stryker-manifest "4.16.0" "4.17.0"))
      (is (= (refusal) (refused-mutate env 1))))
    (git "rm" "--quiet" "--force" relative)
    (git "commit" "--quiet" "-m" "unpin .config")))

(deftest mutate-needs-a-runnable-stryker
  (is (= "sf-cs: dotnet stryker --help exited 1; run sf-cs tools to restore the pinned Stryker"
         (refused-mutate {"FAKE_STRYKER_EXIT" "1"} 2))))

(deftest mutate-without-dotnet-keeps-the-launch-failure
  (let [bin (fs/create-temp-dir {:prefix "sfcs-nodotnet"})]
    (try
      (doseq [tool ["bb" "git" "dirname" "bash"]]
        (fs/create-sym-link (fs/path bin tool) (fs/which tool)))
      (let [result (p/shell {:dir *repo* :out :string :err :string :continue true
                             :extra-env {"PATH" (str bin)}}
                            (str (fs/which "bash")) (str (fs/path here ".." ".." "bin" "sf-cs")) "mutate" "--all")]
        (is (= 2 (:exit result)) (:err result))
        (is (str/starts-with? (last-line (:err result)) "sf-cs: dotnet: ") (:err result)))
      (finally (fs/delete-tree bin)))))

(deftest crap-reads-sources-named-by-absolute-path
  (let [dir (fs/path *repo* "abs-coverage")
        xml (str/replace (fixture "coverage" "coverage.cobertura.xml") "filename=\"Bank/Ledger.cs\""
                         (str "filename=\"" (fs/path *repo* "Bank" "Ledger.cs") "\""))]
    (fs/create-dirs dir)
    (spit (str (fs/path dir "coverage.cobertura.xml")) xml)
    (doseq [flags [["--all" "--no-executable-code" "Bank/Legacy.cs"] ["--base" "main"]]]
      (let [{:keys [lines err]} (apply sfcs {} "crap" "--report" (str dir) flags)]
        (is (= "OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw" (first (filter #(str/starts-with? % "OVER") lines))) err)))))

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
    (let [{:keys [lines]} (sfcs {} "crap" "--all" "--no-executable-code" "Bank/Legacy.cs" "--report" (str dir))]
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

(deftest fresh-report-required-after-running-a-tool
  (doseq [[command report] [["dry" "tmp/jscpd/jscpd-report.json"]
                            ["mutate" "tmp/stryker/reports/mutation-report.json"]]
          exit ["0" "5"]]
    (let [path (fs/path *repo* report)]
      (fs/create-dirs (fs/parent path))
      (spit (str path) (if (= command "dry") "{\"duplicates\":[]}" "{\"files\":{}}"))
      (let [{:keys [status lines err]} (sfcs {"FAKE_EXIT" exit} command "--all")]
        (is (= (if (and (= "dry" command) (= "5" exit)) 5 2) status) (str command " exit " exit))
        (is (not (fs/exists? path)))
        (is (= (if (= "dry" command) 1 3) (count lines)))
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
    (is (str/includes? help "tools                       restore local dotnet tools and check node, npx, dotnet\n"))
    (is (not (str/includes? help "install dotnet-stryker")))
    (is (str/includes? help "Stryker on changed files"))
    (is (str/includes? help "needs dotnet-stryker pinned in a committed tool manifest")))
  (doseq [{:keys [label] :as profile} compiled-languages]
    (let [{:keys [status lines]} (in-language profile {} "help")
          help (str/join "\n" lines)]
      (is (= 0 status))
      (is (str/includes? help "restore local dotnet tools and check node, npx, dotnet"))
      (is (str/includes? help (str "unsupported for " label "; exit 2"))))))

(deftest compiled-language-tool-boundary
  (doseq [{:keys [label] :as profile} compiled-languages]
    (let [{:keys [status lines err]} (in-language profile {} "tools")]
      (is (= 0 status) err)
      (is (= ["$ dotnet tool restore" "$ npx --yes jscpd@4 --version"] lines)))
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
      (is (= [["test" "--filter" category "--no-build"]] (dotnet-calls log))))))

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
      (is (= 1 (:status (in-language profile {"FAKE_EXIT" "7"} "coverage"))))
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
  "Calls `f` with *repo* bound to a fresh git repository holding `files` (path -> text)
  and a committed root tool manifest."
  [files f]
  (let [dir (str (fs/real-path (fs/create-temp-dir {:prefix "sffs-tree"})))]
    (try
      (doseq [[path text] (assoc files "dotnet-tools.json" stryker-manifest)]
        (fs/create-dirs (fs/parent (fs/path dir path)))
        (spit (str (fs/path dir path)) text))
      (binding [*repo* dir]
        (git "init" "--quiet" "--initial-branch=main")
        (git "config" "user.email" "test@example.com")
        (git "config" "user.name" "Test")
        (git "add" ".")
        (git "commit" "--quiet" "-m" "tree")
        (f))
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

(deftest symlinked-directories-are-not-walked-for-projects
  (in-tree {"tests/Laws/Laws.fsproj" (declared "Laws")}
    #(do (fs/create-sym-link (fs/path *repo* "linked") (fs/path *repo* "tests"))
         (is (= ["$ dotnet test tests/Laws/Laws.fsproj --filter FullyQualifiedName~Laws."]
                (:lines (sfcs {"LANGUAGE" "fsharp"} "property")))))))

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
           (is (= (if (= "coverage" command)
                    ["$ dotnet tool list --local dotnet-coverage --format json" "$ dotnet dotnet-coverage --version"]
                    [])
                  (dotnet-lines lines)))
           (is (not (fs/exists? (fs/path *repo* "tmp" "coverage"))))
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
        collector (fn [i] (str "$ dotnet dotnet-coverage collect -s tmp/dotnet-coverage.xml -f cobertura -o tmp/coverage/run-"
                               i "/coverage.cobertura.xml -- dotnet test "))
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
             (is (= [(str (collector 1) "a/A.fsproj --filter FullyQualifiedName!~P. --no-build")
                     (str (collector 2) "b/B.fsproj --filter Category!=Property --no-build")]
                    (filterv (fn [line] (str/includes? line "collect")) (dotnet-lines lines))))
             (is (not (fs/exists? old)))
             (is (= "2 coverage file(s) under tmp/coverage" (last lines)))))
         (testing "a failing project is the status after every project ran"
           (let [{:keys [status lines]} (sfcs {"LANGUAGE" "fsharp" "FAKE_COVERAGE" report "FAKE_PROJECT_EXITS" "a/A.fsproj=4"} "coverage")]
             (is (= 1 status))
             (is (= 2 (count (filter (fn [line] (str/includes? line "collect")) (dotnet-lines lines)))))))
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

;; Coverage through the project's pinned dotnet-coverage.

(defn- refused-coverage
  "Runs coverage with `env`; asserts exit 2 and an untouched tmp/coverage; returns stderr's last line."
  [env]
  (let [sentinel (fs/path *repo* "tmp" "coverage" "old" "coverage.cobertura.xml")]
    (fs/create-dirs (fs/parent sentinel))
    (spit (str sentinel) "old")
    (let [{:keys [status lines err]} (sfcs env "coverage")]
      (is (= 2 status) err)
      (is (empty? (filter #(str/includes? % "collect") lines)))
      (is (= "old" (slurp (str sentinel))))
      (fs/delete-tree (fs/parent sentinel))
      (last (str/split-lines err)))))

(defn- coverage-listing
  "Fake `dotnet tool list` stdout naming `manifest` as the `package` pin."
  [package manifest]
  (json/generate-string {"version" 1 "data" [{"packageId" package "manifest" manifest}]}))

(deftest coverage-needs-a-dotnet-coverage-pin
  (let [root (str (fs/real-path *repo*))
        instructions (str "a committed tool manifest at the repository root (in " root
                          ": dotnet new tool-manifest (when the root has none), then dotnet tool install dotnet-coverage"
                          " --version <version>, then commit the manifest)")]
    (testing "no pin"
      (is (= (str "sf-cs: dotnet tool list --local dotnet-coverage found no dotnet-coverage (exit 1); "
                  "ask the operator to pin one in " instructions)
             (refused-coverage {"FAKE_LIST_JSON" "{\"version\":1,\"data\":[]}" "FAKE_LIST_EXIT" "1"}))))
    (testing "a pin below the root"
      (let [manifest (fs/path *repo* "elsewhere" "dotnet-tools.json")]
        (fs/create-dirs (fs/parent manifest))
        (spit (str manifest) stryker-manifest)
        (is (= (str "sf-cs: dotnet-coverage is pinned in " manifest ", not at the repository root, "
                    "so sf-cs tools does not restore it; ask the operator to pin it in " instructions " and remove this pin")
               (refused-coverage {"FAKE_LIST_JSON" (coverage-listing "dotnet-coverage" (str manifest))})))
        (fs/delete-tree (fs/parent manifest))))
    (testing "a dotnet-stryker pin is no dotnet-coverage pin"
      (is (str/includes? (refused-coverage {"FAKE_LIST_JSON" (coverage-listing "dotnet-stryker" (str root "/dotnet-tools.json"))})
                         "found no dotnet-coverage")))))

(deftest coverage-needs-a-restored-dotnet-coverage
  (is (= "sf-cs: dotnet dotnet-coverage --version exited 1; run sf-cs tools to restore the pinned dotnet-coverage"
         (refused-coverage {"FAKE_COVERAGE_TOOL_EXIT" "1"})))
  (doseq [[language wrapper] wrappers]
    (is (= (str "sf-cs: dotnet dotnet-coverage --version exited 1; run " wrapper " tools to restore the pinned dotnet-coverage")
           (refused-coverage {"LANGUAGE" language "FAKE_COVERAGE_TOOL_EXIT" "1"})))))

(def odd-dir "odd/a.b+(c) [d]{e}^$|?*#&x")

(def settings-tree
  {"Root.Tests.csproj" "<Project/>"
   "src/Lib/Lib.csproj" "<Project/>"
   "src/Lib/Account.cs" "class Account {}"
   "Mixed/Mixed.csproj" "<Project/>"
   "Mixed/Mixed.Tests.csproj" "<Project/>"
   "tests/Mtp/Mtp.fsproj" "<Project/>"
   "tests/Broken/Broken.csproj" "<Project/>"
   "Bad/Bad.csproj" "<Project/>"
   (str odd-dir "/Odd.vbproj") "<Project/>"
   "bin/Old/Old.csproj" "<Project/>"
   ".hidden/H.csproj" "<Project/>"})

(def settings-projects
  (sort ["Root.Tests.csproj" "src/Lib/Lib.csproj" "Mixed/Mixed.csproj" "Mixed/Mixed.Tests.csproj"
         "tests/Mtp/Mtp.fsproj" "tests/Broken/Broken.csproj" "Bad/Bad.csproj" (str odd-dir "/Odd.vbproj")]))

(defn- sources [xml section]
  (->> (re-find (re-pattern (str "(?s)<Sources>.*?<" section ">(.*?)</" section ">")) xml)
       second
       (re-seq #"<Source>(.*?)</Source>")
       (mapv #(str/replace (second %) "&amp;" "&"))))

(defn- excluded-modules [xml]
  (->> (re-find #"(?s)<ModulePaths>\s*<Exclude>(.*?)</Exclude>" xml)
       second
       (re-seq #"<ModulePath>(.*?)</ModulePath>")
       (mapv #(str/replace (second %) "&amp;" "&"))))

(defn- evaluation [project]
  ["msbuild" project "-getProperty:IsTestProject" "-getProperty:IsTestingPlatformApplication" "-getProperty:AssemblyName"])

(deftest coverage-settings-leave-out-test-assemblies
  (in-tree settings-tree
    #(let [log (str (fs/path *repo* "tmp-dotnet.log"))
           env {"FAKE_DOTNET_LOG" log
                "FAKE_COVERAGE" (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))
                "FAKE_TEST_PROJECTS" (str "Root.Tests.csproj,Mixed/Mixed.Tests.csproj,tests/Broken/Broken.csproj,"
                                          "Bad/Bad.csproj," odd-dir "/Odd.vbproj")
                "FAKE_MTP_PROJECTS" "tests/Mtp/Mtp.fsproj"
                "FAKE_ASSEMBLY_NAMES" "Mixed/Mixed.Tests.csproj=Mixed.Specs,tests/Mtp/Mtp.fsproj=My.Mtp+Tests"
                "FAKE_RESTORE_FAILS" "Bad/Bad.csproj"
                "FAKE_PROJECT_EXITS" "tests/Broken/Broken.csproj=1"}
           {:keys [status err]} (sfcs env "coverage")
           root *repo*
           rx (str "^" (str/replace root "/" "[\\\\/]") "[\\\\/]")
           xml (slurp (str (fs/path *repo* "tmp" "dotnet-coverage.xml")))]
       (is (= 0 status) err)
       (is (re-matches #"[A-Za-z0-9/_-]+" root) "the root needs no escaping")
       (testing "each walked project is restored, then evaluated when its restore passed; no bare restore"
         (is (= (mapcat (fn [p] (if (= p "Bad/Bad.csproj") [["restore" p]] [["restore" p] (evaluation p)]))
                        settings-projects)
                (->> (dotnet-calls log) (drop 2) butlast vec))))
       (testing "a failed restore is a warning naming the project, whose test code is then scored"
         (is (= 1 (count (re-seq #"warning" err))) err)
         (is (str/includes? err "warning: dotnet restore Bad/Bad.csproj failed")))
       (testing "sources: everything under the root but obj/; no directory is excluded"
         (is (= [(str rx ".*")] (sources xml "Include")))
         (is (= [".*[\\\\/]obj[\\\\/].*"] (sources xml "Exclude"))))
       (testing "one module pattern per evaluated test project, by its assembly name"
         (is (= [".*[\\\\/]Mixed\\.Specs\\.(dll|exe)$"
                 ".*[\\\\/]Root\\.Tests\\.(dll|exe)$"
                 ".*[\\\\/]Odd\\.(dll|exe)$"
                 ".*[\\\\/]My\\.Mtp\\+Tests\\.(dll|exe)$"]
                (excluded-modules xml))))
       (is (not (str/includes? xml "Bad")) "a project whose restore failed is not excluded")
       (testing "the module patterns match test assemblies, dll or exe, under either separator, and nothing else"
         (let [[mixed root-tests _ mtp] (map re-pattern (excluded-modules xml))]
           (is (re-find mixed (str root "/Mixed/bin/Debug/net10.0/Mixed.Specs.dll")))
           (is (re-find mixed "C:\\w\\Mixed\\bin\\Mixed.Specs.exe"))
           (is (not (re-find mixed (str root "/Mixed/bin/Debug/net10.0/Mixed.dll"))))
           (is (not (re-find mixed (str root "/Mixed/bin/Mixed.Specs.dll.config"))))
           (is (not (re-find mixed (str root "/bin/XMixed.Specs.dll"))))
           (is (not (re-find root-tests (str root "/bin/RootxTests.dll"))))
           (is (re-find mtp (str root "/tests/Mtp/bin/My.Mtp+Tests.dll")))
           (is (not (re-find mtp (str root "/tests/Mtp/bin/My.Mtp++Tests.dll"))))))
       (testing "the sources pattern keeps production sources beside or below a test project"
         (let [[include obj] (map re-pattern (concat (sources xml "Include") (sources xml "Exclude")))]
           (is (re-matches include (str root "/Mixed/Calc.cs")))
           (is (re-matches include (str root "/src/Lib/Account.cs")))
           (is (re-matches include (str (str/replace root "/" "\\") "\\src\\Lib\\Account.cs")))
           (is (not (re-matches include (str root "-other/src/Lib/Account.cs"))))
           (is (re-matches obj (str root "/src/Lib/obj/Debug/Gen.cs"))))))))

(defn- stale-report []
  (let [path (fs/path *repo* "tmp" "coverage" "old" "coverage.cobertura.xml")]
    (fs/create-dirs (fs/parent path))
    (spit (str path) "old")
    path))

(deftest coverage-refuses-a-tree-with-nothing-to-measure
  (doseq [[case-name files env] [["no project" {"src/Account.cs" "class Account {}"} {}]
                                 ["only test projects" {"T/T.csproj" "<Project/>" "U/U.csproj" "<Project/>"}
                                  {"FAKE_TEST_PROJECTS" "T/T.csproj" "FAKE_MTP_PROJECTS" "U/U.csproj"}]
                                 ["only test projects among those restored"
                                  {"T/T.csproj" "<Project/>" "P/P.csproj" "<Project/>"}
                                  {"FAKE_TEST_PROJECTS" "T/T.csproj" "FAKE_RESTORE_FAILS" "P/P.csproj"}]]]
    (in-tree files
      #(let [stale (stale-report)
             {:keys [status lines err]} (sfcs (assoc env "FAKE_COVERAGE" (str (fs/path fixtures "coverage" "coverage.cobertura.xml")))
                                              "coverage" "../App.sln")]
         (is (= 2 status) case-name)
         (is (str/includes? err (str "no project to measure under " *repo*)) case-name)
         (is (str/includes? err "run sf-cs coverage from the repository or solution root") case-name)
         (is (not-any? (fn [line] (str/includes? line "collect")) lines) case-name)
         (when (= case-name "no project")
           (is (not-any? (fn [line] (str/starts-with? line "$ dotnet restore")) lines)))
         (is (not (fs/exists? stale)) (str case-name ": the stale report is gone"))
         (is (not (fs/exists? (fs/path *repo* "tmp" "dotnet-coverage.xml"))) case-name)))))

(deftest coverage-creates-each-run-directory-and-replaces-stale-runs
  (in-tree {"a/A.fsproj" (declared "P") "b/B.fsproj" xunit-fsproj}
    #(let [report (str (fs/path fixtures "coverage" "coverage.cobertura.xml"))
           stale (fs/path *repo* "tmp" "coverage" "run-3" "coverage.cobertura.xml")]
       (fs/create-dirs (fs/parent stale))
       (spit (str stale) "x")
       (let [{:keys [status lines err]} (sfcs {"LANGUAGE" "fsharp" "FAKE_COVERAGE" report} "coverage")]
         (is (= 0 status) err)
         (is (= "2 coverage file(s) under tmp/coverage" (last lines))))
       (is (not (fs/exists? stale)))
       (is (= #{"run-1" "run-2"} (set (map (comp str fs/file-name) (fs/list-dir (fs/path *repo* "tmp" "coverage")))))))))

(deftest a-test-project-is-what-msbuild-evaluates-as-one
  (doseq [[evaluation expected]
          [[{:exit 0 :out "{\"Properties\":{\"IsTestProject\":\"true\",\"IsTestingPlatformApplication\":\"\"}}"} true]
           [{:exit 0 :out "{\"Properties\":{\"IsTestProject\":\"\",\"IsTestingPlatformApplication\":\"True\"}}"} true]
           [{:exit 0 :out "{\"Properties\":{\"IsTestProject\":\"false\",\"IsTestingPlatformApplication\":\"\"}}"} false]
           [{:exit 1 :out "{\"Properties\":{\"IsTestProject\":\"true\"}}"} false]
           [{:exit 0 :out "MSBUILD : error MSB1009: Project file does not exist."} false]
           [{:exit 0 :out "[]"} false]]]
    (is (= expected (coverage/test-project? evaluation)) (pr-str evaluation))))
