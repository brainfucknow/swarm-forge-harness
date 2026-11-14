(ns sf.repo-support
  "A throwaway repository with a small swarmforge/ catalogue, for specs that
  run sf commands against real git."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)

(def default-conf
  {"roles.conf" "specifier frontier subagent\ncoder strong subagent audit\ncleaner fast subagent\n"
   "tiers.conf" "frontier inherit high gpt kilo/frontier-model vibe/frontier-model\nstrong opus medium gpt-5 kilo/strong-model vibe/strong-model\nfast sonnet low gpt-mini kilo/fast-model vibe/fast-model\n"
   "routes.conf" "feature specifier,coder,cleaner\nquick coder\n"
   "tools.conf" ""})

(defn git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" "-c" "user.name=t" "-c" "user.email=t@x" args)))

(defn write-conf!
  "Writes swarmforge/<name> in dir."
  [dir name text]
  (fs/create-dirs (fs/path dir "swarmforge"))
  (spit (str (fs/path dir "swarmforge" name)) text))

(defn- write-config! [dir conf]
  (doseq [[name text] conf] (write-conf! dir name text))
  (fs/create-dirs (fs/path dir "swarmforge" "roles"))
  (doseq [role ["specifier" "coder" "cleaner"]]
    (spit (str (fs/path dir "swarmforge" "roles" (str role ".md"))) (str "# " role "\n\nDoes " role " work.\n"))))

(def ^:private harness-variables
  "The variables that decide the backend on a record; a spec run inside a
  harness or a dispatch must not see them."
  #{"CLAUDECODE" "SWARMFORGE_BACKEND" "SWARMFORGE_DISPATCHED"})

(defn- without-harness [env]
  (fn [name default]
    (if (harness-variables name) default (env name default))))

(defn with-repo
  "Runs run-spec in a new repository whose catalogue is default-conf merged
  with conf, with sf's repo-root pointing at it and the harness variables
  hidden."
  ([run-spec] (with-repo {} run-spec))
  ([conf run-spec]
   (let [dir (str (fs/create-temp-dir {:prefix "sf-spec-"}))]
     (try
       (git dir "init" "-q" "-b" "main")
       (write-config! dir (merge default-conf conf))
       (git dir "add" ".")
       (git dir "commit" "-q" "-m" "Base")
       (with-redefs [shell/repo-root (constantly dir)
                     shell/checkout-root (constantly dir)
                     shell/env (without-harness shell/env)]
         (binding [*dir* dir] (run-spec)))
       (finally (fs/delete-tree dir))))))

(defn captured
  "The stdout and the stderr that f writes, trimmed, and the exit status of
  its failure, or 0: {:out :err :status :result}."
  [f]
  (let [err (java.io.StringWriter.)
        out (java.io.StringWriter.)
        outcome (binding [*out* out *err* err]
                  (try {:status 0 :result (f)}
                       (catch clojure.lang.ExceptionInfo e
                         (when-not (str/blank? (ex-message e)) (binding [*out* *err*] (println (ex-message e))))
                         {:status (or (:sf/exit (ex-data e)) 1)})))]
    (assoc outcome :out (str/trim (str out)) :err (str/trim (str err)))))
