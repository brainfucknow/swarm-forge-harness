(ns sfjava.architecture-spec
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [speclj.core :refer :all]))

(defn- dependencies [resource]
  (let [[_ _ & clauses] (read-string (slurp (io/resource resource)))]
    (set (for [clause clauses
               :when (and (sequential? clause) (= :require (first clause)))
               spec (rest clause)]
           (if (sequential? spec) (first spec) spec)))))

(describe "Java policy boundary"
  (it "keeps selection and report decisions independent of process and project adapters"
    (doseq [[resource allowed] {"sfjava/policy.clj" '#{clojure.string}
                              "sfjava/reports.clj" '#{clojure.edn clojure.string sfcore.changes}}]
      (should= #{} (set/difference (dependencies resource) allowed)))))
