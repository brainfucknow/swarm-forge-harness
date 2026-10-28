(ns sf.gherkin-cache
  "The gherkin-mutator wrapper keeps the block of its --feature file out of the
  feature between runs, in .metrics/gherkin/<feature path from the checkout
  root>. Usage: bb -cp <sf src> -m sf.gherkin-cache restore|stash <feature>."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.feature-block :as block]
            [sf.shell :as shell]))

(defn- cache-file
  "The cache of an existing feature inside the checkout, or nil."
  [feature]
  (when (fs/regular-file? feature)
    (let [root (fs/canonicalize (shell/checkout-root))
          path (fs/relativize root (fs/canonicalize feature))]
      (when-not (str/starts-with? (str path) "..")
        (fs/path root ".metrics" "gherkin" path)))))

(defn restore!
  "Puts the cached block on top of the feature when it has none."
  [feature]
  (when-let [cache (cache-file feature)]
    (let [text (slurp (str feature))]
      (when (and (fs/exists? cache) (nil? (block/split text)))
        (spit (str feature) (block/with-block (slurp (str cache)) text))))))

(defn stash!
  "Moves the block of the feature to its cache."
  [feature]
  (when-let [cache (cache-file feature)]
    (when-let [{:keys [block body]} (block/split (slurp (str feature)))]
      (fs/create-dirs (fs/parent cache))
      (spit (str cache) block)
      (spit (str feature) body))))

(defn main [[command feature]]
  (case command
    "restore" (restore! feature)
    "stash" (stash! feature)
    (shell/die "usage: restore|stash <feature>")))

(defn -main [& args]
  (System/exit (shell/main-with "sf gherkin-cache: " #(main args))))
