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
