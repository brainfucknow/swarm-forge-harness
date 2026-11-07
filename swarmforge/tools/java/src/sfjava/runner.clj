(ns sfjava.runner
  "Maven preparation and dedicated JUnit Platform / JaCoCo processes."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sfcore.shell :as shell]
            [sfjava.policy :as policy]))

(def coverage-xml "target/site/jacoco/jacoco.xml")
(def coverage-exec "target/jacoco.exec")
(def dependency-plugin "org.apache.maven.plugins:maven-dependency-plugin:3.8.1")
(def console-jar "junit-platform-console-standalone-1.10.2.jar")
(def agent-jar "org.jacoco.agent-0.8.12-runtime.jar")
(def jacoco-jar "org.jacoco.cli-0.8.12-nodeps.jar")

(defn cache []
  (or (System/getenv "SFJAVA_CACHE")
      (str (fs/absolutize (shell/in-dir ".swarmforge/java")))))

(defn- require-command [command]
  (when-not (shell/available? command)
    (throw (ex-info (str "no " (if (= "mvn" command) "Maven" command) " executable on PATH") {}))))

(defn- validate-layout []
  (when-not (shell/exists? "pom.xml") (throw (ex-info "no pom.xml in the current directory" {})))
  (when (re-find #"<(?:\w+:)?modules[\s>]" (slurp (shell/in-dir "pom.xml")))
    (throw (ex-info "a Maven reactor root is unsupported; run in one module" {}))))

(defn- validate-java []
  (let [{:keys [exit out]} (shell/capture "java" ["-version"])]
    ;; java -version writes stderr on common JDKs; use --version for stdout.
    (when-not (and (zero? exit) (policy/supported-java? out))
      (let [{:keys [exit out]} (shell/capture "java" ["--version"])
            version (or (some-> (re-find #"(?:openjdk|java)\s+(\d+)" out) second parse-long) 0)]
        (when-not (and (zero? exit) (>= version 17))
          (throw (ex-info "Java older than 17 or unrecognized Java version" {})))))))

(defn validate-project []
  (validate-layout)
  (doseq [command ["java" "mvn"]] (require-command command))
  (validate-java))

(defn maven [args]
  (shell/run "mvn" (into ["-B" "-q" (str "-Dmaven.repo.local=" (cache) "/m2")
                          "-DskipTests=true" "-DskipITs=true"] args)))

(defn- jar-path [jar] (str (fs/path (cache) "lib" jar)))

(defn install-runtime []
  (doseq [[artifact filename] [["org.junit.platform:junit-platform-console-standalone:1.10.2" console-jar]
                               ["org.jacoco:org.jacoco.agent:0.8.12:jar:runtime" agent-jar]
                               ["org.jacoco:org.jacoco.cli:0.8.12:jar:nodeps" jacoco-jar]]]
    (when-not (fs/exists? (jar-path filename))
      (when-not (zero? (maven [(str dependency-plugin ":copy") (str "-Dartifact=" artifact)
                              (str "-DoutputDirectory=" (cache) "/lib")]))
        (throw (ex-info (str "cannot install " artifact) {}))))))

(defn prepare []
  (maven ["test-compile" (str dependency-plugin ":build-classpath")
          "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/sf-java/classpath.txt"]))

(defn classpath []
  (str/join fs/path-separator
            (remove str/blank? ["target/classes" "target/test-classes"
                                (str/trim (slurp (shell/in-dir "target/sf-java/classpath.txt")))])))

(defn- junit [command agent?]
  (let [args (concat (when agent? [(str "-javaagent:" (jar-path agent-jar) "=destfile=" coverage-exec ",append=false")])
                     ["-jar" (jar-path console-jar) "execute" "--class-path" (classpath)
                      "--scan-class-path=target/test-classes" "--fail-if-no-tests" "--disable-banner" "--details=summary"]
                     (policy/tag-args command))]
    (if (zero? (shell/run "java" (vec args))) 0 1)))

(defn tests [command]
  (validate-project)
  (if (zero? (prepare))
    (do (install-runtime) (junit command false))
    1))

(defn coverage []
  (validate-project)
  (doseq [path [coverage-xml coverage-exec]] (fs/delete-if-exists (shell/in-dir path)))
  (if-not (zero? (prepare))
    1
    (do
      (install-runtime)
      (if-not (zero? (junit "coverage" true))
        1
        (do
          (fs/create-dirs (fs/parent (shell/in-dir coverage-xml)))
          (when-not (zero? (shell/run "java" ["-jar" (jar-path jacoco-jar) "report" coverage-exec
                                              "--classfiles" "target/classes" "--sourcefiles" "src/main/java"
                                              "--xml" coverage-xml]))
            (throw (ex-info "JaCoCo report generation failed" {})))
          (when-not (shell/exists? coverage-xml)
            (throw (ex-info "missing new coverage report after dedicated runner" {})))
          (println (str "coverage written to " coverage-xml))
          0)))))
