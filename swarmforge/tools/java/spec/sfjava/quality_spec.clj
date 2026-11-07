(ns sfjava.quality-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [speclj.core :refer :all]
            [sfcore.shell :as shell]
            [sfcore.git :as git]
            [sfjava.quality :as quality]
            [sfjava.runner :as runner]))

(describe "Java duplicate gate"
  (it "fails only duplicates that overlap changed production lines"
    (let [candidate {:score 0.9 :left {:file "src/main/java/A.java" :start-line 2 :end-line 4}
                     :right {:file "src/main/java/B.java" :start-line 8 :end-line 10}}]
      (with-redefs [runner/validate-project (constantly nil)
                    quality/tool (constantly "dry4java")
                    quality/changed (constantly {"src/main/java/A.java" #{3}})
                    git/repo-root (constantly nil)
                    shell/capture (constantly {:exit 0 :out (pr-str {:candidates [candidate]})})]
        (binding [*out* (java.io.StringWriter.)]
          (should= 1 (quality/dry {}))
          (with-redefs [quality/changed (constantly {"src/main/java/A.java" #{20}})]
            (should= 0 (quality/dry {})))))))
  (it "rejects an unsuccessful external analysis"
    (with-redefs [runner/validate-project (constantly nil)
                  quality/tool (constantly "dry4java")
                  quality/changed (constantly {})
                  shell/capture (constantly {:exit 1 :out ""})]
      (should-throw Exception "analysis failure: dry4java failed" (quality/dry {})))))

(describe "Java mutation cleanup"
  (it "restores original sources and pom when external execution fails"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "restore-spec"}))
          file "src/main/java/A.java"]
      (try
        (binding [shell/*dir* dir]
          (shell/write-file file "original source\n")
          (shell/write-file "pom.xml" "original pom\n")
          (with-redefs [runner/validate-project (constantly nil)
                        quality/changed (constantly {file #{1}})
                        quality/tool (constantly "fake-tool")
                        runner/coverage (constantly 0)
                        runner/cache (constantly "cache")
                        quality/runner-command (constantly "runner")
                        process/shell (fn [& _]
                                        (spit (shell/in-dir file) "corrupted")
                                        (spit (shell/in-dir "pom.xml") "corrupted")
                                        (throw (ex-info "external failure" {})))]
            (should-throw Exception (quality/mutate {:base "main"}))
            (should= "original source\n" (slurp (shell/in-dir file)))
            (should= "original pom\n" (slurp (shell/in-dir "pom.xml")))))
        (finally (fs/delete-tree dir))))))
