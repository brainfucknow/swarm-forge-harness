(ns sf.gherkin-cache-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should= should-not around]]
            [sf.gherkin-cache :as cache]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)

(def block "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n")

(defn- file [& parts] (str (apply fs/path *dir* parts)))

(defn- write [path text]
  (fs/create-dirs (fs/parent path))
  (spit path text))

(describe "the gherkin-mutator block between runs"
  (around [it]
    (let [dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "gherkin-cache-"})))]
      (try
        (with-redefs [shell/checkout-root (constantly dir)]
          (binding [*dir* dir] (it)))
        (finally (fs/delete-tree dir)))))

  (it "moves the block of a feature to .metrics/gherkin/<path from the checkout root>"
    (write (file "features" "x.feature") (str block "\nFeature: x\n"))
    (write (file ".metrics" "gherkin" "features" "x.feature") "old\n")
    (cache/stash! (file "features" "x.feature"))
    (should= "Feature: x\n" (slurp (file "features" "x.feature")))
    (should= block (slurp (file ".metrics" "gherkin" "features" "x.feature"))))

  (it "leaves the cache alone when the feature has no block"
    (write (file "x.feature") "Feature: x\n")
    (cache/stash! (file "x.feature"))
    (should-not (fs/exists? (file ".metrics"))))

  (it "puts the cached block on top of a feature without one"
    (write (file "x.feature") "Feature: x\n")
    (write (file ".metrics" "gherkin" "x.feature") block)
    (cache/restore! (file "x.feature"))
    (should= (str block "\nFeature: x\n") (slurp (file "x.feature"))))

  (it "keeps the block a feature already has"
    (write (file "x.feature") (str block "\nFeature: x\n"))
    (write (file ".metrics" "gherkin" "x.feature") "# acceptance-mutation-manifest-begin\n# acceptance-mutation-manifest-end\n")
    (cache/restore! (file "x.feature"))
    (should= (str block "\nFeature: x\n") (slurp (file "x.feature"))))

  (it "does nothing for a missing feature or one outside the checkout"
    (cache/restore! (file "missing.feature"))
    (cache/stash! (file "missing.feature"))
    (let [outside (str (fs/create-temp-file {:suffix ".feature"}))]
      (try
        (spit outside (str block "\nFeature: x\n"))
        (cache/stash! outside)
        (should= (str block "\nFeature: x\n") (slurp outside))
        (finally (fs/delete outside))))
    (should-not (fs/exists? (file ".metrics")))))
