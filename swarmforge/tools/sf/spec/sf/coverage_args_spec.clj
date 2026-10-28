(ns sf.coverage-args-spec
  (:require [speclj.core :refer [describe context it should=]]
            [sf.coverage-args :as coverage-args]))

(describe "absolute"
  (it "puts a relative path under the current directory"
    (should= "/m/tools/x/src" (coverage-args/absolute "/m" "tools/x/src")))

  (it "drops empty and . parts and steps back for .."
    (should= "/m/tools/x/src" (coverage-args/absolute "/m" "/m/tools/./x/../x//src/")))

  (it "writes the root as the empty path, as bash strips it"
    (should= "" (coverage-args/absolute "/m" "../.."))
    (should= "" (coverage-args/absolute "/" "."))))

(describe "relative"
  (it "is . for the directory itself"
    (should= "." (coverage-args/relative "/m/x" "/m/x")))

  (it "goes down into the directory"
    (should= "src/a" (coverage-args/relative "/m/x/src/a" "/m/x")))

  (it "goes up to the common parent first"
    (should= "../core/src" (coverage-args/relative "/m/core/src" "/m/x"))
    (should= "../.." (coverage-args/relative "/m" "/m/x/y")))

  (it "does not take a sibling with a common prefix for the directory"
    (should= "../xy" (coverage-args/relative "/m/xy" "/m/x")))

  (it "goes down from the root"
    (should= "m" (coverage-args/relative "/m" ""))))

(describe "physical"
  (it "is the target of an existing directory"
    (should= "/real/app" (coverage-args/physical {"/m/app" "/real/app"} "/m/app")))

  (it "is the name in the target of the parent when only the parent exists"
    (should= "/real/app/src" (coverage-args/physical {"/m/app" "/real/app"} "/m/app/src"))
    (should= "/src" (coverage-args/physical {"/m" "/"} "/m/src")))

  (it "is the path itself when neither exists"
    (should= "/m/missing/src" (coverage-args/physical {} "/m/missing/src"))))

(def ^:private config "/cfg/bb.edn")

(defn- plan
  "The plan from /m for args, with these directories (each its own target
  unless mapped) and files."
  [args & {:keys [dirs links files clj?]}]
  (coverage-args/plan {:here "/m" :args args :config config :clj? clj?
                       :directory (merge (zipmap dirs dirs) links)
                       :file? (set files)}))

(defn- command [& parts]
  (apply str "bb --config /cfg/bb.edn coverage" parts))

(describe "plan"
  (context "the directory"
    (it "is the directory of the first source root, with the source roots relative to it"
      (should= {:dir "/m/tools/x"
                :args ["--source-root" "src" "-s" "../core/src" "orders" "--lcov" "x/y"
                       "--coverage-command" (command " -r speclj -p src -p ../core/src -s spec --lcov")]}
               (plan ["--source-root" "tools/x/src" "-s" "tools/core/src" "orders" "--lcov" "x/y"]
                     :dirs ["/m" "/m/test" "/m/tools/x" "/m/tools/x/spec" "/m/tools/core/src"])))

    (it "comes from an absolute source root"
      (should= {:dir "/m/tools/x" :args ["-s" "src" "--coverage-command" (command " -p src --lcov")]}
               (plan ["-s" "/m/tools/./x/../x/src/"] :dirs ["/m/tools/x"])))

    (it "follows a symlinked directory to its target before making the roots relative"
      (should= ["/m/projects/app" ["-s" "src" "-s" "../core/src"]]
               (let [{:keys [dir args]} (plan ["-s" "linked/src" "-s" "projects/core/src"]
                                              :dirs ["/m/projects/app" "/m/projects/core/src"]
                                              :links {"/m/linked" "/m/projects/app"})]
                 [dir (take 4 args)])))

    (it "is not chosen without a source root, so crap4clj runs where it was called"
      (should= {:dir nil :args ["--coverage-command" (command " -p src --lcov")]} (plan [])))

    (it "skips empty source roots and the --lcov value"
      (should= {:dir nil :args ["-s" "" "--lcov" "-s" "x/src" "--coverage-command" (command " -p src --lcov")]}
               (plan ["-s" "" "--lcov" "-s" "x/src"] :dirs ["/m/x"])))

    (it "is missing when the parent of the first source root does not exist"
      (should= {:error "no such directory: /m/missing"} (plan ["--source-root" "missing/src"])))

    (it "is the root for a source root right under it"
      (should= {:dir "/" :args ["-s" "src" "--coverage-command" (command " -p src --lcov")]}
               (plan ["-s" "/src"]))))

  (context "the coverage command"
    (it "covers src and the test directories in the directory crap4clj runs in"
      (should= ["--coverage-command" (command " -p src -s test --lcov")] (:args (plan [] :dirs ["/m/test"]))))

    (it "runs speclj when spec exists, and covers the source roots the caller names"
      (should= ["--source-root" "lib" "-s" "scripts" "--lcov" "-s"
                "--coverage-command" (command " -r speclj -p lib -p scripts -s spec -s test --lcov")]
               (:args (plan ["--source-root" "lib" "-s" "scripts" "--lcov" "-s"]
                            :dirs ["/m" "/m/spec" "/m/test"]))))

    (it "is left out when the caller settled coverage"
      (should= ["--use-existing-coverage"] (:args (plan ["--use-existing-coverage"])))
      (should= ["--coverage-command" "x"] (:args (plan ["--coverage-command" "x"]))))

    (it "is still added for a settling flag that is an option value"
      (should= ["--lcov" "--use-existing-coverage" "--coverage-command" (command " -p src --lcov")]
               (:args (plan ["--lcov" "--use-existing-coverage"]))))

    (it "is added with the Clojure CLI but no deps.edn, and left out with both"
      (should= ["--coverage-command" (command " -p src --lcov")] (:args (plan [] :clj? true)))
      (should= [] (:args (plan [] :clj? true :files ["/m/deps.edn"])))
      (should= ["--coverage-command" (command " -p src --lcov")] (:args (plan [] :files ["/m/deps.edn"]))))

    (it "looks for deps.edn, spec and test in the directory of the first source root"
      (should= ["-s" "src"] (:args (plan ["-s" "x/src"] :dirs ["/m/x"] :clj? true :files ["/m/x/deps.edn"])))
      (should= (command " -p src -s test --lcov")
               (last (:args (plan ["-s" "x/src"] :dirs ["/m/spec" "/m/x" "/m/x/test"])))))))
