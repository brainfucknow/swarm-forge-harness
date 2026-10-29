(ns sfjs.architecture-test
  "Dependency rules for the sf-* wrappers, read from the ns forms under
  swarmforge/tools: policy namespaces stay free of IO, shared core never
  reaches into a language, and only the F# entrypoint delegates to the .NET CLI."
  (:require [babashka.fs :as fs]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def tools-dir (str (nth (iterate fs/parent (fs/canonicalize *file*)) 4)))

(def source-roots
  ["core/src" "javascript/src" "javascript/acceptance/src" "csharp/src"])

(defn- ns-form [file]
  (with-open [reader (java.io.PushbackReader. (java.io.FileReader. (str file)))]
    (read reader)))

(defn- required [form]
  (set (for [clause (drop 2 form)
             :when (and (seq? clause) (= :require (first clause)))
             spec (rest clause)]
         (if (sequential? spec) (first spec) spec))))

(def graph
  "Each namespace under the source roots and the namespaces it requires."
  (into (sorted-map)
        (for [root source-roots
              file (fs/glob (fs/path tools-dir root) "**.clj")
              :let [form (ns-form file)]]
          [(second form) (required form)])))

(def io-libraries '#{babashka.fs babashka.process clojure.java.io clojure.java.shell})

(def adapters
  "The namespaces that may touch processes, files, git, or the console."
  '#{sfcore.shell sfcore.git sfjs.cli sfcs.cli sfcs.cobertura sffs.cli})

(defn- in? [prefix sym]
  (str/starts-with? (str sym) prefix))

(defn- violations [pred]
  (into (sorted-map) (keep (fn [[from deps]] (when-let [bad (seq (filter #(pred from %) deps))] [from (vec (sort bad))])))
        graph))

(deftest the-graph-is-read
  (is (contains? graph 'sfjs.cli))
  (is (contains? (graph 'sfjs.cli) 'sfcore.git)))

(deftest policy-namespaces-stay-free-of-io
  (is (= {} (violations (fn [from dep]
                          (and (not (adapters from))
                               (not (in? "sfjs.acceptance." from))
                               (or (io-libraries dep) (adapters dep))))))))

(deftest core-never-reaches-into-a-language
  (is (= {} (violations (fn [from dep] (and (in? "sfcore." from) (not (in? "sfcore." dep)) (in? "sf" dep)))))))

(deftest languages-stay-apart
  (is (= {} (violations (fn [from dep]
                          (or (and (in? "sfjs." from) (in? "sfcs." dep))
                              (and (in? "sfcs." from) (in? "sfjs." dep))))))))

(deftest command-line-dependencies-are-limited-to-the-dotnet-entrypoint
  (is (= {} (violations (fn [from dep]
                          (or (and (#{'sfjs.cli 'sfcs.cli 'sffs.cli} dep)
                                   (not= ['sffs.cli 'sfcs.cli] [from dep]))
                              (and (not (in? "sfjs.acceptance." from)) (in? "sfjs.acceptance." dep))))))))

(def complexity-sources
  "The linters sf-js crap takes complexity from; each reads its own report
  into the functions sfjs.crap scores."
  '#{sfjs.eslint sfjs.oxlint})

(deftest crap-scoring-knows-no-linter-and-the-linters-stay-apart
  (is (= {} (violations (fn [from dep]
                          (and (complexity-sources dep)
                               (or (= 'sfjs.crap from) (complexity-sources from))))))))

(defn- reachable [start]
  (loop [seen #{} todo (vec (graph start))]
    (if-let [[next & more] (seq todo)]
      (if (seen next)
        (recur seen (vec more))
        (recur (conj seen next) (into (vec more) (graph next))))
      seen)))

(deftest no-import-cycles
  (is (= [] (filter #(contains? (reachable %) %) (keys graph)))))

(deftest adapters-exist
  (is (= #{} (set/difference adapters (set (keys graph))))))
