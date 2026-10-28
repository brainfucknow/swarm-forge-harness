(ns sf.tool
  "sf tool: installs the constitution tools in the project and wraps them with
  the argument guardrails of the constitution. Catalogue: swarmforge/tools.conf
  of the checkout sf tool runs in."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.entry-point :as entry-point]
            [sf.shell :as shell]
            [sf.wrapper :as wrapper]))

(def usage "usage: sf tool <command> [tool]

  require <tool>   print OK and the wrapper path, or MISSING (exit 1)
  ensure <tool>    install the tool and what it needs; write its wrapper
  list             print the catalogue
  bin              print the wrapper directory; put it on PATH

The catalogue is swarmforge/tools.conf of the checkout you run in. Wrappers
live in .swarmforge/bin/ and sources in .swarmforge/tools/, both under the
main checkout and ignored by git. SWARMFORGE_TOOL_SRC points every
GitHub source at one local checkout instead of cloning.")

(defn- catalogue
  "The rows of swarmforge/tools.conf of the checkout sf tool runs in."
  []
  (shell/conf-rows (fs/path (shell/checkout-root) "swarmforge" "tools.conf")))

(defn- spec-of [tool]
  (let [[_ kind source task rewrite needs] (or (some #(when (= tool (first %)) %) (catalogue))
                                               (shell/die "unknown tool '" tool "' (see swarmforge/tools.conf)"))]
    {:kind kind :source source :task task :rewrite rewrite
     :needs (if (= "-" needs) [] (str/split needs #","))}))

(defn- bin-dir []
  (str (fs/path (shell/repo-root) ".swarmforge" "bin")))

(defn- wrapper-path [tool]
  (str (fs/path (bin-dir) tool)))

(defn- require-tool [tool]
  (doseq [dep (conj (:needs (spec-of tool)) tool)]
    (when-not (fs/executable? (wrapper-path dep))
      (println (str "MISSING: " dep))
      (println (str "Run: sf tool ensure " dep))
      (shell/fail 1 nil)))
  (println (str "OK: " tool " " (wrapper-path tool))))

(defn- ensure-source
  "The local checkout of a GitHub source. A directory that exists is used as it
  is; only a missing one is cloned."
  [source]
  (let [override (shell/env "SWARMFORGE_TOOL_SRC" nil)
        dir (or override (str (fs/path (shell/repo-root) ".swarmforge" "tools" (fs/file-name source))))]
    (when-not (or override (fs/exists? dir))
      (fs/create-dirs (fs/parent dir))
      (when-not (shell/ok? (shell/run {:err :inherit} "git" "clone" "--quiet" "--depth" "1" (str "https://" source ".git") dir))
        (shell/die "failed to clone https://" source ".git")))
    dir))

(defn- checked-source
  "The tool's source directory. A source without the entry point stops the
  install and leaves any earlier wrapper as it is."
  [tool {:keys [kind source task]}]
  (let [dir (ensure-source source)]
    (when-let [problem (entry-point/problem kind dir task)]
      (shell/die tool ": " problem))
    dir))

(defn- coverage-config-path [tool]
  (str (fs/path (shell/repo-root) ".swarmforge" "tools" (str tool "-coverage") "bb.edn")))

(defn- sf-src []
  (str (fs/path (shell/repo-root) "swarmforge" "tools" "sf" "src")))

(defn- rewrite-for [rewrite tool]
  (case rewrite
    "mutate" (shell/template "rewrite-mutate.sh")
    "gherkin" (shell/template "rewrite-gherkin.sh")
    "coverage" (wrapper/coverage-rewrite (str (fs/which "bb")) (sf-src) (coverage-config-path tool))
    "-" ""))

(defn- write-coverage-config [tool dir]
  (let [path (coverage-config-path tool)]
    (fs/create-dirs (fs/parent path))
    (spit path (wrapper/coverage-config dir))))

(defn- command-for [tool {:keys [kind source task rewrite] :as spec}]
  (case kind
    "bb" (let [dir (checked-source tool spec)]
           (when (= "coverage" rewrite) (write-coverage-config tool dir))
           (wrapper/bb-command dir task))
    "bbmain" (wrapper/bbmain-command (checked-source tool spec) task)
    "clj" (wrapper/clj-command tool source task)
    "local" (wrapper/local-command (shell/repo-root) source)))

(defn- install-one [tool]
  (let [spec (spec-of tool)
        command (cond->> (command-for tool spec)
                  (= "gherkin" (:rewrite spec))
                  (wrapper/keeping-feature-block (sf-src)))
        path (wrapper-path tool)]
    (fs/create-dirs (bin-dir))
    (spit path (wrapper/script (rewrite-for (:rewrite spec) tool) command))
    (fs/set-posix-file-permissions path "rwxr-xr-x")
    (println (str "INSTALLED: " tool " " path))))

(defn- check-known
  "Stops when the tool or a tool in its needs chain has a kind or rewrite sf
  tool does not know."
  [tool]
  (let [{:keys [kind rewrite needs]} (spec-of tool)]
    (run! check-known needs)
    (when-not (config/tool-kinds kind) (shell/die "unknown kind '" kind "' for " tool))
    (when-not (config/tool-rewrites rewrite) (shell/die "unknown rewrite '" rewrite "' in swarmforge/tools.conf"))))

(defn- install-with-needs [tool]
  (run! install-with-needs (:needs (spec-of tool)))
  (install-one tool))

(defn- ensure-tool [tool]
  (check-known tool)
  (install-with-needs tool))

(defn- list-tools []
  (doseq [[name kind source] (catalogue)]
    (println (format "%-22s %-6s %s" name kind source))))

(defn- named
  "The tool the command was given; stops when there is none."
  [command tool]
  (when (str/blank? tool) (shell/die command " needs a tool"))
  tool)

(defn main
  "Runs one sf tool command and returns its exit status."
  [[command tool]]
  (shell/main-with
   "sf tool: "
   #(case command
      "require" (require-tool (named command tool))
      "ensure" (ensure-tool (named command tool))
      "list" (list-tools)
      "bin" (println (bin-dir))
      (nil "" "-h" "--help" "help") (println usage)
      (shell/die "unknown command '" command "'"))))
