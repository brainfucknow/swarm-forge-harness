(ns sfcs.coverage-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sfcs.core-test :as support]
            [speclj.core :refer :all]))

(doseq [language ["csharp" "fsharp" "vbnet"]]
  (describe (str language " test-assembly coverage")
    (around [example] (#'support/seed-repo example))

    (it "opts a test-only root into collection without passing the wrapper flag to dotnet test"
      (#'support/in-tree {"Unit.csproj" "<Project/>"}
        #(let [log (str (fs/path support/*repo* "dotnet.log"))
               env {"LANGUAGE" language "FAKE_TEST_PROJECTS" "Unit.csproj"
                    "FAKE_DOTNET_LOG" log
                    "FAKE_COVERAGE" (str (fs/path support/fixtures "coverage" "coverage.cobertura.xml"))}]
           (should= 2 (:status (#'support/sfcs env "coverage")))
           (let [{:keys [status]} (#'support/sfcs env "coverage" "--include-test-assemblies" "App.sln" "--no-build")
                 settings (slurp (str (fs/path support/*repo* "tmp/dotnet-coverage.xml")))
                 calls (#'support/dotnet-calls log)]
             (should= 0 status)
             (should-not-contain "ModulePaths" settings)
             (should-contain "<Sources>" settings)
             (should-contain "obj" settings)
             (should= ["App.sln" "--no-build"] (take-last 2 (last calls)))
             (should-not (some (fn [arg] (= "--include-test-assemblies" arg)) (mapcat identity calls)))))))

    (it "keeps an empty root an input error even when test assemblies are included"
      (#'support/in-tree {}
        #(let [{:keys [status err]} (#'support/sfcs {"LANGUAGE" language} "coverage" "--include-test-assemblies")]
           (should= 2 status)
           (should-contain "no project to measure" err))))

    (it "preserves external failures and the caller's arguments after the separator"
      (#'support/in-tree {"Unit.csproj" "<Project/>"}
        #(let [log (str (fs/path support/*repo* "dotnet.log"))
               {:keys [status]} (#'support/sfcs
                                 {"LANGUAGE" language "FAKE_TEST_PROJECTS" "Unit.csproj"
                                  "FAKE_DOTNET_LOG" log "FAKE_EXIT" "7"}
                                 "coverage" "--include-test-assemblies" "--" "--no-build" "--logger" "trx")]
           (should= 1 status)
           (should= ["--" "--no-build" "--logger" "trx"]
                    (take-last 4 (last (#'support/dotnet-calls log)))))))))
