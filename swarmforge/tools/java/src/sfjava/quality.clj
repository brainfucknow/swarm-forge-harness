(ns sfjava.quality
  "Changed-source quality commands through the pinned upstream executables."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [sfcore.git :as git]
            [sfcore.shell :as shell]
            [sfjava.policy :as policy]
            [sfjava.reports :as reports]
            [sfjava.runner :as runner]))

(defn tool-bin []
  (str (fs/path (str/trim (git/git ["rev-parse" "--path-format=absolute" "--git-common-dir"]))
                ".." ".swarmforge" "bin")))

(defn tool [name]
  (let [path (str (fs/normalize (fs/path (tool-bin) name)))]
    (when-not (fs/executable? path)
      (throw (ex-info (str "analysis failure: missing tool executable " name "; run sf-java tools") {})))
    path))

(defn changed [opts]
  (git/collect-changed-lines {:base (or (:base opts) (git/default-base nil))
                              :pathspecs ["*.java"]}))

(defn files [scope]
  (policy/production-files (keys scope) shell/exists?))

(defn quote-shell [word]
  (str "'" (str/replace (str word) "'" "'\\''") "'"))

(defn runner-command [command]
  (str (quote-shell (str (fs/canonicalize (fs/path (System/getProperty "sfjava.home") ".." ".." "bin" "sf-java"))))
       " " command))

(defn crap [opts]
  (runner/validate-project)
  (let [selected (files (changed opts))]
    (if (empty? selected)
      (do (println "no changed production Java files to analyze") 0)
      (let [command (tool "crap4java")
            _ (println (str "Analyzing " (str/join ", " selected)))
            result (apply process/shell
                            {:out :string :err :inherit :continue true :dir shell/*dir*
                             :extra-env {"SFJAVA_COVERAGE_COMMAND" (runner-command "coverage")
                                         "SFJAVA_CACHE" (runner/cache)}}
                            command selected)]
        (print (:out result))
        (flush)
        (reports/crap-status (:exit result) (:out result))))))

(defn dry [opts]
  (runner/validate-project)
  (let [scope (changed opts)
        {:keys [exit out]} (shell/capture (tool "dry4java") ["--edn" "src/main/java"])]
    (when-not (zero? exit) (throw (ex-info "analysis failure: dry4java failed" {})))
    (let [findings (reports/relevant-duplicates out scope (git/repo-root))]
      (doseq [candidate findings] (println (pr-str candidate)))
      (println (str (count findings) " relevant duplicates"))
      (if (seq findings) 1 0))))

(defn- mutate-file [command file]
  (let [source (shell/in-dir file)
        original (slurp source)
        pom (slurp (shell/in-dir "pom.xml"))
        manifest (str (fs/absolutize (shell/in-dir (str ".metrics/mutate/java/" file ".manifest"))))]
    (try
      (let [result (process/shell
                    {:out :string :err :inherit :continue true :dir shell/*dir*
                     :extra-env {"SFJAVA_CACHE" (runner/cache) "SFJAVA_MANIFEST" manifest}}
                    command file "--max-workers" "4" "--test-command" (runner-command "test") "--verbose")]
        (print (:out result))
        (flush)
        (reports/mutation-status (:exit result) (:out result)))
      (finally
        (spit source original)
        (spit (shell/in-dir "pom.xml") pom)))))

(defn mutate [opts]
  (runner/validate-project)
  (let [selected (files (changed opts))]
    (if (empty? selected)
      (do (println "no production Java files require mutation") 0)
      (let [command (tool "mutate4java")
            coverage (runner/coverage)]
        (if-not (zero? coverage)
          coverage
          (reduce max 0 (map #(mutate-file command %) selected)))))))
