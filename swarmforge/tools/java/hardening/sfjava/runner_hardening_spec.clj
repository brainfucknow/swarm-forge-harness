(ns sfjava.runner-hardening-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sfjava.runner :as runner]
            [sfcore.shell :as shell]))

(describe "Java runtime preparation and outcomes"
  (with project (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "runner-outcome"})))
  (after (fs/delete-tree @project))
  (it "uses the configured cache or a project-local default and preserves classpath entries"
    (binding [shell/*dir* @project]
      (should= (or (System/getenv "SFJAVA_CACHE") (str (fs/absolutize (fs/path @project ".swarmforge/java"))))
               (runner/cache))
      (shell/write-file "target/sf-java/classpath.txt" "  /deps/junit.jar  \n")
      (should= (str "target/classes" fs/path-separator "target/test-classes" fs/path-separator "/deps/junit.jar")
               (runner/classpath))
      (shell/write-file "target/sf-java/classpath.txt" "\n")
      (should= (str "target/classes" fs/path-separator "target/test-classes") (runner/classpath))))
  (it "reuses cached runtime jars and reports download failures"
    (let [calls (atom [])]
      (with-redefs [runner/cache (constantly @project)
                    fs/exists? (constantly true)
                    runner/maven #(do (swap! calls conj %) 0)]
        (runner/install-runtime)
        (should= [] @calls))
      (with-redefs [runner/cache (constantly @project)
                    fs/exists? (constantly false)
                    runner/maven #(do (swap! calls conj %) 1)]
        (should-throw Exception "cannot install org.junit.platform:junit-platform-console-standalone:1.10.2"
                      (runner/install-runtime))
        (should= 1 (count @calls)))))
  (it "runs ordinary tests without instrumentation and maps runner failure to status one"
    (let [calls (atom [])]
      (with-redefs [runner/validate-project (constantly nil)
                    runner/prepare (constantly 0)
                    runner/install-runtime (constantly nil)
                    runner/classpath (constantly "classes")
                    shell/run (fn [_ args] (swap! calls conj args) 9)]
        (should= 1 (runner/tests "test"))
        (should-not (some #(clojure.string/starts-with? % "-javaagent:") (first @calls))))))
  (it "does not run coverage after compile failure or failed tests"
    (with-redefs [runner/validate-project (constantly nil)
                  runner/prepare (constantly 3)]
      (binding [shell/*dir* @project] (should= 1 (runner/coverage))))
    (with-redefs [runner/validate-project (constantly nil)
                  runner/prepare (constantly 0)
                  runner/install-runtime (constantly nil)
                  runner/classpath (constantly "classes")
                  shell/run (constantly 8)]
      (binding [shell/*dir* @project] (should= 1 (runner/coverage)))))
  (it "instruments a passing ordinary run and requires a successful fresh report"
    (let [calls (atom [])]
      (binding [shell/*dir* @project *out* (java.io.StringWriter.)]
        (with-redefs [runner/validate-project (constantly nil)
                      runner/prepare (constantly 0)
                      runner/install-runtime (constantly nil)
                      runner/classpath (constantly "classes")
                      shell/run (fn [_ args]
                                  (swap! calls conj args)
                                  (when (some #{"report"} args)
                                    (shell/write-file runner/coverage-xml "fresh coverage"))
                                  0)]
          (should= 0 (runner/coverage))
          (should (some #(clojure.string/starts-with? % "-javaagent:") (first @calls)))
          (should (some #{"--exclude-tag"} (first @calls)))
          (should= "fresh coverage" (slurp (shell/in-dir runner/coverage-xml)))))
      (binding [shell/*dir* @project]
        (with-redefs [runner/validate-project (constantly nil)
                      runner/prepare (constantly 0)
                      runner/install-runtime (constantly nil)
                      runner/classpath (constantly "classes")
                      shell/run (fn [_ args] (if (some #{"report"} args) 7 0))]
          (should-throw Exception "JaCoCo report generation failed" (runner/coverage)))))))
