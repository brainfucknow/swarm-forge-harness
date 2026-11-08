(ns sf.maven-policy-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.maven-tool :as maven]
            [sf.clone :as clone]
            [sf.tool :as tool]
            [sf.shell :as shell]))

(describe "Maven tool installation policy"
  (it "patches pinned sources and leaves local development overrides unpatched"
    (let [dir (str (fs/create-temp-dir {:dir (fs/create-dirs "tmp") :prefix "maven-policy"}))
          calls (atom [])]
      (try
        (fs/create-dirs (fs/path dir "swarmforge"))
        (spit (str (fs/path dir "swarmforge/tools.conf"))
              "crap4java maven github.com/x/crap4java 0123456789abcdef0123456789abcdef01234567 - - -\n")
        (with-redefs [shell/repo-root (constantly dir)
                      shell/checkout-root (constantly dir)
                      shell/env (fn [_ fallback] fallback)
                      clone/head (constantly nil)
                      clone/ensure-at (constantly nil)
                      maven/build (fn [& args] (swap! calls conj args) "tool.jar")]
          (binding [*out* (java.io.StringWriter.)]
            (should= 0 (tool/main ["ensure" "crap4java"]))
            (with-redefs [shell/env (fn [_ _] "local-source")]
              (should= 0 (tool/main ["ensure" "crap4java"]))))
          (should= [true false] (map last @calls)))
        (finally (fs/delete-tree dir))))))
