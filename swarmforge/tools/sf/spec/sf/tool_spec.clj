(ns sf.tool-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [describe context it should= should should-not around]]
            [sf.shell :as shell]
            [sf.tool :as tool]))

(def ^:dynamic *dir* nil)
(def ^:dynamic *env* {})
(def ^:dynamic *clone* nil)

(def ^:private catalogue
  "gherkin-parser bb     github.com/x/aps     gherkin-parser -        -
gherkin-mutator bb    github.com/x/aps     gherkin-mutator gherkin -
crap4clj       bb     github.com/x/crap4clj crap           coverage cloverage
dry4clj        bbmain github.com/x/dry4clj  dry4clj.core   -        -
cloverage      clj    cloverage/cloverage   cloverage.coverage -    -
speclj         clj    speclj/speclj         speclj.main    -        -
clj-mutate     clj    clj-mutate/clj-mutate clj-mutate.core mutate  -
probe          local  bin/probe             -              -        -
weird          java   x                     -              -        -
odd            local  bin/odd               -              rot13    probe
outer          bb     github.com/x/outer    crap           coverage weird
")

(defn- with-checkout [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "tool-spec-"}))]
    (try
      (fs/create-dirs (fs/path dir "swarmforge"))
      (spit (str (fs/path dir "swarmforge" "tools.conf")) catalogue)
      (with-redefs [shell/repo-root (constantly dir)
                    shell/checkout-root (constantly dir)
                    shell/env (fn [name default] (get *env* name default))
                    shell/run (fn [_ & cmd] (*clone* cmd))]
        (binding [*dir* dir
                  *clone* (fn [_] {:exit 1 :out "" :err ""})]
          (run-spec)))
      (finally (fs/delete-tree dir)))))

(defn- sf-tool
  "Runs sf tool with args; returns [status stdout stderr]."
  [& args]
  (let [err (java.io.StringWriter.)
        status (atom nil)
        out (with-out-str (binding [*err* err] (reset! status (tool/main (vec args)))))]
    [@status out (str err)]))

(defn- source [name & {:keys [tasks]}]
  (let [dir (fs/create-dirs (fs/path *dir* ".swarmforge" "tools" name))]
    (when tasks (spit (str (fs/path dir "bb.edn")) (str "{:tasks {" (str/join " " (map #(str % " {}") tasks)) "}}")))
    (str dir)))

(defn- wrapper [name]
  (str (fs/path *dir* ".swarmforge" "bin" name)))

(describe "sf tool"
  (around [it] (with-checkout it))

  (it "prints usage, the bin directory and the catalogue"
    (should (str/includes? (second (sf-tool "help")) "usage: sf tool"))
    (should= [0 (str (fs/path *dir* ".swarmforge" "bin") "\n") ""] (sf-tool "bin"))
    (let [[status out] (sf-tool "list")]
      (should= 0 status)
      (should (str/includes? out "dry4clj                bbmain github.com/x/dry4clj"))))

  (it "rejects an unknown command, a missing tool name and an unknown tool"
    (should= [1 "" "sf tool: unknown command 'frob'\n"] (sf-tool "frob"))
    (should= [1 "" "sf tool: ensure needs a tool\n"] (sf-tool "ensure"))
    (should= [1 "" "sf tool: require needs a tool\n"] (sf-tool "require"))
    (should= [1 "" "sf tool: unknown tool 'nope' (see swarmforge/tools.conf)\n"] (sf-tool "ensure" "nope")))

  (context "ensure"
    (it "wraps a bb task of an existing source without cloning it"
      (let [dir (source "aps" :tasks ["gherkin-parser"])]
        (should= [0 (str "INSTALLED: gherkin-parser " (wrapper "gherkin-parser") "\n") ""] (sf-tool "ensure" "gherkin-parser"))
        (should (str/includes? (slurp (wrapper "gherkin-parser")) (str "exec bb --config '" dir "/bb.edn' gherkin-parser")))))

    (it "keeps the gherkin-mutator block in .metrics/gherkin/ with the main checkout's sf"
      (let [dir (source "aps" :tasks ["gherkin-mutator"])
            text (do (sf-tool "ensure" "gherkin-mutator") (slurp (wrapper "gherkin-mutator")))]
        (should (str/includes? text "--level hard"))
        (should (str/includes? text (str "bb -cp '" *dir* "/swarmforge/tools/sf/src' -m sf.gherkin-cache")))
        (should (str/includes? text (str "\nbb --config '" dir "/bb.edn' gherkin-mutator \"$@\"\n")))))

    (it "uses SWARMFORGE_TOOL_SRC for every GitHub source"
      (let [dir (str (fs/create-dirs (fs/path *dir* "override")))]
        (spit (str (fs/path dir "bb.edn")) "{:tasks {gherkin-parser {}}}")
        (binding [*env* {"SWARMFORGE_TOOL_SRC" dir}]
          (should= 0 (first (sf-tool "ensure" "gherkin-parser"))))
        (should (str/includes? (slurp (wrapper "gherkin-parser")) (str "'" dir "/bb.edn'")))))

    (it "clones a missing source, and stops when the clone fails"
      (binding [*clone* (fn [cmd] (fs/create-dirs (last cmd)) (spit (str (fs/path (last cmd) "bb.edn")) "{:tasks {gherkin-parser {}}}") {:exit 0})]
        (should= 0 (first (sf-tool "ensure" "gherkin-parser"))))
      (fs/delete-tree (fs/path *dir* ".swarmforge" "tools" "aps"))
      (should= [1 "" "sf tool: failed to clone https://github.com/x/aps.git\n"] (sf-tool "ensure" "gherkin-parser")))

    (it "installs a tool's needs first and writes the crap4clj coverage config"
      (source "crap4clj" :tasks ["crap"])
      (let [[status out] (sf-tool "ensure" "crap4clj")]
        (should= 0 status)
        (should= [(str "INSTALLED: cloverage " (wrapper "cloverage")) (str "INSTALLED: crap4clj " (wrapper "crap4clj"))]
                 (str/split-lines out)))
      (should (fs/exists? (fs/path *dir* ".swarmforge" "tools" "crap4clj-coverage" "bb.edn")))
      (should (str/includes? (slurp (wrapper "crap4clj"))
                             (str "-cp '" *dir* "/swarmforge/tools/sf/src' -m sf.coverage-rewrite '"
                                  *dir* "/.swarmforge/tools/crap4clj-coverage/bb.edn' \"$@\"")))
      (should (str/includes? (slurp (wrapper "cloverage")) "exec clojure -Sdeps")))

    (it "wraps a bbmain namespace, a clj main and a local script"
      (let [dir (source "dry4clj")]
        (fs/create-dirs (fs/path dir "src" "dry4clj"))
        (spit (str (fs/path dir "src" "dry4clj" "core.clj")) "(ns dry4clj.core)")
        (should= 0 (first (sf-tool "ensure" "dry4clj")))
        (should (str/includes? (slurp (wrapper "dry4clj")) "-m dry4clj.core")))
      (should= 0 (first (sf-tool "ensure" "speclj")))
      (should= 0 (first (sf-tool "ensure" "clj-mutate")))
      (should (str/includes? (slurp (wrapper "clj-mutate")) "--mutate-all"))
      (should= 0 (first (sf-tool "ensure" "probe")))
      (should (str/includes? (slurp (wrapper "probe")) (str "'" *dir* "/bin/probe'"))))

    (it "keeps an earlier wrapper and stops when the entry point is missing"
      (source "aps" :tasks ["other"])
      (fs/create-dirs (fs/path *dir* ".swarmforge" "bin"))
      (spit (wrapper "gherkin-parser") "stale")
      (let [[status _ err] (sf-tool "ensure" "gherkin-parser")]
        (should= 1 status)
        (should (str/includes? err "gherkin-parser: ")))
      (should= "stale" (slurp (wrapper "gherkin-parser"))))

    (it "stops on an unknown kind or rewrite in the needs chain before it writes or clones anything"
      (let [cloned (atom [])]
        (binding [*clone* (fn [cmd] (swap! cloned conj cmd) {:exit 1})]
          (should= [1 "" "sf tool: unknown kind 'java' for weird\n"] (sf-tool "ensure" "weird"))
          (should= [1 "" "sf tool: unknown rewrite 'rot13' in swarmforge/tools.conf\n"] (sf-tool "ensure" "odd"))
          (should= [1 "" "sf tool: unknown kind 'java' for weird\n"] (sf-tool "ensure" "outer")))
        (should= [] @cloned))
      (should-not (fs/exists? (fs/path *dir* ".swarmforge")))))

  (context "require"
    (it "names a missing need, then reports a ready tool"
      (should= [1 (str "MISSING: cloverage\nRun: sf tool ensure cloverage\n") ""] (sf-tool "require" "crap4clj"))
      (sf-tool "ensure" "probe")
      (should= [0 (str "OK: probe " (wrapper "probe") "\n") ""] (sf-tool "require" "probe")))))
