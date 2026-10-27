(ns sf.tool
  "sf tool: installs the constitution tools in the project and wraps them with
  the argument guardrails of the constitution. Catalogue: swarmforge/tools.conf."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.shell :as shell]))

(def usage "usage: sf tool <command> [tool]

  require <tool>   print OK and the wrapper path, or MISSING (exit 1)
  ensure <tool>    install the tool and what it needs; write its wrapper
  list             print the catalogue
  bin              print the wrapper directory; put it on PATH

Wrappers live in .swarmforge/bin/ and sources in .swarmforge/tools/, both
under the repository root and ignored by git. SWARMFORGE_TOOL_SRC points every
GitHub source at one local checkout instead of cloning.")

(defn- spec-of [tool]
  (let [[_ kind source task rewrite needs] (or (some #(when (= tool (first %)) %) (config/rows "tools.conf"))
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
  "The local checkout of a GitHub source, cloned on first use."
  [source]
  (let [override (shell/env "SWARMFORGE_TOOL_SRC" nil)
        dir (or override (str (fs/path (shell/repo-root) ".swarmforge" "tools" (fs/file-name source))))]
    (when-not (fs/exists? (fs/path dir "bb.edn"))
      (when override (shell/die "SWARMFORGE_TOOL_SRC has no bb.edn: " dir))
      (fs/create-dirs (fs/parent dir))
      (when-not (shell/ok? (shell/run {:err :inherit} "git" "clone" "--quiet" "--depth" "1" (str "https://" source ".git") dir))
        (shell/die "failed to clone https://" source ".git")))
    dir))

(defn- sq
  "Single-quotes a word for bash."
  [word]
  (str "'" (str/replace word "'" "'\\''") "'"))

(defn- rewrite-for [rewrite]
  (case rewrite
    "mutate" (shell/template "rewrite-mutate.sh")
    "gherkin" (shell/template "rewrite-gherkin.sh")
    "-" ""
    (shell/die "unknown rewrite '" rewrite "' in swarmforge/tools.conf")))

(def ^:private speclj-dep "speclj/speclj {:mvn/version \"3.13.0\"}")

(defn- clj-command [tool coordinate main]
  (case tool
    "cloverage" (str "exec clojure -Sdeps " (sq (str "{:paths [\"src\" \"spec\" \"test\"] :deps {cloverage/cloverage {:mvn/version \"RELEASE\"} " speclj-dep "}}"))
                     " -M -m " main " -p src -s spec -s test -r speclj \"$@\"")
    "speclj" (str "exec clojure -Sdeps " (sq (str "{:paths [\"src\" \"spec\" \"test\"] :deps {" speclj-dep "}}"))
                  " -M -m " main " -c spec \"$@\"")
    (str "exec clojure -Sdeps " (sq (str "{:deps {" coordinate " {:mvn/version \"RELEASE\"}}}"))
         " -M -m " main " \"$@\"")))

(defn- install-one [tool]
  (let [{:keys [kind source task rewrite]} (spec-of tool)
        command (case kind
                  "bb" (str "exec bb --config " (sq (str (fs/path (ensure-source source) "bb.edn"))) " " task " \"$@\"")
                  "clj" (clj-command tool source task)
                  "local" (str "exec " (sq (str (fs/path (shell/repo-root) source))) " \"$@\"")
                  (shell/die "unknown kind '" kind "' for " tool))
        path (wrapper-path tool)]
    (fs/create-dirs (bin-dir))
    (spit path (str "#!/usr/bin/env bash\n" (rewrite-for rewrite) command "\n"))
    (fs/set-posix-file-permissions path "rwxr-xr-x")
    (println (str "INSTALLED: " tool " " path))))

(defn- ensure-tool [tool]
  (run! ensure-tool (:needs (spec-of tool)))
  (install-one tool))

(defn- list-tools []
  (doseq [[name kind source] (config/rows "tools.conf")]
    (println (format "%-22s %-5s %s" name kind source))))

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
