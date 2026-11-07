(ns sfjava.quality-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [speclj.core :refer :all]
            [sfcore.shell :as shell]
            [sfjava.quality :as quality]
            [sfjava.runner :as runner]))

(describe "Java mutation cleanup"
  (it "restores original sources and pom when external execution fails"
    (let [dir (str (fs/create-temp-dir {:dir "tmp" :prefix "restore-spec"}))
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
