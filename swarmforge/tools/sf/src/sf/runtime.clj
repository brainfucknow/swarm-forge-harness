(ns sf.runtime
  "The Babashka versions sf runs on. bin/sf loads this namespace before any
  other, so an older Babashka gets a clear refusal instead of a load error.")

(def minimum
  "The oldest Babashka that loads sf: 1.12.205 added java.text.Normalizer."
  "1.12.205")

(defn- parts
  "The major, minor and patch numbers of version, 0 when missing."
  [version]
  (vec (take 3 (concat (map parse-long (re-seq #"\d+" (str version))) (repeat 0)))))

(defn too-old?
  "True when version, a Babashka version such as 1.12.200, is older than minimum."
  [version]
  (neg? (compare (parts version) (parts minimum))))

(defn refusal
  "The message that refuses version, or nil when sf runs on it."
  [version]
  (when (too-old? version)
    (str "sf: Babashka " version " is too old; sf needs " minimum " or newer: https://babashka.org")))
