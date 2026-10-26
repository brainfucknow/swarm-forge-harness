(ns sfcs.core-test
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sfcs.changes :as changes]
            [sfcs.cobertura :as cob]
            [sfcs.jscpd :as jscpd]
            [sfcs.stryker :as stryker]))

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
    (is (= [7 29 30] (vec (get changed "Bank/Ledger.cs"))))))

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
  (is (= 1.0 (cob/crap-score 1 1)))
  (is (= 20.0 (cob/crap-score 4 0)))
  (is (= "10.750" (cob/fmt "%.3f" (cob/crap-score 4 0.25)))))

(deftest cobertura-methods
  (let [methods (cob/parse-cobertura (fixture "coverage" "coverage.cobertura.xml"))]
    (is (= [["Bank.Ledger" "Deposit" "Bank/Ledger.cs" 1.0 1.0 6 8]
            ["Bank.Ledger" "Withdraw" "Bank/Ledger.cs" 4.0 0.25 13 17]
            ["Bank.Ledger" "Balance" "Bank/Ledger.cs" 1.0 1.0 20 22]
            ["Bank.Audit<T>" "Record" "Bank/Audit.cs" 2.0 0.0 4 5]]
           (map (juxt :class-name :name :file :complexity :coverage :first-line :last-line) methods)))))

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

(defn- sfcs [env & args]
  (let [result (apply p/shell {:dir *repo* :out :string :err :string :continue true
                               :extra-env (merge {"PATH" (str *bin* fs/path-separator (System/getenv "PATH"))} env)}
                      "bash" (str (fs/path here ".." ".." "bin" "sf-cs")) args)]
    {:status (:exit result) :lines (vec (str/split-lines (str/trimr (:out result)))) :err (:err result)}))

(defn- seed-repo [f]
  (let [repo (str (fs/create-temp-dir {:prefix "sfcs"}))
        bin (str (fs/path repo "fakebin"))]
    (fs/create-dirs bin)
    (spit (str (fs/path bin "dotnet"))
          (str "#!/usr/bin/env bash\n"
               "printf '%s\\n' \"$@\" > \"$FAKE_DOTNET_LOG\"\n"
               "if [ \"$1\" = test ] && [ -n \"$FAKE_COVERAGE\" ]; then mkdir -p tmp/coverage/run && cp \"$FAKE_COVERAGE\" tmp/coverage/run/coverage.cobertura.xml; fi\n"))
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
