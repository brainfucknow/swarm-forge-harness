(ns sfcore.command
  "The command line every sf-* wrapper shares: help, unknown commands, option
  parsing, and handing the parsed arguments to the command's function."
  (:require [babashka.cli :as cli]
            [sfcore.outcome :as outcome]))

(defn- show-usage [usage]
  (println usage)
  outcome/exit-ok)

(defn- dispatch [{:keys [usage commands option-spec help-option?]} command args]
  (let [[before after] (split-with #(not= "--" %) args)
        opts (:opts (cli/parse-args (vec before) option-spec))
        run (commands command)]
    (cond
      (and help-option? (:help opts)) (show-usage usage)
      (nil? run) (throw (outcome/usage-error (str "unknown command '" command "'\n" usage)))
      :else (run {:args (vec args) :opts opts :extra (vec (drop 1 after))}))))

(defn main
  "Runs the command named first in `argv`. `commands` maps names to functions
  of {:args :opts :extra}; `help-option?` makes --help after a command print
  the usage instead of reaching the command."
  [{:keys [usage] :as spec} argv]
  (let [[command & args] argv]
    (if (or (nil? command) (#{"help" "--help" "-h"} command))
      (show-usage usage)
      (dispatch spec command args))))
