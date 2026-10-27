(ns sf.repo-support
  "A throwaway repository with a small swarmforge/ catalogue, for specs that
  run sf commands against real git."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)

(def default-conf
  {"roles.conf" "specifier frontier subagent\ncoder strong subagent audit\ncleaner fast subagent\n"
   "tiers.conf" "frontier inherit high gpt\nstrong opus medium gpt-5\nfast sonnet low gpt-mini\n"
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

(defn with-repo
  "Runs run-spec in a new repository whose catalogue is default-conf merged
  with conf, with sf's repo-root pointing at it."
  ([run-spec] (with-repo {} run-spec))
  ([conf run-spec]
   (let [dir (str (fs/create-temp-dir {:prefix "sf-spec-"}))]
     (try
       (git dir "init" "-q" "-b" "main")
       (write-config! dir (merge default-conf conf))
       (git dir "add" ".")
       (git dir "commit" "-q" "-m" "Base")
       (with-redefs [shell/repo-root (constantly dir)]
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
