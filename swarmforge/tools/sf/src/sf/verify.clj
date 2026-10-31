(ns sf.verify
  "The project's verification: the commands in swarmforge/verify.conf of a
  checkout, each run with bash -c from the root of that checkout."
  (:require [babashka.fs :as fs]
            [sf.shell :as shell]))

(defn commands
  "The commands in the text of verify.conf: its lines as every
  swarmforge/*.conf file has them, one command per line."
  [text]
  (vec (shell/conf-lines text)))

(defn- run-command
  "The exit status of command, run from dir with its output on stderr."
  [dir command]
  (let [status (:exit (shell/run {:dir dir :out *err* :err *err*} "bash" "-c" command))]
    (binding [*out* *err*] (flush))
    status))

(defn check!
  "Runs the commands of the checkout at dir in order and prints a VERIFY line
  for each one that passes, stopping at the first that fails. Returns that
  failure as {:command :exit}, or nil when verification passed."
  [dir]
  (let [file (fs/path dir "swarmforge" "verify.conf")
        commands (commands (when (fs/exists? file) (slurp (str file))))]
    (when (empty? commands) (println "VERIFY: none configured"))
    (some (fn [command]
            (let [status (run-command dir command)]
              (if (zero? status)
                (println (str "VERIFY: passed: " command))
                {:command command :exit status})))
          commands)))
