(ns sfcore.shell
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def exit-ok 0)
(def exit-findings 1)
(def exit-usage 2)

(defn usage-error [message]
  (ex-info message {:usage true}))

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

(defn git
  "Runs git with the given arguments and returns its stdout; throws on failure."
  [args & {:keys [dir]}]
  (let [result (apply p/shell {:out :string :err :string :dir (or dir *dir*) :continue true}
                      "git" "-c" "core.quotePath=false" args)]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "git " (str/join " " args) ": " (str/trim (:err result))) {})))
    (:out result)))

(defn repo-root [& {:keys [dir]}]
  (str/trim (git ["rev-parse" "--show-toplevel"] :dir dir)))

(defn ref-exists? [ref dir]
  (zero? (:exit (p/shell {:out :string :err :string :dir (or dir *dir*) :continue true}
                         "git" "rev-parse" "--verify" "--quiet" (str ref "^{commit}")))))

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

(defn run-main
  "Exits with what `main` returns; an exception is a usage error on stderr
  prefixed with the tool name."
  [tool main argv]
  (System/exit
   (try
     (main argv)
     (catch Exception e
       (binding [*out* *err*] (println (str tool ": " (ex-message e))))
       exit-usage))))
