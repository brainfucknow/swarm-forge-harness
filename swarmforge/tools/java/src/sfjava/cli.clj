(ns sfjava.cli
  "sf-java command dispatch."
  (:require [babashka.fs :as fs]
            [sfcore.shell :as shell]
            [sfjava.policy :as policy]
            [sfjava.quality :as quality]
            [sfjava.runner :as runner]))

(def usage "usage: sf-java <tools|test|property|coverage|crap|dry|mutate> [--base REF]

  tools       install pinned quality tools and JUnit / JaCoCo runtime JARs
  test        compile, then dedicated JUnit 5 ordinary tests (exclude property)
  property    compile, then only tests tagged property
  coverage    ordinary tests with fresh target/site/jacoco/jacoco.xml
  crap        pinned crap4java on changed production Java files; CRAP <= 10
  dry         pinned dry4java; fail duplicates touching changed source lines
  mutate      differential mutate4java on changed production files, four workers

Run next to a single-module pom.xml, with Java 17+, Maven and Babashka on PATH.
--base is available for quality commands; defaults to SWARMFORGE_BASE, main,
then master. Exit 0: pass; 1: test/build failure or findings; 2: invalid input,
missing prerequisites or analysis failure. See swarmforge/languages/java.md.")

(defn- tools []
  (runner/validate-project)
  (let [sf (str (fs/canonicalize (fs/path (System/getProperty "sfjava.home") ".." ".." "bin" "sf")))]
    (doseq [tool ["crap4java" "dry4java" "mutate4java"]]
      (when-not (zero? (shell/run sf ["tool" "ensure" tool]))
        (throw (ex-info (str "cannot install " tool) {})))
      (when-not (zero? (shell/run sf ["tool" "require" tool]))
        (throw (ex-info (str "cannot require " tool) {}))))
    (runner/install-runtime)
    0))

(defn main [argv]
  (let [{:keys [command] :as options} (policy/options argv)]
    (case command
      "help" (do (println usage) 0)
      "tools" (tools)
      "test" (runner/tests "test")
      "property" (runner/tests "property")
      "coverage" (runner/coverage)
      "crap" (quality/crap options)
      "dry" (quality/dry options)
      "mutate" (quality/mutate options))))

(defn -main [& argv] (shell/run-main "sf-java" main argv))
