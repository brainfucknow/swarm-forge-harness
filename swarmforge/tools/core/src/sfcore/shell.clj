(ns sfcore.shell
  "Processes, PATH, project files and the console: the IO edge every sf-*
  command runs through."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfcore.outcome :as outcome]))

(def ^:dynamic *dir*
  "The directory commands run in and project paths resolve against; nil is the
  process's own working directory."
  nil)

(defn in-dir
  "`path` resolved against *dir*, or unchanged when there is none."
  [path]
  (if *dir*
    (str (.resolve (fs/path *dir*) (str path)))
    (str path)))

(defn exists? [path]
  (fs/exists? (in-dir path)))

(defn read-json [path]
  (json/parse-string (slurp (in-dir path))))

(defn write-file
  "Writes `content` to `path`, creating its directories."
  [path content]
  (fs/create-dirs (fs/parent (in-dir path)))
  (spit (in-dir path) content))

(defn- launch
  "Prints the command line, then runs it with the process `options`; its
  :extra-env assignments print ahead of the command."
  [options command args]
  (let [env (map (fn [[k v]] (str k "=" v)) (:extra-env options))]
    (println (str "$ " (str/join " " (concat env (cons command args))))))
  (try
    (apply p/shell (merge {:continue true :dir *dir*} options) command args)
    (catch java.io.IOException e
      (throw (ex-info (str command ": " (.getMessage e)) {})))))

(defn run
  "Runs a tool in the foreground, streaming its output; returns its exit status.
  `env` adds environment variables."
  ([command args] (run command args nil))
  ([command args env] (:exit (launch (if env {:extra-env env} {}) command args))))

(defn capture
  "Runs a tool with its stdout kept rather than shown and its stderr streamed;
  returns its exit status and stdout."
  [command args]
  (select-keys (launch {:out :string} command args) [:exit :out]))

(defn warn
  "Prints a warning on stderr, prefixed with the tool name."
  [tool message]
  (binding [*out* *err*] (println (str tool ": warning: " message))))

(defn available? [command]
  (some? (fs/which command)))

(defn emit
  "Prints a report's lines; returns its exit status."
  [{:keys [lines status]}]
  (run! println lines)
  status)

(defn run-main
  "Exits with what `main` returns; an exception is a usage error on stderr
  prefixed with the tool name."
  [tool main argv]
  (System/exit
   (try
     (main argv)
     (catch Exception e
       (binding [*out* *err*] (println (str tool ": " (ex-message e))))
       outcome/exit-usage))))
