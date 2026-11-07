(ns sf.maven-tool-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.maven-tool :as maven]
            [sf.shell :as shell]))

(describe "Maven JAR tool acquisition"
  (it "validates patches before applying and publishes only a completed JAR build"
    (let [calls (atom [])]
      (with-redefs [fs/exists? (constantly true)
                    shell/run (fn [_ & args] (swap! calls conj (vec args)) {:exit 0 :out "" :err ""})]
        (should= "source/target/crap4java-0.1.0-SNAPSHOT.jar"
                 (maven/build "source" "crap4java" "patches" "cache"))
        (should= ["git" "apply" "--check" "patches/crap4java.patch"] (first @calls))
        (should= ["git" "apply" "patches/crap4java.patch"] (second @calls))
        (should (some #{"-Dmaven.test.skip=true"} (last @calls)))
        (should (some #{"package"} (last @calls))))))
  (it "fails before building when patch validation fails"
    (let [calls (atom [])]
      (with-redefs [fs/exists? (constantly true)
                    shell/run (fn [_ & args] (swap! calls conj (vec args)) {:exit 1 :out "bad" :err "patch rejected"})]
        (should-throw Exception "compatibility patch failure for crap4java: patch rejected"
                      (maven/build "source" "crap4java" "patches" "cache"))
        (should= 1 (count @calls))))))

(describe "Maven development overrides"
  (it "builds supplied sources without changing them with patches"
    (let [calls (atom [])]
      (with-redefs [fs/exists? (constantly true)
                    shell/run (fn [_ & args] (swap! calls conj (vec args)) {:exit 0 :out "" :err ""})]
        (maven/build "source" "dry4java" "patches" "cache" false)
        (should= 1 (count @calls))
        (should= "mvn" (ffirst @calls))))))
