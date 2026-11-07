(ns sfjava.validation-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sfcore.shell :as shell]
            [sfjava.runner :as runner]))

(describe "Java project validation"
  (with project (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "validation-spec"})))
  (after (fs/delete-tree @project))
  (it "rejects missing and reactor POMs before launching processes"
    (binding [shell/*dir* @project]
      (should-throw Exception "no pom.xml in the current directory" (runner/validate-project))
      (shell/write-file "pom.xml" "<project><modules><module>child</module></modules></project>")
      (should-throw Exception "a Maven reactor root is unsupported; run in one module"
                    (runner/validate-project))))
  (it "names missing prerequisites and rejects old Java runtimes"
    (binding [shell/*dir* @project]
      (shell/write-file "pom.xml" "<project/>")
      (with-redefs [shell/available? #(not= "mvn" %)]
        (should-throw Exception "no Maven executable on PATH" (runner/validate-project)))
      (with-redefs [shell/available? (constantly false)]
        (should-throw Exception "no java executable on PATH" (runner/validate-project)))
      (with-redefs [shell/available? (constantly true)
                    shell/capture (constantly {:exit 0 :out "java version \"1.8.0\""})]
        (should-throw Exception "Java older than 17 or unrecognized Java version"
                      (runner/validate-project)))))
  (it "accepts supported version output and its stdout fallback"
    (binding [shell/*dir* @project]
      (shell/write-file "pom.xml" "<project/>")
      (with-redefs [shell/available? (constantly true)
                    shell/capture (constantly {:exit 0 :out "openjdk version \"17.0.1\""})]
        (should-not-throw (runner/validate-project)))
      (with-redefs [shell/available? (constantly true)
                    shell/capture (fn [_ args]
                                    {:exit 0 :out (if (= ["--version"] args) "openjdk 21.0.1" "")})]
        (should-not-throw (runner/validate-project))))))
