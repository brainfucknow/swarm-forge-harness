(ns sfjava.steps
  "Public Java CLI contract fixtures, using the existing APS step runtime."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfjava.acceptance :as acceptance]))

(def harness (str (fs/canonicalize (fs/path acceptance/java-dir ".." ".." ".."))))
(def fixtures (str (fs/path acceptance/java-dir "fixtures")))

(defn- check [truth message]
  (when-not truth (throw (ex-info message {}))))

(defn- write [path content]
  (fs/create-dirs (fs/parent path))
  (spit (str path) content))

(defn- process [world args]
  (apply process/shell {:dir (:repo world) :out :string :err :string :continue true :extra-env (:env world)} args))

(defn- git [world & args]
  (let [r (process world (into [(str (fs/which "git"))] args))]
    (check (zero? (:exit r)) (str "git failed: " (:err r)))
    (str/trim (:out r))))

(defn- configure [world values]
  (let [file (:state world)
        state (json/parse-string (slurp file) true)]
    (spit file (json/generate-string (merge state values)))
    world))

(defn- calls [world]
  (if (fs/exists? (:log world))
    (map #(json/parse-string % true) (str/split-lines (slurp (:log world)))) []))

(defn- run [world args]
  (assoc world :result (process world args)))

(defn- sf [world & args]
  (run world (into [(str (fs/path (:repo world) "swarmforge/bin/sf"))] args)))

(defn- java [world args]
  (run world (into [(str (fs/path (:repo world) "swarmforge/bin/sf-java"))] (str/split args #"\s+"))))

(def pom "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><groupId>demo</groupId><artifactId>ledger</artifactId><version>1</version><properties><maven.compiler.source>17</maven.compiler.source><maven.compiler.target>17</maven.compiler.target></properties><dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.10.2</version><scope>test</scope></dependency></dependencies></project>")
(def source "package demo;\npublic class Ledger {\n public int value() {\n  return 1;\n }\n}\n")
(def test-source "package demo;\nimport org.junit.jupiter.api.*;\nclass LedgerTest { @Test void ordinary() { Assertions.assertEquals(1,new Ledger().value()); } @Test @Tag(\"property\") void property() { Assertions.assertTrue(new Ledger().value()>0); } }\n")

(defn- upstreams [world]
  (into {} (for [tool ["crap4java" "dry4java" "mutate4java"]
                 :let [dest (str (fs/path (:dir world) tool))
                       _ (fs/copy-tree (fs/path fixtures "upstream" tool) dest)
                       w (assoc world :repo dest)]]
             (do
               (git w "init" "-q")
               (git w "add" ".")
               (git w "-c" "user.name=Fixture" "-c" "user.email=fixture@example.com" "commit" "-q" "-m" "upstream fixture")
               [tool dest]))))

(defn fresh []
  (let [root (str (fs/path harness "tmp" "sf-java" "worlds"))
        _ (fs/create-dirs root)
        dir (str (fs/create-temp-dir {:dir root :prefix "contract"}))
        repo (str (fs/path dir "project with spaces"))
        bin (str (fs/path dir "bin"))
        system (str (fs/path dir "system"))
        log (str (fs/path dir "calls.jsonl"))
        state (str (fs/path dir "state.json"))
        _ (doseq [p [repo bin system]] (fs/create-dirs p))
        _ (doseq [cmd ["bash" "sh" "bb" "cat" "dirname" "mkdir" "cp" "basename" "touch" "grep" "tail" "chmod" "env"]]
            (fs/create-sym-link (fs/path system cmd) (fs/which cmd)))
        world {:dir dir :repo repo :bin bin :log log :state state
               :env {"PATH" (str bin ":" system) "SFJAVA_FAKE_STATE" state "GIT_CONFIG_NOSYSTEM" "1"}}
        _ (spit state (json/generate-string {:log log :real_git (str (fs/which "git"))}))
        upstream (upstreams world)]
    (doseq [cmd ["java" "mvn" "git"]]
      (fs/create-sym-link (fs/path bin cmd) (fs/path fixtures "fake-tool.py")))
    (configure world {:upstreams upstream})
    (git world "init" "-q" "--initial-branch=main")
    (git world "config" "user.name" "Fixture")
    (git world "config" "user.email" "fixture@example.com")
    (let [installed (process world [(str (fs/path harness "install.sh")) repo])]
      (check (zero? (:exit installed)) (str "installation failed: " (:out installed) (:err installed))))
    (write (fs/path repo "pom.xml") pom)
    (write (fs/path repo "src/main/java/demo/Ledger.java") source)
    (write (fs/path repo "src/main/java/demo/Other.java") (str/replace source "Ledger" "Other"))
    (write (fs/path repo "src/test/java/demo/LedgerTest.java") test-source)
    (let [catalogue (str (fs/path repo "swarmforge/tools.conf"))
          pins (into {} (for [[tool path] upstream] [tool (git (assoc world :repo path) "rev-parse" "HEAD")]))]
      (spit catalogue (reduce (fn [text [tool sha]]
                               (str/replace text (re-pattern (str "(?m)^(" tool "\\s+maven\\s+\\S+\\s+)\\S+")) (str "$1" sha)))
                             (slurp catalogue) pins))
      (git world "add" ".")
      (git world "commit" "-q" "-m" "base")
      (git world "switch" "-q" "-c" "task")
      (spit log "")
      (assoc world :pins pins :upstreams upstream))))

(defn cleanup [world] (fs/delete-tree (:dir world)))

(defn- ensure-tools [world]
  (doseq [tool ["crap4java" "dry4java" "mutate4java"]]
    (let [r (:result (sf world "tool" "ensure" tool))]
      (check (zero? (:exit r)) (str "ensure failed: " (:out r) (:err r)))))
  (spit (:log world) "")
  world)

(defn- changed-source [world]
  (write (fs/path (:repo world) "src/main/java/demo/Ledger.java")
         (str/replace source "return 1" "return 2"))
  world)

(defn- result-output [world]
  (str (get-in world [:result :out]) (get-in world [:result :err])))

(defn- expect-output [world text]
  (check (str/includes? (result-output world) text)
         (str "expected output " (pr-str text) ", got " (pr-str (:result world))))
  world)

(defn- expect-status [world expected]
  (check (= expected (get-in world [:result :exit])) (str "expected exit " expected ", got " (:result world)))
  world)

(defn- runner-calls [world]
  (filter #(and (= "java" (:tool %)) (some (fn [s] (str/includes? s "console-standalone")) (:args %))) (calls world)))

(defn- no-runner [world]
  (check (empty? (runner-calls world)) "dedicated runner unexpectedly started") world)

(defn- no-maven-tests [world]
  (doseq [{:keys [args]} (filter #(= "mvn" (:tool %)) (calls world))]
    (check (some #{"-DskipTests=true" "-Dmaven.test.skip=true"} args) (str "unsafe Maven invocation " args))
    (check (not-any? #{"test" "verify"} args) (str "Maven test lifecycle invoked: " args))) world)

(defn- no-manifest [world]
  (check (= (slurp (str (fs/path (:repo world) "src/main/java/demo/Ledger.java")))
            (str/replace source "return 1" "return 2")) "source was changed by mutation") world)

(def step-handlers
  [[#"a single-module Maven Java project in a task worktree" (fn [w] w)]
   [#"controllable Java, Maven and JUnit tools on PATH" (fn [w] w)]
   [#"controllable pinned Java quality tools are installed through sf tool" ensure-tools]
   [#"the project has a passing ordinary JUnit test covering production code" (fn [w] w)]
   [#"the project has a passing JUnit test tagged property" (fn [w] w)]
   [#"an ordinary JUnit test fails" #(configure % {:test_failure true})]
   [#"the Java sources fail compilation" #(configure % {:compile_failure true})]
   [#"the project has \"(.+)\"" (fn [w problem]
                                    (case problem
                                      "no pom.xml" (fs/delete (fs/path (:repo w) "pom.xml"))
                                      "Java older than 17" (configure w {:old_java true})
                                      "no Maven executable" (fs/delete (fs/path (:bin w) "mvn"))
                                      "a Maven reactor root" (write (fs/path (:repo w) "pom.xml") "<project><modules><module>child</module></modules></project>")) w)]
   [#"I run sf-java with \"(.+)\"(?: in that project)?" java]
   [#"the exit status is (\d+)" (fn [w n] (expect-status w (parse-long n)))]
   [#"the exit status is nonzero" (fn [w] (check (not (zero? (get-in w [:result :exit]))) "expected failure") w)]
   [#"the dedicated runner executes \"(.+)\" tests" (fn [w group]
                                                             (let [actual (mapv second (re-seq #"RUN (\w+) tests" (result-output w)))]
                                                               (check (= [group] actual) (str "expected test group " group ", got " actual))) w)]
   [#"Maven does not execute tests" no-maven-tests]
   [#"the output identifies the failing test" #(expect-output % "LedgerTest failed")]
   [#"the dedicated runner was not started" no-runner]
   [#"the error output identifies \"(.+)\"" expect-output]
   [#"a stale JaCoCo XML report is (present|absent)" (fn [w state] (when (= "present" state) (write (fs/path (:repo w) "target/site/jacoco/jacoco.xml") "stale")) w)]
   [#"a successful dedicated coverage run writes no new JaCoCo XML report" #(configure % {:no_coverage true})]
   [#"the error output identifies the missing new coverage report" #(expect-output % "missing new coverage report")]
   [#"a new JaCoCo XML report records the covered production code" (fn [w] (check (str/includes? (slurp (str (fs/path (:repo w) "target/site/jacoco/jacoco.xml"))) "covered=\"3\"") "no production coverage") w)]
   [#"the output identifies the coverage report path" #(expect-output % "target/site/jacoco/jacoco.xml")]
   [#"a changed production method has tool-reported CRAP ([\d.]+)" (fn [w score] (configure (changed-source w) {:crap (parse-double score)}))]
   [#"the output identifies the method with CRAP ([\d.]+)" (fn [w score] (expect-output w (str "ledger demo.Ledger 3 100.0% " score "\n")))]
   [#"the coverage run uses the dedicated runner without property tests" (fn [w] (check (seq (runner-calls w)) "no runner") (check (every? #(some #{"--exclude-tag"} (:args %)) (runner-calls w)) "property tests not excluded") w)]
   [#"a production Java file has a (committed|uncommitted|untracked) change from main" (fn [w change]
                                                                                    (if (= "untracked" change)
                                                                                      (write (fs/path (:repo w) "src/main/java/demo/New.java") source)
                                                                                      (changed-source w))
                                                                                    (when (= "committed" change) (git w "add" ".") (git w "commit" "-q" "-m" "change")) w)]
   [#"the tool reports a CRAP score above ten for that file" #(configure % {:crap 11.0})]
   [#"the output identifies that production Java file" #(expect-output % "src/main/java/demo/")]
   [#"only \"(.+)\" changed from main" (fn [w path] (write (fs/path (:repo w) path) "changed\n") w)]
   [#"mutate4java was not started" (fn [w] (check (not-any? #(some (fn [a] (str/includes? a "mutate4java-")) (:args %)) (calls w)) "mutation started") w)]
   [#"the output states that no production Java files require mutation" #(expect-output % "no production Java files require mutation")]
   [#"dry4java reports a duplicate whose left declaration is (changed|unchanged)" (fn [w side] (configure (changed-source w) {:left side}))]
   [#"its right declaration is (changed|unchanged)" (fn [w side] (configure w {:right side}))]
   [#"the output reports (\d+) relevant duplicates" (fn [w n] (expect-output w (str n " relevant duplicates")))]
   [#"a production Java file changed from main" changed-source]
   [#"mutate4java reports (\d+) surviving mutants" (fn [w n] (configure w {:survivors (parse-long n)}))]
   [#"mutate4java uses differential manifest selection with four workers" (fn [w]
                                                                         (let [c (first (filter #(some (fn [a] (str/includes? a "mutate4java-")) (:args %)) (calls w)))]
                                                                           (check (some #{"--max-workers"} (:args c)) "no worker limit")
                                                                           (check (some #{"4"} (:args c)) "wrong workers")
                                                                           (check (not-any? #{"--mutate-all"} (:args c)) "full mutation requested")) w)]
   [#"mutant tests recompile the mutated source and use the dedicated runner" (fn [w] (check (>= (count (runner-calls w)) 3) "mutant runner was not called")
                                                                                   (check (>= (count (filter #(and (= "mvn" (:tool %)) (some #{"test-compile"} (:args %))) (calls w))) 3) "mutants were not recompiled") w)]
   [#"property tests are excluded" (fn [w] (check (every? #(some #{"--exclude-tag"} (:args %)) (runner-calls w)) "property tests included") w)]
   [#"the output reports (\d+) surviving mutants" (fn [w n] (expect-output w (str n " survived")))]
   [#"no embedded mutation manifest remains in tracked Java source" no-manifest]
   [#"mutation encounters a failing baseline" #(configure % {:baseline_failure true})]
   [#"production source content is unchanged from before the command" no-manifest]
   [#"the application pom.xml is unchanged" (fn [w] (check (= pom (slurp (str (fs/path (:repo w) "pom.xml")))) "pom changed") w)]
   [#"the quality tool cannot produce valid analysis because of \"(.+)\"" (fn [w problem]
                                                                           (changed-source w)
                                                                           (case problem
                                                                             "missing coverage data" (configure w {:no_coverage true})
                                                                             "malformed tool report" (configure w {:tool_failure true})
                                                                             "missing tool executable" (fs/delete (fs/path (:repo w) ".swarmforge/bin/mutate4java"))) w)]
   [#"the error output identifies the analysis failure" #(expect-output % "analysis failure")]
   [#"a fresh SwarmForge consumer project" (fn [w] w)]
   [#"a controllable upstream checkout of \"(.+)\" at its configured commit" (fn [w tool] (assoc w :tool tool))]
   [#"I run \"sf tool ensure (.+)\" in the consumer project" (fn [w tool] (assoc (sf w "tool" "ensure" tool) :tool tool))]
   [#"the installed wrapper runs the patched Java JAR" (fn [w]
                                                       (let [tool (:tool w)
                                                             result (process w [(str (fs/path (:repo w) ".swarmforge/bin" tool)) "--help"])]
                                                         (check (zero? (:exit result)) "wrapper not executable")
                                                         (check (some #(and (= "java" (:tool %)) (some (fn [a] (str/includes? a (str tool "-0.1.0-SNAPSHOT.jar"))) (:args %))) (calls w)) "not a Java JAR")) w)]
   [#"the output identifies the configured upstream commit" (fn [w] (expect-output w (str "@" (get (:pins w) (:tool w))))) ]
   [#"\"sf tool require (.+)\" succeeds" (fn [w tool] (expect-status (sf w "tool" "require" tool) 0) w)]
   [#"the pinned Java tool source does not accept its required compatibility patch" (fn [w]
                                                                                     (let [up (get (:upstreams w) "crap4java")
                                                                                           uw (assoc w :repo up)]
                                                                                       (write (fs/path up "src/crap4java/CoverageRunner.java") "incompatible source")
                                                                                       (git uw "add" ".")
                                                                                       (git uw "-c" "user.name=Fixture" "-c" "user.email=fixture@example.com" "commit" "-q" "-m" "incompatible")
                                                                                       (let [pin (git uw "rev-parse" "HEAD")
                                                                                             cat (str (fs/path (:repo w) "swarmforge/tools.conf"))]
                                                                                         (spit cat (str/replace (slurp cat) (get (:pins w) "crap4java") pin))))
                                                                                     (assoc w :tool "crap4java"))]
   [#"I install that Java tool through sf tool ensure" #(sf % "tool" "ensure" (:tool %))]
   [#"the error output identifies the patch failure" #(expect-output % "compatibility patch failure")]
   [#"no wrapper is published for that Java tool" (fn [w] (check (not (fs/exists? (fs/path (:repo w) ".swarmforge/bin" (:tool w)))) "wrapper was published") w)]
   [#"a SwarmForge consumer project with the patched Java tools installed" ensure-tools]
   [#"I ensure the same Java tools again" (fn [w] (ensure-tools w) (assoc w :result {:exit 0 :out "" :err ""}))]
   [#"the installed tools remain usable at their configured commits" (fn [w] (doseq [tool ["crap4java" "dry4java" "mutate4java"]] (expect-status (sf w "tool" "require" tool) 0)) w)]
   [#"a new Git project with a passing Maven JUnit 5 application" (fn [w] w)]
   [#"I install SwarmForge into that project with install.sh" (fn [w] (run w [(str (fs/path harness "install.sh")) (:repo w)]))]
   [#"the ordinary JUnit tests pass through the dedicated runner" #(expect-output % "RUN ordinary tests")]
   [#"no runtime dependency is read from the harness source checkout" (fn [w]
                                                                    (doseq [c (calls w) :when (= "java" (:tool c))
                                                                            arg (:args c)]
                                                                      (check (not (str/includes? arg (str harness "/swarmforge/"))) "borrowed source checkout runtime")) w)]])

(def handlers {:fresh fresh :cleanup cleanup :steps step-handlers})
