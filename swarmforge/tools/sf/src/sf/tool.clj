(ns sf.tool
  "sf tool: installs the constitution tools in the project and wraps them with
  the argument guardrails of the constitution. Catalogue: swarmforge/tools.conf
  of the checkout sf tool runs in."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.catalogue :as catalogue]
            [sf.clone :as clone]
            [sf.entry-point :as entry-point]
            [sf.shell :as shell]
            [sf.wrapper :as wrapper]))

(def usage "usage: sf tool <command> [tool]

  require <tool>   print OK and the wrapper path, or MISSING (exit 1)
  ensure <tool>    install the tool and what it needs; write its wrapper
  list             print the catalogue
  bin              print the wrapper directory; put it on PATH

The catalogue is swarmforge/tools.conf of the checkout you run in. Each row
pins a ref: ensure puts a GitHub source's clone at its commit and prints it
after the wrapper path (@<sha>), and a clj tool's wrapper uses its Maven
version (@<version>). Wrappers live in .swarmforge/bin/ and clones in
.swarmforge/tools/, both under the main checkout and ignored by git.
SWARMFORGE_TOOL_SRC points every GitHub source at one local checkout, used as
it is: unpinned, never fetched or checked out, and printed without @.")

(defn- tools
  "The tools of swarmforge/tools.conf of the checkout sf tool runs in."
  []
  (map catalogue/tool (shell/conf-rows (fs/path (shell/checkout-root) "swarmforge" "tools.conf"))))

(defn- spec-of [tool]
  (or (some #(when (= tool (:name %)) %) (tools))
      (shell/die "unknown tool '" tool "' (see swarmforge/tools.conf)")))

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

(defn- pinned-source
  "The tool's source directory, its commit, and a function that puts the clone
  back where it was. SWARMFORGE_TOOL_SRC is used as it is, with no commit."
  [tool {:keys [source ref]}]
  (if-let [override (shell/env "SWARMFORGE_TOOL_SRC" nil)]
    [override nil (constantly nil)]
    (let [dir (str (fs/path (shell/repo-root) ".swarmforge" "tools" (catalogue/github-repo source)))
          url (str "https://" source ".git")
          earlier (clone/head dir)]
      (clone/ensure-at tool dir url ref)
      [dir ref #(when (and earlier (not= earlier ref)) (clone/ensure-at tool dir url earlier))])))

(defn- checked-source
  "The tool's source directory and its commit. A source without the entry
  point stops the install, puts the clone back at its earlier commit, and
  leaves any earlier wrapper as it is."
  [tool {:keys [kind task] :as spec}]
  (let [[dir pin restore] (pinned-source tool spec)]
    (when-let [problem (entry-point/problem kind dir task)]
      (restore)
      (shell/die tool ": " problem))
    [dir pin]))

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

(defn- clj-deps
  "The Maven dependencies of a clj tool's wrapper as [coordinate version]
  pairs: its own, then those of the rows it takes a pin from."
  [tool {:keys [source ref]}]
  (into [[source ref]] (map (comp (juxt :source :ref) spec-of)) (catalogue/pin-sources tool)))

(defn- command-for
  "The wrapper command for the tool and the ref it is pinned at, if any."
  [tool {:keys [kind source ref task rewrite] :as spec}]
  (case kind
    "bb" (let [[dir pin] (checked-source tool spec)]
           (when (= "coverage" rewrite) (write-coverage-config tool dir))
           [(wrapper/bb-command dir task) pin])
    "bbmain" (let [[dir pin] (checked-source tool spec)]
               [(wrapper/bbmain-command dir task) pin])
    "clj" [(wrapper/clj-command tool (clj-deps tool spec) task) ref]
    "local" [(wrapper/local-command (shell/repo-root) source) nil]))

(defn- install-one [tool]
  (let [spec (spec-of tool)
        [command pin] (command-for tool spec)
        command (cond->> command
                  (= "gherkin" (:rewrite spec))
                  (wrapper/keeping-feature-block (sf-src)))
        path (wrapper-path tool)]
    (fs/create-dirs (bin-dir))
    (spit path (wrapper/script (rewrite-for (:rewrite spec) tool) command))
    (fs/set-posix-file-permissions path "rwxr-xr-x")
    (println (str "INSTALLED: " tool " " path (when pin (str " @" pin))))))

(defn- check-known
  "Stops when the tool, a tool it needs, or a row it takes a pin from has an
  unknown kind or rewrite, or breaks a ref or source rule."
  [tool]
  (let [{:keys [kind rewrite needs] :as spec} (spec-of tool)]
    (run! check-known needs)
    (when-let [problem (first (catalogue/ref-problems spec (tools)))]
      (shell/die "tools.conf: " problem))
    (when-not (catalogue/kinds kind) (shell/die "unknown kind '" kind "' for " tool))
    (when-not (catalogue/rewrites rewrite) (shell/die "unknown rewrite '" rewrite "' in swarmforge/tools.conf"))
    (run! check-known (catalogue/pin-sources tool))))

(defn- install-with-needs [tool]
  (run! install-with-needs (:needs (spec-of tool)))
  (install-one tool))

(defn- ensure-tool [tool]
  (check-known tool)
  (install-with-needs tool))

(defn- list-tools []
  (let [rows (tools)
        width (apply max 0 (map (comp count :source) rows))]
    (doseq [{:keys [name kind source ref]} rows]
      (println (format (str "%-22s %-6s %-" (max 1 width) "s %s") name kind source ref)))))

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
