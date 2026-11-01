(ns sf.wrapper
  "The text of the wrapper scripts sf tool writes, and of the Babashka coverage
  config it writes for crap4clj."
  (:require [clojure.string :as str]
            [sf.shell :as shell]))

(defn sq
  "Single-quotes a word for bash."
  [word]
  (str "'" (.replace (str word) "'" "'\\''") "'"))

(defn script
  "A bash wrapper: the argument rewriting prelude, then the command."
  [prelude command]
  (str "#!/usr/bin/env bash\n" prelude command "\n"))

(defn bb-command [source-dir task]
  (str "exec bb --config " (sq (str source-dir "/bb.edn")) " " (sq task) " \"$@\""))

(defn bbmain-command [source-dir namespace]
  (str "exec bb -cp " (sq (str source-dir "/src")) " -m " (sq namespace) " \"$@\""))

(defn local-command
  "Runs the script at source in the caller's checkout when it has one and shares
  the main checkout's git directory, else the main checkout's."
  [main-root source]
  (str "top=\"$(git rev-parse --show-toplevel 2>/dev/null)\""
       " && [ \"$(git rev-parse --path-format=absolute --git-common-dir)\" = " (sq (str main-root "/.git")) " ]"
       " && [ -x \"$top/\"" (sq source) " ] && exec \"$top/\"" (sq source) " \"$@\"\n"
       "exec " (sq (str main-root "/" source)) " \"$@\""))

(defn keeping-feature-block
  "Runs command (an exec line) so that the block of the --feature file stays
  in .metrics/gherkin/ between runs, through sf.gherkin-cache of sf-src. Exits
  with the command's status."
  [sf-src command]
  (-> (shell/template "keep-feature-block.sh")
      (str/replace "{{src}}" (sq sf-src))
      (str/replace "{{command}}" (str/replace-first command #"^exec " ""))
      str/trim-newline))

(defn- sdeps
  "The -Sdeps argument: paths (or none) and each [coordinate version] of deps
  at its Maven version."
  [paths deps]
  (sq (str "{" (when paths (str ":paths " (pr-str paths) " "))
           ":deps {" (str/join " " (for [[coordinate version] deps]
                                     (str coordinate " {:mvn/version " (pr-str version) "}")))
           "}}")))

(defn clj-command
  "Runs the main namespace of a clj tool with the Clojure CLI, its Maven
  dependencies given as [coordinate version] pairs."
  [tool deps main]
  (case tool
    "cloverage" (str "exec clojure -Sdeps " (sdeps ["src" "spec" "test"] deps)
                     " -M -m " (sq main) " -p src -s spec -s test -r speclj \"$@\"")
    "speclj" (str "exec clojure -Sdeps " (sdeps ["src" "spec" "test"] deps)
                  " -M -m " (sq main) " -c spec \"$@\"")
    (str "exec clojure -Sdeps " (sdeps nil deps) " -M -m " (sq main) " \"$@\"")))

(defn coverage-rewrite
  "Has sf.coverage-rewrite of sf-src, run by the Babashka executable bb, move
  into the directory of the first source root, with the source roots made
  relative to it, and append --coverage-command for the Babashka coverage
  config unless the caller settled coverage or the Clojure CLI and deps.edn
  are there."
  [bb sf-src config-path]
  (-> (shell/template "rewrite-coverage.sh")
      (str/replace "{{bb}}" (sq bb))
      (str/replace "{{src}}" (sq sf-src))
      (str/replace "{{config}}" (sq config-path))))

(defn coverage-config
  "A bb.edn whose coverage task runs Cloverage, at the Babashka-compatible commit
  crap4clj documents, through crap4clj.bb-coverage of the clone in source-dir.
  The project's own bb.edn :paths (such as a sibling ../core/src) go on the
  classpath too, so its namespaces load."
  [source-dir]
  (str "{:deps {cloverage/cloverage {:git/url \"https://github.com/cloverage/cloverage.git\"
                             :git/sha \"61e3cac426e9907a9dd01c37597f85c71a57ff90\"
                             :deps/root \"cloverage\"}
        speclj/speclj {:mvn/version \"3.12.2\"}}
 :tasks
 {coverage
  {:doc \"Run Cloverage on the -p and -s directories through crap4clj.bb-coverage\"
   :requires ([babashka.classpath :as classpath]
              [babashka.fs :as fs]
              [clojure.string :as str]
              [edamame.core :as edamame])
   :task (let [args *command-line-args*
               given (keep (fn [[flag dir]] (when (#{\"-p\" \"-s\"} flag) dir))
                           (partition 2 1 args))
               project (when (fs/exists? \"bb.edn\")
                         (:paths (edamame/parse-string (slurp \"bb.edn\")
                                                       {:all true :auto-resolve name :read-cond :allow
                                                        :features #{:bb :clj} :readers (fn [_tag] identity)})))
               dirs (concat [" (pr-str (str source-dir "/src")) "] given project)]
           (classpath/add-classpath
             (str/join fs/path-separator (map (fn [dir] (str (fs/absolutize dir))) dirs)))
           (when (some #{\"speclj\"} args) (require 'speclj.cloverage))
           (require 'crap4clj.bb-coverage)
           (apply (resolve 'crap4clj.bb-coverage/-main) args))}}}
"))
