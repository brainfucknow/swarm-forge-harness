(ns sfrb.mutant
  "The mutant command line sf-rb runs, and whether a project has chosen the
  licence usage mutant needs to run at all."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [sfrb.commands :as commands]))

(def config-files
  "The files mutant reads its configuration from."
  [".mutant.yml" "config/mutant.yml" "mutant.yml"])

(def jobs "4")

(def no-usage
  (str "mutation is optional and needs a mutant usage; set `usage: opensource` (public open-source "
       "repositories only) or `usage: commercial` (paid) in " (str/join ", " config-files)
       ", or record Not-tested: mutation: no mutant usage"))

(defn declares-usage?
  "Whether a mutant configuration's YAML text sets the top-level usage key."
  [text]
  (let [config (yaml/parse-string text)]
    (boolean (and (map? config) (contains? config :usage)))))

(defn sets-usage?
  "Whether the caller's mutant arguments choose a usage, which sf-rb leaves
  to the project's configuration."
  [extra]
  (boolean (some #(or (= "--usage" %) (str/starts-with? % "--usage=")) extra)))

(defn- without-jobs [extra]
  (loop [[arg & more] extra kept []]
    (cond
      (nil? arg) kept
      (#{"--jobs" "-j"} arg) (recur (rest more) kept)
      (re-matches #"--jobs=.*|-j\d+" arg) (recur more kept)
      :else (recur more (conj kept arg)))))

(def ^:private rspec-arguments
  "rspec's arguments under mutant: its default --fail-fast and spec, without
  the property specs."
  ["--fail-fast" "--exclude-pattern" commands/property-pattern "spec"])

(defn args
  "`bundle` arguments running mutant on subjects touched since `since` (every
  configured subject when nil), with four jobs whatever `extra` asks for."
  [since extra]
  (-> ["exec" "mutant" "run" "--jobs" jobs]
      (cond-> since (into ["--since" since]))
      (into (mapcat #(vector "--integration-argument" %) rspec-arguments))
      (into (without-jobs extra))))
