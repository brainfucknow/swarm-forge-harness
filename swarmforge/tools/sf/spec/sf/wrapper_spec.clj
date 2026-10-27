(ns sf.wrapper-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [speclj.core :refer [describe context it should should= with around]]
            [sf.wrapper :as wrapper]))

(describe "commands"
  (it "runs a bb task of the source's bb.edn"
    (should= "exec bb --config '/t/crap4clj/bb.edn' crap \"$@\""
             (wrapper/bb-command "/t/crap4clj" "crap")))

  (it "runs a main namespace from the source's src"
    (should= "exec bb -cp '/t/dry4clj/src' -m dry4clj.core \"$@\""
             (wrapper/bbmain-command "/t/dry4clj" "dry4clj.core")))

  (it "runs a local script from the caller's checkout when it has one, else the main checkout's"
    (should= (str "top=\"$(git rev-parse --show-toplevel 2>/dev/null)\" && [ -x \"$top/\"'bin/x' ] && exec \"$top/\"'bin/x' \"$@\"\n"
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

(defn- run-wrapper
  "Runs a crap4clj-style wrapper in dir with a fake bb (and clj when clj?) on PATH;
  returns the fake bb's arguments."
  [dir clj? args]
  (let [bin (fs/create-dirs (fs/path dir "fakebin"))
        script (str (fs/path dir "wrapper"))]
    (spit (str (fs/path bin "bb")) "#!/usr/bin/env bash\nprintf '%s\\n' \"$@\"\n")
    (when clj? (spit (str (fs/path bin "clj")) "#!/usr/bin/env bash\n"))
    (run! #(fs/set-posix-file-permissions % "rwxr-xr-x") (fs/list-dir bin))
    (spit script (wrapper/script (wrapper/coverage-rewrite "/cfg/bb.edn") (wrapper/bb-command "/src" "crap")))
    (-> (p/shell {:cmd (into ["bash" script] args) :dir (str dir) :out :string
                  :extra-env {"PATH" (str bin ":/usr/bin:/bin")}})
        :out str/split-lines)))

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
