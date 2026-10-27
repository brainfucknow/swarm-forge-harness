(ns sf.tool-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [around describe it should should= should-contain]]
            [sf.repo-support :as support :refer [*dir* with-repo]]
            [sf.shell :as shell]
            [sf.tool :as tool]))

(def ^:private tools
  (str "alpha bb github.com/x/alpha task-a mutate beta\n"
       "beta clj foo/beta foo.main - -\n"
       "cloverage clj cloverage/cloverage cloverage.coverage - -\n"
       "speclj clj speclj/speclj speclj.main - -\n"
       "loc local bin/loc - - -\n"
       "gherk bb github.com/x/gherk g gherkin -\n"
       "odd-kind zzz x - - -\n"
       "odd-rewrite bb github.com/x/odd o zzz -\n"))

(defn- run-tool [& args]
  (support/captured #(let [status (tool/main (vec args))]
                       (when-not (zero? status) (shell/fail status nil)))))

(defn- tool-source
  "A directory that passes for the local checkout of a tool source."
  []
  (let [dir (str (fs/create-temp-dir))]
    (spit (str dir "/bb.edn") "{}")
    dir))

(defn- source-in [dir]
  (fn [name default] (if (= "SWARMFORGE_TOOL_SRC" name) dir default)))

(defn- wrapper [tool]
  (slurp (str (fs/path *dir* ".swarmforge" "bin" tool))))

(describe "sf tool"
  (around [it] (with-repo {"tools.conf" tools} it))

  (it "prints the usage without a command and for help"
    (should-contain "usage: sf tool" (:out (run-tool)))
    (should-contain "usage: sf tool" (:out (run-tool "--help"))))

  (it "names the wrapper directory and lists the catalogue"
    (should= (str *dir* "/.swarmforge/bin") (:out (run-tool "bin")))
    (should-contain "alpha                  bb    github.com/x/alpha" (:out (run-tool "list"))))

  (it "refuses an unknown command, a missing tool name, and an unknown tool"
    (should= {:out "" :err "sf tool: unknown command 'nope'" :status 1} (dissoc (run-tool "nope") :result))
    (should-contain "require needs a tool" (:err (run-tool "require")))
    (should-contain "ensure needs a tool" (:err (run-tool "ensure")))
    (should-contain "unknown tool 'nothing'" (:err (run-tool "ensure" "nothing"))))

  (it "reports the first missing dependency, then OK once it is installed"
    (with-redefs [shell/env (source-in (tool-source))]
      (let [missing (run-tool "require" "alpha")]
        (should= 1 (:status missing))
        (should= "MISSING: beta\nRun: sf tool ensure beta" (:out missing)))
      (should-contain "INSTALLED: alpha" (:out (run-tool "ensure" "alpha")))
      (should-contain (str "OK: alpha " *dir* "/.swarmforge/bin/alpha") (:out (run-tool "require" "alpha")))
      (should (str/includes? (wrapper "alpha") "exec bb --config"))
      (should (str/includes? (wrapper "alpha") "--max-workers"))
      (should (str/includes? (wrapper "beta") "exec clojure -Sdeps"))))

  (it "writes the wrapper of each kind"
    (with-redefs [shell/env (source-in (tool-source))]
      (doseq [name ["cloverage" "speclj" "loc" "gherk"]] (run-tool "ensure" name))
      (should (str/includes? (wrapper "cloverage") "-r speclj"))
      (should (str/includes? (wrapper "speclj") "-c spec"))
      (should (str/includes? (wrapper "loc") (str "exec '" *dir* "/bin/loc'")))
      (should (str/includes? (wrapper "gherk") "--workers"))))

  (it "refuses an unknown kind and an unknown rewrite"
    (should-contain "unknown kind 'zzz' for odd-kind" (:err (run-tool "ensure" "odd-kind")))
    (with-redefs [shell/env (source-in (tool-source))]
      (should-contain "unknown rewrite 'zzz'" (:err (run-tool "ensure" "odd-rewrite")))))

  (it "refuses a tool source override without a bb.edn"
    (with-redefs [shell/env (source-in (str (fs/create-temp-dir)))]
      (should-contain "SWARMFORGE_TOOL_SRC has no bb.edn" (:err (run-tool "ensure" "gherk")))))

  (it "clones a source on first use and stops when the clone fails"
    (let [cloned (atom nil)
          clone (fn [ok?] (fn [_ & cmd]
                            (reset! cloned cmd)
                            (when ok? (fs/create-dirs (last cmd)) (spit (str (last cmd) "/bb.edn") "{}"))
                            {:exit (if ok? 0 1) :out "" :err ""}))]
      (with-redefs [shell/run (clone false)]
        (should-contain "failed to clone https://github.com/x/gherk.git" (:err (run-tool "ensure" "gherk"))))
      (with-redefs [shell/run (clone true)]
        (should-contain "INSTALLED: gherk" (:out (run-tool "ensure" "gherk")))
        (should= ["git" "clone" "--quiet" "--depth" "1" "https://github.com/x/gherk.git"
                  (str *dir* "/.swarmforge/tools/gherk")]
                 @cloned)))))
