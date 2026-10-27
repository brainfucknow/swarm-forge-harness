(ns sf.tool
  "sf tool: installs the constitution tools in the project and wraps them with
  the argument guardrails of the constitution. Catalogue: swarmforge/tools.conf
  of the checkout sf tool runs in."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
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
  "The tool's source directory. A source without the entry point gets no
  wrapper, and loses an old one."
  [tool {:keys [kind source task]}]
  (let [dir (ensure-source source)]
    (when-let [problem (entry-point/problem kind dir task)]
      (fs/delete-if-exists (wrapper-path tool))
      (shell/die tool ": " problem))
    dir))

(defn- coverage-config-path [tool]
  (str (fs/path (shell/repo-root) ".swarmforge" "tools" (str tool "-coverage") "bb.edn")))

(defn- rewrite-for [rewrite tool]
  (case rewrite
    "mutate" (shell/template "rewrite-mutate.sh")
    "gherkin" (shell/template "rewrite-gherkin.sh")
    "coverage" (wrapper/coverage-rewrite (coverage-config-path tool))
    "-" ""
    (shell/die "unknown rewrite '" rewrite "' in swarmforge/tools.conf")))

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
    "local" (wrapper/local-command (shell/repo-root) source)
    (shell/die "unknown kind '" kind "' for " tool)))

(defn- install-one [tool]
  (let [spec (spec-of tool)
        command (command-for tool spec)
        path (wrapper-path tool)]
    (fs/create-dirs (bin-dir))
    (spit path (wrapper/script (rewrite-for (:rewrite spec) tool) command))
    (fs/set-posix-file-permissions path "rwxr-xr-x")
    (println (str "INSTALLED: " tool " " path))))

(defn- ensure-tool [tool]
  (run! ensure-tool (:needs (spec-of tool)))
  (install-one tool))

(defn- list-tools []
  (doseq [[name kind source] (catalogue)]
    (println (format "%-22s %-6s %s" name kind source))))

(defn main
  "Runs one sf tool command and returns its exit status."
  [[command tool]]
  (shell/main-with
   "sf tool: "
   #(case command
      "require" (do (when (str/blank? tool) (shell/die "require needs a tool")) (require-tool tool))
      "ensure" (do (when (str/blank? tool) (shell/die "ensure needs a tool")) (ensure-tool tool))
      "list" (list-tools)
      "bin" (println (bin-dir))
      (nil "" "-h" "--help" "help") (println usage)
      (shell/die "unknown command '" command "'"))))
