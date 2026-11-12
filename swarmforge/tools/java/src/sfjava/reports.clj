(ns sfjava.reports
  "Validate upstream reports and select findings without recomputing metrics."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [sfcore.changes :as changes]))

(defn- failed [message]
  (throw (ex-info (str "analysis failure: " message) {})))

(defn crap-status [exit out]
  (when-not (and (#{0 2} exit) (str/includes? out "CRAP Report")
                 (not (str/includes? out "N/A")))
    (failed "crap4java failed or reported missing coverage data"))
  (if (= 2 exit) 1 0))

(defn- location? [{:keys [file start-line end-line]}]
  (and (string? file) (pos-int? start-line) (pos-int? end-line) (<= start-line end-line)))

(defn- valid-duplicate? [{:keys [score left right]}]
  (and (number? score) (location? left) (location? right)))

(defn- touches? [location changed root]
  (let [{:keys [file start-line end-line]} location
        path (changes/relative-path file root)]
    (changes/overlaps? path start-line end-line changed)))

(defn relevant-duplicates [text changed root]
  (let [report (try (edn/read-string text) (catch Exception _ nil))
        candidates (:candidates report)]
    (when-not (and (vector? candidates) (every? valid-duplicate? candidates))
      (failed "malformed dry4java report"))
    (filterv #(or (touches? (:left %) changed root) (touches? (:right %) changed root)) candidates)))

(defn mutation-status [exit out]
  (when-not (and (#{0 3} exit)
                 (re-find #"Summary: \d+ killed, \d+ survived, \d+ total\." out))
    (failed (str "mutate4java exited " exit " without a valid result")))
  (if (= 3 exit) 1 0))
