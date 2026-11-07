(ns sfjava.policy
  "CLI and selection rules independent of processes and files."
  (:require [clojure.string :as str]))

(def commands #{"tools" "test" "property" "coverage" "crap" "dry" "mutate"})
(def quality-commands #{"crap" "dry" "mutate"})

(defn options [[command & args]]
  (cond
    (#{nil "help" "--help" "-h"} command) {:command "help"}
    (not (commands command)) (throw (ex-info (str "unknown command: " command) {}))
    (empty? args) {:command command}
    (and (quality-commands command) (= 2 (count args)) (= "--base" (first args))
         (not (str/starts-with? (second args) "-"))) {:command command :base (second args)}
    :else (throw (ex-info (str "invalid options for " command "; see sf-java help") {}))))

(defn supported-java? [version]
  (when-let [[_ major] (re-find #"version\s+\"(\d+)" version)]
    (>= (parse-long major) 17)))

(defn tag-args [command]
  [(if (= "property" command) "--include-tag" "--exclude-tag") "property"])

(defn production-files [paths exists?]
  (->> paths
       (filter #(and (str/starts-with? % "src/main/java/") (str/ends-with? % ".java")))
       (filter exists?) sort vec))
