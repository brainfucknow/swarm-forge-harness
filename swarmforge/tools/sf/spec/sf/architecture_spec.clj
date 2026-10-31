(ns sf.architecture-spec
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [speclj.core :refer [describe it should= should-be]]))

(defn- source-path [ns-name]
  (str (.replace (.replace (name ns-name) "-" "_") "." "/") ".clj"))

(defn- required
  "The namespaces the ns form of the source file for ns-name requires."
  [ns-name]
  (let [[_ _ & clauses] (read-string (slurp (io/resource (source-path ns-name))))]
    (set (for [clause clauses
               :when (and (sequential? clause) (= :require (first clause)))
               spec (rest clause)]
           (if (sequential? spec) (first spec) spec)))))

(defn- sf-required [ns-name]
  (set (filter #(.startsWith (name %) "sf.") (required ns-name))))

(def ^:private io-namespaces
  '#{babashka.fs babashka.process clojure.java.io clojure.java.shell sf.shell})

(def ^:private pure
  "The high-level namespaces: plain values in and out, no IO."
  '#{sf.lore sf.record sf.flow sf.feature-block sf.coverage-args})

(def ^:private allowed
  "The sf namespaces each sf namespace may require. Dependencies point from the
  IO-near namespaces toward the pure ones; nothing requires sf.cli."
  '{sf.shell #{}
    sf.lore #{}
    sf.record #{}
    sf.flow #{sf.record}
    sf.config #{sf.flow sf.shell}
    sf.entry-point #{}
    sf.wrapper #{sf.shell}
    sf.tool #{sf.config sf.entry-point sf.shell sf.wrapper}
    sf.adapters #{sf.config sf.shell}
    sf.git #{sf.shell}
    sf.store #{sf.git sf.record sf.shell}
    sf.metrics #{sf.git sf.shell}
    sf.verify #{sf.shell}
    sf.feature-block #{}
    sf.gherkin-cache #{sf.feature-block sf.shell}
    sf.coverage-args #{}
    sf.coverage-rewrite #{sf.coverage-args sf.shell sf.wrapper}
    sf.hook #{sf.lore sf.record sf.shell}
    sf.options #{sf.record sf.shell}
    sf.audit #{sf.config sf.flow sf.shell sf.store}
    sf.history #{sf.flow sf.lore sf.record sf.shell sf.store}
    sf.dispatch #{sf.config sf.flow sf.shell sf.store sf.task}
    sf.task #{sf.audit sf.config sf.flow sf.git sf.history sf.hook sf.metrics sf.options sf.record sf.shell sf.store sf.verify}
    sf.cli #{sf.adapters sf.config sf.dispatch sf.hook sf.shell sf.task sf.tool}})

(defn- source-namespaces []
  (let [dir (fs/parent (fs/path (io/resource (source-path 'sf.cli))))]
    (set (for [file (fs/glob dir "*.clj")]
           (symbol (str "sf." (.replace (fs/strip-ext (fs/file-name file)) "_" "-")))))))

(defn- reachable
  "The namespaces that ns-name reaches through allowed."
  [ns-name]
  (loop [seen #{} todo (allowed ns-name)]
    (if-let [[next & more] (seq todo)]
      (if (seen next)
        (recur seen more)
        (recur (conj seen next) (concat more (allowed next))))
      seen)))

(describe "sf namespaces"
  (it "are each pinned to the sf namespaces they may require"
    (should= (set (keys allowed)) (source-namespaces)))

  (it "require only what the pin allows"
    (doseq [ns-name (keys allowed)]
      (should= [ns-name #{}] [ns-name (set/difference (sf-required ns-name) (allowed ns-name))])))

  (it "form no cycle"
    (doseq [ns-name (keys allowed)]
      (should= [ns-name false] [ns-name (contains? (reachable ns-name) ns-name)]))))

(describe "the pure namespaces"
  (it "do no IO, so the commands and the hook reach them with plain values"
    (doseq [ns-name pure]
      (should= [ns-name #{}] [ns-name (set (filter io-namespaces (required ns-name)))])))

  (it "require only each other"
    (doseq [ns-name pure]
      (should-be #(set/subset? % pure) (sf-required ns-name)))))

(describe "the IO adapters"
  (it "ask sf.lore for the byline, the Lore-id and the LORE lines"
    (should-be #(contains? % 'sf.lore) (required 'sf.hook))
    (should-be #(contains? % 'sf.lore) (required 'sf.history)))

  (it "ask sf.flow whether a task waits on the operator"
    (should-be #(contains? % 'sf.flow) (required 'sf.dispatch))
    (should-be #(contains? % 'sf.flow) (required 'sf.task))))
