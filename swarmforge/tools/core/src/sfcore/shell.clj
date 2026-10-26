(ns sfcore.shell
  "Processes, PATH and the console: the IO edge every sf-* command runs through."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
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

(defn run
  "Runs a tool in the foreground, streaming its output; returns its exit status."
  [command args]
  (println (str "$ " (str/join " " (cons command args))))
  (try
    (:exit (apply p/shell {:continue true :dir *dir*} command args))
    (catch java.io.IOException e
      (throw (ex-info (str command ": " (.getMessage e)) {})))))

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
