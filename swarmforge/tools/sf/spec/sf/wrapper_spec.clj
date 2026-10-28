(ns sf.wrapper-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [speclj.core :refer [describe context it should should= with around]]
            [sf.shell :as shell]
            [sf.wrapper :as wrapper]))

(describe "commands"
  (it "runs a bb task of the source's bb.edn"
    (should= "exec bb --config '/t/crap4clj/bb.edn' crap \"$@\""
             (wrapper/bb-command "/t/crap4clj" "crap")))

  (it "runs a main namespace from the source's src"
    (should= "exec bb -cp '/t/dry4clj/src' -m dry4clj.core \"$@\""
             (wrapper/bbmain-command "/t/dry4clj" "dry4clj.core")))

  (it "runs a local script from the caller's checkout of the same repository when it has one, else the main checkout's"
    (should= (str "top=\"$(git rev-parse --show-toplevel 2>/dev/null)\""
                  " && [ \"$(git rev-parse --path-format=absolute --git-common-dir)\" = '/main/.git' ]"
                  " && [ -x \"$top/\"'bin/x' ] && exec \"$top/\"'bin/x' \"$@\"\n"
                  "exec '/main/bin/x' \"$@\"")
             (wrapper/local-command "/main" "bin/x")))

  (it "quotes single quotes in paths"
    (should= "exec bb --config '/it'\\''s/bb.edn' t \"$@\"" (wrapper/bb-command "/it's" "t"))))

(describe "coverage-config"
  (with config (edamame/parse-string (wrapper/coverage-config "/t/crap4clj") {:all true}))

  (it "pins Cloverage at the Babashka-compatible commit"
    (should= {:git/url "https://github.com/cloverage/cloverage.git"
              :git/sha "61e3cac426e9907a9dd01c37597f85c71a57ff90"
              :deps/root "cloverage"}
             (get-in @config [:deps 'cloverage/cloverage])))

  (it "pins speclj at 3.12.2"
    (should= {:mvn/version "3.12.2"} (get-in @config [:deps 'speclj/speclj])))

  (it "has a coverage task that runs crap4clj.bb-coverage from the clone's src"
    (let [task (pr-str (get-in @config [:tasks 'coverage]))]
      (should (str/includes? task "crap4clj.bb-coverage"))
      (should (str/includes? task "\"/t/crap4clj/src\""))
      (should (str/includes? task "speclj.cloverage"))))

  (it "puts the project's bb.edn :paths on the classpath, read with edamame"
    (let [task (pr-str (get-in @config [:tasks 'coverage]))]
      (should (str/includes? task "(:paths (edamame/parse-string (slurp \"bb.edn\")"))
      (should (str/includes? task "edamame.core")))))

(def ^:private sf-src
  (str (fs/parent (fs/parent (fs/path (.getPath (io/resource "sf/shell.clj")))))))

(defn- run-wrapper-in
  "Runs a crap4clj-style wrapper from dir with a fake bb (and clj when clj?) on
  PATH; returns the exit, the error output, the fake bb's arguments, and the
  directory it ran in (nil when it did not run)."
  [dir clj? args]
  (let [bin (fs/create-dirs (fs/path dir "fakebin"))
        script (str (fs/path dir "wrapper"))
        ran (fs/path dir "ran")]
    (fs/delete-if-exists ran)
    (spit (str (fs/path bin "bb")) (str "#!/usr/bin/env bash\npwd -P > " (wrapper/sq ran) "\nprintf '%s\\n' \"$@\"\n"))
    (when clj? (spit (str (fs/path bin "clj")) "#!/usr/bin/env bash\n"))
    (run! #(fs/set-posix-file-permissions % "rwxr-xr-x") (fs/list-dir bin))
    (spit script (wrapper/script (wrapper/coverage-rewrite (str (fs/which "bb")) sf-src "/cfg/bb.edn") (wrapper/bb-command "/src" "crap")))
    (let [{:keys [exit out err]} (p/shell {:cmd (into ["bash" script] args) :dir (str dir) :out :string :err :string
                                            :continue true :extra-env {"PATH" (str bin ":/usr/bin:/bin")}})]
      {:exit exit :err err :args (str/split-lines out)
       :ran (when (fs/exists? ran) (str/trim (slurp (str ran))))})))

(defn- run-wrapper
  "The fake bb's arguments from run-wrapper-in."
  [dir clj? args]
  (:args (run-wrapper-in dir clj? args)))

(describe "coverage-rewrite"
  (with dir (str (fs/create-temp-dir {:prefix "wrapper-"})))
  (around [it] (try (it) (finally (fs/delete-tree @dir))))

  (context "without the Clojure CLI"
    (it "adds a Babashka coverage command over src and the test directories"
      (fs/create-dirs (fs/path @dir "test"))
      (should= ["--config" "/src/bb.edn" "crap" "--coverage-command" "bb --config /cfg/bb.edn coverage -p src -s test --lcov"]
               (run-wrapper @dir false [])))

    (it "runs speclj when spec exists, and covers the source roots the caller names"
      (fs/create-dirs (fs/path @dir "spec"))
      (fs/create-dirs (fs/path @dir "test"))
      (should= ["--config" "/src/bb.edn" "crap" "--source-root" "lib" "-s" "scripts" "--lcov" "-s"
                "--coverage-command" "bb --config /cfg/bb.edn coverage -r speclj -p lib -p scripts -s spec -s test --lcov"]
               (run-wrapper @dir false ["--source-root" "lib" "-s" "scripts" "--lcov" "-s"])))

    (it "adds nothing when the caller settled coverage"
      (should= ["--config" "/src/bb.edn" "crap" "--use-existing-coverage"] (run-wrapper @dir false ["--use-existing-coverage"]))
      (should= ["--config" "/src/bb.edn" "crap" "--coverage-command" "x"] (run-wrapper @dir false ["--coverage-command" "x"]))))

  (context "with the Clojure CLI"
    (it "still adds the command when there is no deps.edn"
      (should= "bb --config /cfg/bb.edn coverage -p src --lcov" (last (run-wrapper @dir true []))))

    (it "adds nothing when there is a deps.edn"
      (spit (str (fs/path @dir "deps.edn")) "{}")
      (should= ["--config" "/src/bb.edn" "crap"] (run-wrapper @dir true [])))))

(describe "coverage-rewrite directory"
  (with dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "wrapper-"}))))
  (around [it] (try (it) (finally (fs/delete-tree @dir))))

  (it "runs in the directory of the first source root, with the source roots relative to it"
    (fs/create-dirs (fs/path @dir "tools" "x" "spec"))
    (fs/create-dirs (fs/path @dir "tools" "core" "src"))
    (fs/create-dirs (fs/path @dir "test"))
    (should= {:exit 0 :err "" :ran (str (fs/path @dir "tools" "x"))
              :args ["--config" "/src/bb.edn" "crap" "--source-root" "src" "-s" "../core/src" "orders" "--lcov" "x/y"
                     "--coverage-command" "bb --config /cfg/bb.edn coverage -r speclj -p src -p ../core/src -s spec --lcov"]}
             (run-wrapper-in @dir false ["--source-root" "tools/x/src" "-s" "tools/core/src" "orders" "--lcov" "x/y"])))

  (it "makes an absolute first source root relative to its directory"
    (fs/create-dirs (fs/path @dir "tools" "x"))
    (let [{:keys [ran args]} (run-wrapper-in @dir false ["-s" (str @dir "/tools/./x/../x/src/")])]
      (should= (str (fs/path @dir "tools" "x")) ran)
      (should= ["--config" "/src/bb.edn" "crap" "-s" "src"] (take 5 args))))

  (it "follows a symlinked directory to its target before making the roots relative"
    (fs/create-dirs (fs/path @dir "projects" "app"))
    (fs/create-dirs (fs/path @dir "projects" "core" "src"))
    (fs/create-sym-link (fs/path @dir "linked-project") (fs/path @dir "projects" "app"))
    (let [{:keys [ran args]} (run-wrapper-in @dir false ["-s" "linked-project/src" "-s" "projects/core/src"])]
      (should= (str (fs/path @dir "projects" "app")) ran)
      (should= ["-s" "src" "-s" "../core/src"] (take 4 (drop 3 args)))))

  (it "looks for deps.edn in that directory"
    (fs/create-dirs (fs/path @dir "tools" "x"))
    (spit (str (fs/path @dir "tools" "x" "deps.edn")) "{}")
    (should= ["--config" "/src/bb.edn" "crap" "--source-root" "src"]
             (run-wrapper @dir true ["--source-root" "tools/x/src"])))

  (it "stays in the current directory without a source root or when the first one is there"
    (should= @dir (:ran (run-wrapper-in @dir true [])))
    (let [{:keys [ran args]} (run-wrapper-in @dir true ["--source-root" "lib" "-s" "tools/x/src"])]
      (should= @dir ran)
      (should= ["--config" "/src/bb.edn" "crap" "--source-root" "lib" "-s" "tools/x/src"] (take 7 args))))

  (it "exits 1 without running when that directory is missing"
    (let [{:keys [exit err ran]} (run-wrapper-in @dir false ["--source-root" "missing/src"])]
      (should= 1 exit)
      (should= nil ran)
      (should (str/includes? err "missing")))))

(def ^:private block "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n")

(defn- run-gherkin-wrapper
  "Runs a gherkin-mutator wrapper whose tool is a fake that saves the feature it
  sees to seen, puts a new block on it, runs last (shell text, such as a kill
  of the wrapper), and exits with status; returns the exit."
  [dir status args & [last]]
  (let [fake (str (fs/path dir "fake"))
        script (str (fs/path dir "wrapper"))]
    (spit fake (str "#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > " (fs/path dir "args") "\n"
                    "cp features/x.feature " (fs/path dir "seen") "\n"
                    "{ printf '" (str/replace block "\n" "\\n") "\\n'; grep -v '^#' features/x.feature | sed '/./,$!d'; } > " (fs/path dir "new") "\n"
                    "mv " (fs/path dir "new") " features/x.feature\n" last "\nexit " status "\n"))
    (fs/set-posix-file-permissions fake "rwxr-xr-x")
    (spit script (wrapper/script (shell/template "rewrite-gherkin.sh")
                                 (wrapper/keeping-feature-block sf-src (str "exec " (wrapper/sq fake) " \"$@\""))))
    (:exit (p/shell {:cmd (into ["bash" script] args) :dir (str dir) :continue true :out :string :err :string}))))

(describe "keeping-feature-block"
  (with dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "wrapper-"}))))
  (around [it] (try (it) (finally (fs/delete-tree @dir))))

  (it "keeps the block in .metrics/gherkin/ between runs and exits with the run's status"
    (p/shell {:dir @dir :out :string} "git init -q")
    (fs/create-dirs (fs/path @dir "features"))
    (spit (str (fs/path @dir "features" "x.feature")) "Feature: x\n")
    (fs/create-dirs (fs/path @dir ".metrics" "gherkin" "features"))
    (spit (str (fs/path @dir ".metrics" "gherkin" "features" "x.feature")) "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end\n")
    (should= 3 (run-gherkin-wrapper @dir 3 ["--feature" "features/x.feature" "--level" "full"]))
    (should= "# acceptance-mutation-manifest-begin\n# {old}\n# acceptance-mutation-manifest-end\n\nFeature: x\n"
             (slurp (str (fs/path @dir "seen"))))
    (should= ["--feature" "features/x.feature" "--level" "hard" "--workers" "4"]
             (str/split-lines (slurp (str (fs/path @dir "args")))))
    (should= "Feature: x\n" (slurp (str (fs/path @dir "features" "x.feature"))))
    (should= block (slurp (str (fs/path @dir ".metrics" "gherkin" "features" "x.feature")))))

  (it "moves the block out when the wrapper is interrupted during the run"
    (p/shell {:dir @dir :out :string} "git init -q")
    (fs/create-dirs (fs/path @dir "features"))
    (spit (str (fs/path @dir "features" "x.feature")) "Feature: x\n")
    (doseq [[signal status] [["INT" 130] ["TERM" 143]]]
      (should= status (run-gherkin-wrapper @dir 0 ["--feature" "features/x.feature"] (str "kill -" signal " $PPID; kill -" signal " $$")))
      (should= "Feature: x\n" (slurp (str (fs/path @dir "features" "x.feature"))))
      (should= block (slurp (str (fs/path @dir ".metrics" "gherkin" "features" "x.feature"))))))

  (it "only runs the tool without --feature"
    (should= 0 (run-gherkin-wrapper @dir 0 ["--level" "hard"]))
    (should (not (fs/exists? (fs/path @dir ".metrics"))))))
