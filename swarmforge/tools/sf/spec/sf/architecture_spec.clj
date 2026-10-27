(ns sf.architecture-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer [describe it should= should-be]]))

(defn- required
  "The namespaces the ns form of the source file for ns-name requires."
  [ns-name]
  (let [path (str (.replace (.replace (name ns-name) "-" "_") "." "/") ".clj")
        [_ _ & clauses] (read-string (slurp (io/resource path)))]
    (set (for [[kind & specs] clauses
               :when (= :require kind)
               spec specs]
           (if (sequential? spec) (first spec) spec)))))

(def ^:private io-namespaces
  '#{babashka.fs babashka.process clojure.java.io clojure.java.shell sf.shell})

(describe "sf.lore"
  (it "is a leaf: it requires no other sf namespace"
    (should= #{} (set (filter #(.startsWith (name %) "sf.") (required 'sf.lore)))))

  (it "does no IO, so the hook and sf start reach it with plain values"
    (should= #{} (set (filter io-namespaces (required 'sf.lore)))))

  (it "is the namespace the IO adapters for the hook and the LORE lines call"
    (should-be #(contains? % 'sf.lore) (required 'sf.hook))
    (should-be #(contains? % 'sf.lore) (required 'sf.task))))
