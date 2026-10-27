(ns sf.manifest-boundary-spec
  "The manifest policy stays away from IO: sf.manifest and sf.feature-block
  answer the questions, and only sf.manifest-store touches git and files."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [speclj.core :refer [describe it should= should-contain]]))

(defn- requires
  "The namespaces that the ns form of a source file requires."
  [resource]
  (let [[_ _ & clauses] (read-string (slurp (io/resource resource)))]
    (->> clauses
         (filter #(and (seq? %) (= :require (first %))))
         (mapcat rest)
         (map #(if (sequential? %) (first %) %))
         set)))

(def ^:private io-near '#{babashka.fs babashka.process clojure.java.io clojure.java.shell
                          sf.shell sf.bug sf.manifest-store sf.task})

(describe "manifest boundaries"
  (it "keep sf.feature-block free of other sf modules and IO"
    (should= #{'clojure.string} (requires "sf/feature_block.clj")))

  (it "keep sf.manifest free of IO"
    (should= #{} (set/intersection io-near (requires "sf/manifest.clj"))))

  (it "let sf.manifest-store depend inward on the policy"
    (should-contain 'sf.manifest (requires "sf/manifest_store.clj"))
    (should-contain 'sf.feature-block (requires "sf/manifest_store.clj"))))
