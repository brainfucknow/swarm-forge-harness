(ns sfjava.runner-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sfjava.runner :as runner]
            [sfcore.shell :as shell]))

(describe "dedicated Java runner"
  (it "prepares with Maven test execution disabled and always compiles before JUnit"
    (let [calls (atom [])]
      (with-redefs [runner/cache (constantly "/cache")
                    shell/exists? (constantly true)
                    shell/available? (constantly true)
                    runner/validate-project (constantly nil)
                    runner/classpath (constantly "classes:tests:deps")
                    shell/run (fn [cmd args & _] (swap! calls conj [cmd args]) 0)]
        (should= 0 (runner/tests "test"))
        (should= "mvn" (ffirst @calls))
        (should (some #{"-DskipTests=true"} (second (first @calls))))
        (should (some #{"test-compile"} (second (first @calls))))
        (should= "java" (first (last @calls)))
        (should (some #{"--exclude-tag"} (second (last @calls)))))))
  (it "does not launch JUnit after compilation fails"
    (let [calls (atom [])]
      (with-redefs [runner/validate-project (constantly nil)
                    runner/cache (constantly "/cache")
                    shell/run (fn [cmd _ & _] (swap! calls conj cmd) 7)]
        (should= 1 (runner/tests "test"))
        (should= ["mvn"] @calls))))
  (it "deletes stale XML and rejects a runner that writes no new coverage"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "coverage-spec"}))]
      (try
        (binding [shell/*dir* dir]
          (shell/write-file runner/coverage-xml "old report")
          (with-redefs [runner/validate-project (constantly nil)
                        runner/cache (constantly "/cache")
                        runner/classpath (constantly "classes")
                        shell/run (constantly 0)]
            (should-throw Exception (runner/coverage))
            (should-not (shell/exists? runner/coverage-xml))))
        (finally (fs/delete-tree dir))))))
