(ns sfjava.quality-hardening-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [speclj.core :refer :all]
            [sfcore.shell :as shell]
            [sfcore.git :as git]
            [sfjava.quality :as quality]
            [sfjava.runner :as runner]))

(describe "Java quality orchestration"
  (it "requires executable tools and selects explicit or default base references"
    (with-redefs [quality/tool-bin (constantly "/tools") fs/executable? (constantly true)]
      (should= "/tools/dry4java" (quality/tool "dry4java")))
    (with-redefs [quality/tool-bin (constantly "/tools") fs/executable? (constantly false)]
      (should-throw Exception "analysis failure: missing tool executable dry4java; run sf-java tools"
                    (quality/tool "dry4java")))
    (with-redefs [git/default-base (constantly "default-base") git/collect-changed-lines identity]
      (should= {:base "topic" :pathspecs ["*.java"]} (quality/changed {:base "topic"}))
      (should= {:base "default-base" :pathspecs ["*.java"]} (quality/changed {}))))
  (it "skips absent production changes without invoking analysis or coverage"
    (with-redefs [runner/validate-project (constantly nil)
                  quality/changed (constantly {})
                  quality/tool (fn [_] (throw (ex-info "unnecessary tool" {})))
                  runner/coverage (fn [] (throw (ex-info "unnecessary coverage" {})))]
      (binding [*out* (java.io.StringWriter.)]
        (should= 0 (quality/crap {}))
        (should= 0 (quality/mutate {})))))
  (it "maps CRAP findings while retaining the process status and coverage command"
    (with-redefs [runner/validate-project (constantly nil)
                  quality/changed (constantly {"src/main/java/A.java" #{1}})
                  shell/exists? (constantly true)
                  quality/tool (constantly "crap4java")
                  runner/cache (constantly "cache")
                  quality/runner-command #(str "dedicated-" %)
                  process/shell (fn [options command & files]
                                  (should= true (:continue options))
                                  (should= "dedicated-coverage" (get-in options [:extra-env "SFJAVA_COVERAGE_COMMAND"]))
                                  (should= "crap4java" command)
                                  (should= ["src/main/java/A.java"] files)
                                  {:exit 2 :out "CRAP Report\nf C 4 0.0% 10.1\n"})]
      (binding [*out* (java.io.StringWriter.)] (should= 1 (quality/crap {})))))
  (it "honors failed baseline coverage and successful mutation across files"
    (with-redefs [runner/validate-project (constantly nil)
                  quality/changed (constantly {"src/main/java/A.java" #{1}})
                  quality/files (constantly ["src/main/java/A.java"])
                  quality/tool (constantly "mutate4java")
                  runner/coverage (constantly 7)]
      (should= 7 (quality/mutate {})))
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "mutation-result"}))]
      (try
        (binding [shell/*dir* dir *out* (java.io.StringWriter.)]
          (shell/write-file "src/main/java/A.java" "original")
          (shell/write-file "pom.xml" "pom")
          (with-redefs [runner/validate-project (constantly nil)
                        quality/changed (constantly {"src/main/java/A.java" #{1}})
                        quality/tool (constantly "mutate4java")
                        runner/coverage (constantly 0)
                        runner/cache (constantly "cache")
                        quality/runner-command (constantly "runner")
                        process/shell (fn [options & _]
                                        (should= true (:continue options))
                                        {:exit 0 :out "Summary: 2 killed, 0 survived, 2 total."})]
            (should= 0 (quality/mutate {}))))
        (finally (fs/delete-tree dir))))))
