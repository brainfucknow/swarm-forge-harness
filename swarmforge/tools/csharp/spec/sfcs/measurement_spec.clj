(ns sfcs.measurement-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sfcs.core-test :as support]
            [speclj.core :refer :all]))

(defn- source! [path contents]
  (let [file (fs/path support/*repo* path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) contents)))

(defn- method-report [file]
  (str "<coverage><packages><package><classes><class name=\"Ledger\" filename=\"" file
       "\"><methods><method name=\"Withdraw\" complexity=\"4\" line-rate=\"0.25\">"
       "<lines><line number=\"10\" hits=\"1\"/></lines></method></methods>"
       "</class></classes></package></packages></coverage>"))

(defn- run-crap [language & args]
  (let [extension ({"csharp" "cs" "fsharp" "fs" "vbnet" "vb"} language)]
    (source! "reports/coverage.cobertura.xml" (method-report (str "Bank/Ledger." extension)))
    (apply #'support/sfcs {"LANGUAGE" language} "crap" "--base" "main"
           "--report" (str (fs/path support/*repo* "reports")) args)))

(doseq [[language extension] [["csharp" "cs"] ["fsharp" "fs"] ["vbnet" "vb"]]]
  (describe (str language " unmeasured changed source files")
    (around [example] (#'support/seed-repo example))

    (it "lists every unmeasured source and exits with an input error"
      (let [files [(str "Missing A." extension) (str "Missing B." extension)]]
        (doseq [file files] (source! file "executable source\n"))
        (let [{:keys [status lines err]} (run-crap language)
              output (str (str/join "\n" lines) err)]
          (should= 2 status)
          (doseq [file files] (should-contain file output)))))))

(defn- run-report [language xml & args]
  (source! "reports/coverage.cobertura.xml" xml)
  (apply #'support/sfcs {"LANGUAGE" language} "crap" "--base" "main"
         "--report" (str (fs/path support/*repo* "reports")) args))

(defn- no-code [files]
  (mapcat #(vector "--no-executable-code" %) files))

(doseq [[language extension] [["csharp" "cs"] ["fsharp" "fs"] ["vbnet" "vb"]]]
  (describe (str language " explicit coverage evidence")
    (around [example] (#'support/seed-repo example))

    (it "normalizes and displays repeated no-code declarations once"
      (#'support/in-tree {}
        #(let [first-file (str "Api A." extension)
               second-file (str "Api B." extension)]
           (source! first-file "interface only\n")
           (source! second-file "constants only\n")
           (let [{:keys [status lines]} (apply run-report language "<coverage/>"
                                              (no-code [first-file (str (fs/path support/*repo* second-file)) first-file]))]
             (should= 0 status)
             (should= [(str "EXEMPT no executable code: " first-file)
                       (str "EXEMPT no executable code: " second-file)]
                      (filterv (fn [line] (str/starts-with? line "EXEMPT")) lines))))))

    (it "keeps an undeclared third gap as an input error"
      (#'support/in-tree {}
        #(let [files (mapv (fn [name] (str name "." extension)) ["A" "B" "C"])]
           (doseq [file files] (source! file "no code\n"))
           (let [{:keys [status lines]} (apply run-report language "<coverage/>" (no-code (take 2 files)))]
             (should= 2 status)
             (should= [(str "UNMEASURED source: " (last files))]
                      (filterv (fn [line] (str/starts-with? line "UNMEASURED")) lines))))))

    (it "does not let a declaration hide a measured method over the limit"
      (#'support/in-tree {}
        #(let [file (str "Measured." extension)]
           (source! file (str/join "\n" (repeat 12 "code")))
           (let [{:keys [status lines]} (run-report language (method-report file) "--no-executable-code" file)]
             (should= 1 status)
             (should-contain (str "EXEMPT no executable code: " file) lines)
             (should (some (fn [line] (str/starts-with? line "OVER crap")) lines))))))

    (it "refuses invalid declarations instead of silently exempting unrelated files"
      (#'support/in-tree {(str "Unchanged." extension) "unchanged\n" "wrong.txt" "wrong\n"}
        #(do
           (source! (str "Changed." extension) "code\n")
           (doseq [value [(str "Absent." extension) (str "Unchanged." extension)
                          "wrong.txt" (str "*." extension) "../outside"]]
             (let [{:keys [status err]} (run-report language "<coverage/>" "--no-executable-code" value)]
               (should= 2 status)
               (should-contain "--no-executable-code" err)))
           (let [{:keys [status err]} (run-report language "<coverage/>" "--no-executable-code")]
             (should= 2 status)
             (should-contain "Missing value" err)))))

    (it "requires evidence for unchanged files only in all mode and ignores deleted files"
      (#'support/in-tree {(str "Unchanged." extension) "code\n" (str "Deleted." extension) "code\n"}
        #(do
           (fs/delete (fs/path support/*repo* (str "Deleted." extension)))
           (should= 0 (:status (run-report language "<coverage/>")))
           (let [{:keys [status lines]} (run-report language "<coverage/>" "--all")]
             (should= 2 status)
             (should= [(str "UNMEASURED source: Unchanged." extension)]
                      (filterv (fn [line] (str/starts-with? line "UNMEASURED")) lines))))))

    (it "combines method evidence from multiple reports and absolute paths"
      (#'support/in-tree {}
        #(let [a (str "A." extension) b (str "B." extension)]
           (doseq [file [a b]] (source! file (str/join "\n" (repeat 12 "code"))))
           (source! "reports/second/coverage.cobertura.xml" (method-report (str (fs/path support/*repo* b))))
           (let [{:keys [status lines]} (run-report language (method-report a))]
             (should= 1 status)
             (should= 2 (count (filter (fn [line] (str/starts-with? line "OVER crap")) lines)))
             (should-not (some (fn [line] (str/starts-with? line "UNMEASURED")) lines))))))

    (it "includes committed, working-tree and untracked gaps"
      (#'support/in-tree {(str "A." extension) "old\n" (str "B." extension) "old\n"}
        #(let [files (mapv (fn [name] (str name "." extension)) ["A" "B" "C"])]
           (#'support/git "switch" "--quiet" "-c" "task")
           (source! (first files) "changed\n")
           (#'support/git "commit" "--quiet" "-am" "change A")
           (source! (second files) "working\n")
           (source! (last files) "untracked\n")
           (let [{:keys [status lines]} (run-report language "<coverage/>")]
             (should= 2 status)
             (should= (mapv (fn [file] (str "UNMEASURED source: " file)) files)
                      (filterv (fn [line] (str/starts-with? line "UNMEASURED")) lines))))))

    (it "reports gaps even when a measured method exceeds the threshold"
      (#'support/in-tree {}
        #(let [measured (str "Measured." extension) missing (str "Missing." extension)]
           (source! measured (str/join "\n" (repeat 12 "code")))
           (source! missing "unmeasured code\n")
           (let [{:keys [status lines]} (run-report language (method-report measured))]
             (should= 2 status)
             (should-contain (str "UNMEASURED source: " missing) lines)
             (should-not (some (fn [line] (str/includes? line "0 over CRAP")) lines))))))

    (it "rejects a malformed report even when every source is exempted"
      (#'support/in-tree {}
        #(let [file (str "Api." extension)]
           (source! file "interface only\n")
           (should= 2 (:status (run-report language "<broken/>" "--no-executable-code" file)))
           (should= 2 (:status (run-report language "<coverage>" "--no-executable-code" file))))))))
