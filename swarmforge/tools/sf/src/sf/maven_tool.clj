(ns sf.maven-tool
  "Build pinned upstream Java sources after validated, tracked compatibility patches."
  (:require [babashka.fs :as fs]
            [sf.shell :as shell]))

(defn- run-command! [dir error & args]
  (let [{:keys [exit err out]} (apply shell/run {:dir dir} args)]
    (when-not (zero? exit) (shell/die error err (when (empty? err) out)))))

(defn build
  ([dir tool patches cache] (build dir tool patches cache true))
  ([dir tool patches cache patch?]
   (let [patch (str (fs/path patches (str tool ".patch")))
         jar (str (fs/path dir "target" (str tool "-0.1.0-SNAPSHOT.jar")))]
     (when patch?
       (when-not (fs/exists? patch) (shell/die "missing compatibility patch for " tool))
       (run-command! dir (str "compatibility patch failure for " tool ": ") "git" "apply" "--check" patch)
       (run-command! dir (str "compatibility patch failure for " tool ": ") "git" "apply" patch))
     (run-command! dir (str "Maven build failed for " tool ": ")
                   "mvn" "-B" "-q" (str "-Dmaven.repo.local=" cache) "-Dmaven.test.skip=true"
                   "org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile" "package")
     (when-not (fs/exists? jar) (shell/die "Maven did not produce " jar))
     jar)))
