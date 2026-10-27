(ns acceptance.manifests.steps
  "Step handlers for test/features/mutation_manifests/. Each execution gets a
  scratch git repository under tmp/acceptance/worlds/ with a copy of the
  swarmforge/ under test, and runs the real sf, git and git-bug in it. The
  step meanings are in test/features/mutation_manifests/README.md."
  (:require [acceptance.manifests.runtime :refer [fail!]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private root (str (fs/cwd)))

;; Processes

(defn- base-env []
  (into {} (remove (fn [[k _]] (str/starts-with? k "SWARMFORGE_"))) (System/getenv)))

(defn- find-program
  "The program as a path: a relative path from dir, then from the main
  checkout, or a name on the world's PATH."
  [world dir program]
  (if (str/includes? program "/")
    (let [local (fs/path dir program)]
      (str (if (fs/exists? local) local (fs/path (:repo world) program))))
    (or (some #(let [candidate (fs/path % program)]
                 (when (fs/executable? candidate) (str candidate)))
              (str/split (get-in world [:env "PATH"]) #":"))
        program)))

(defn- run [world dir & [program & args]]
  (let [result (p/shell {:cmd (into [(find-program world dir program)] args)
                         :dir (str dir) :env (:env world)
                         :out :string :err :string :continue true})]
    (select-keys result [:exit :out :err])))

(defn- must [world dir & cmd]
  (let [{:keys [exit out err]} (apply run world dir cmd)]
    (when-not (zero? exit)
      (fail! (str/join " " cmd) " exited " exit ": " (str/trim (str out err))))
    (str/trim-newline out)))

(defn- git [world dir & args]
  (apply must world dir "git" args))

(defn- sf [world & args]
  (apply must world (:repo world) "sf" args))

;; Text

(defn- unescape [text]
  (str/replace text "\\n" "\n"))

(defn- fill-names
  "Each word that names a task becomes its id."
  [world text]
  (str/replace text #"\b[A-Z]\b" #(get-in world [:tasks %] %)))

(defn- task-id [world name]
  (or (get-in world [:tasks name]) (fail! "no task " name)))

(defn- worktree [world name]
  (str (fs/path (:repo world) ".worktrees" (task-id world name))))

(defn- head [world name]
  (git world (worktree world name) "rev-parse" "--short" "HEAD"))

(defn- expected
  "An expected output with task names, HEAD and \\n filled in."
  [world text]
  (let [text (unescape (fill-names world text))]
    (if (re-find #"\bHEAD\b" text)
      (str/replace text #"\bHEAD\b" (head world (or (:command-task world) (fail! "no task for HEAD"))))
      text)))

(defn- check [ok? & message]
  (when-not ok? (apply fail! message)))

(defn- check-equal [expected actual]
  (check (= expected actual) "expected " (pr-str expected) ", got " (pr-str actual)))

;; Worlds

(defn- new-world []
  (let [scratch (fs/create-dirs (fs/path root "tmp" "acceptance" "worlds" (str (random-uuid))))
        home (str (fs/create-dirs (fs/path scratch "home")))
        repo (str (fs/path scratch "project"))]
    (spit (str (fs/path home ".gitconfig"))
          "[user]\n\tname = tester\n\temail = tester@example.com\n[init]\n\tdefaultBranch = main\n")
    {:scratch (str scratch) :repo repo :tasks {}
     :env (assoc (base-env)
                 "HOME" home
                 "PATH" (str repo "/swarmforge/bin:" (System/getenv "PATH")))}))

(defn- close-world [world]
  (when-not (System/getenv "ACCEPTANCE_KEEP")
    (fs/delete-tree (:scratch world))))

(defn- write-file [path text]
  (fs/create-dirs (fs/parent path))
  (spit (str path) text))

(defn- project [world]
  (let [repo (:repo world)]
    (fs/create-dirs repo)
    (git world repo "init" "-q" "-b" "main")
    (fs/copy-tree (fs/path root "swarmforge") (fs/path repo "swarmforge"))
    (spit (str (fs/path repo ".gitignore")) ".worktrees/\n.swarmforge/\ntmp/\n.metrics/\n")
    (git world repo "add" "-A")
    (git world repo "commit" "-q" "-m" "seed")
    (sf world "init")
    world))

(defn- bare-repository [world]
  (let [repo (:repo world)]
    (fs/create-dirs repo)
    (git world repo "init" "-q" "-b" "main")
    (spit (str (fs/path repo "README.md")) "hello\n")
    (git world repo "add" "-A")
    (git world repo "commit" "-q" "-m" "seed")
    world))

;; Tasks

(defn- created
  ([world name] (created world name "specifier"))
  ([world name route]
   (assoc-in world [:tasks name] (sf world "new" name "-m" name "--route" route))))

(defn- started
  ([world name] (started world name "specifier"))
  ([world name route]
   (let [world (created world name route)]
     (sf world "start" (task-id world name))
     world)))

(defn- finished [world name]
  (let [id (task-id world name)]
    (sf world "review" id)
    (sf world "approve" id)
    (sf world "finish" id)
    world))

(defn- write-worktree-file [world name path text]
  (write-file (fs/path (worktree world name) path) (str (unescape text) "\n"))
  world)

(defn- saved-at-new-commit [world name]
  (git world (worktree world name) "commit" "-q" "--allow-empty" "-m" "save")
  (sf world "manifest" "save" (task-id world name))
  world)

(defn- commas [text]
  (mapv parse-long (str/split text #",")))

(defn- clj-mutate-manifest [world path name source killed survived uncovered]
  (let [forms (mapv (fn [k s u] {:killed k :survived s :uncovered u})
                    (commas killed) (commas survived) (commas uncovered))]
    (write-worktree-file world name path (pr-str {:version 2 :source source :forms forms}))))

(defn- gherkin-mutator-manifest [world feature name killed survived]
  (let [scenarios (mapv (fn [k s] {"result" (array-map "Total" (+ k s) "Killed" k "Survived" s "Errors" 0)})
                        (commas killed) (commas survived))]
    (write-worktree-file world name (str ".metrics/gherkin/" feature)
                         (str "# acceptance-mutation-manifest-begin\n# "
                              (json/generate-string (array-map "feature_path" feature "scenarios" scenarios))
                              "\n# acceptance-mutation-manifest-end"))))

(defn- fake-gherkin-mutator
  "A tool source whose gherkin-mutator task saves the feature to
  tmp/seen.feature, puts block at its top in place of any block, and exits."
  [world block status]
  (let [src (fs/path (:scratch world) "tool-src")]
    (write-file (fs/path src "bb.edn")
                (str "{:tasks {gherkin-mutator {:requires ([babashka.fs :as fs] [clojure.string :as str])\n"
                     " :task (let [args (vec *command-line-args*)\n"
                     "             feature (get args (inc (.indexOf args \"--feature\")))\n"
                     "             text (slurp feature)\n"
                     "             body (drop-while (fn [line] (or (str/blank? line) (str/starts-with? line \"#\")))\n"
                     "                              (str/split text (re-pattern \"\\n\") -1))]\n"
                     "         (fs/create-dirs \"tmp\")\n"
                     "         (spit \"tmp/seen.feature\" text)\n"
                     "         (spit feature (str " (pr-str (unescape block)) " \"\\n\\n\" (str/join \"\\n\" body)))\n"
                     "         (System/exit " status "))}}}\n"))
    (must (assoc-in world [:env "SWARMFORGE_TOOL_SRC"] (str src)) (:repo world) "sf" "tool" "ensure" "gherkin-mutator")
    world))

(defn- audit-flag [world role]
  (let [file (str (fs/path (:repo world) "swarmforge" "roles.conf"))
        lines (str/split-lines (slurp file))]
    (spit file (str (str/join "\n" (map #(if (= role (first (str/split (str/trim %) #"\s+"))) (str % " audit") %) lines)) "\n"))
    world))

(defn- gitignore-without-metrics [world]
  (let [repo (:repo world)
        file (str (fs/path repo ".gitignore"))]
    (spit file (str (str/join "\n" (remove #{".metrics/"} (str/split-lines (slurp file)))) "\n"))
    (git world repo "commit" "-q" "-am" "Track .metrics/")
    world))

;; Commands

(defn- command-task
  "The name of the first task that the command names."
  [world command]
  (some #(when (contains? (:tasks world) %) %) (str/split command #" ")))

(defn- run-command
  ([world command] (run-command world (:repo world) command (command-task world command)))
  ([world dir command name]
   (let [words (str/split (fill-names world command) #" ")]
     (assoc world :result (apply run world dir words) :command-task name))))

(defn- run-install [world times]
  (reduce (fn [world _] (assoc world :result (run world root (str (fs/path root "install.sh")) (:repo world))))
          world (range (parse-long times))))

;; Outcomes

(defn- result [world key]
  (or (get-in world [:result key]) (fail! "no command ran")))

(defn- out-lines [world key]
  (str/split-lines (result world key)))

(defn- has-line [world key line]
  (let [line (expected world line)]
    (check (some #{line} (out-lines world key)) "no line " (pr-str line) " in " (pr-str (result world key))))
  world)

(defn- line-before-separator [world line]
  (let [line (expected world line)
        lines (out-lines world :out)
        at (.indexOf lines line)
        separator (.indexOf lines "---")]
    (check (and (<= 0 at) (< at separator)) (pr-str line) " is not before --- in " (pr-str (result world :out))))
  world)

(defn- file-holds [world path text]
  (check (fs/exists? path) path " does not exist")
  (check-equal (str (unescape text) "\n") (slurp (str path)))
  world)

(defn- note-on-head? [world name]
  (let [dir (worktree world name)]
    (zero? (:exit (run world dir "git" "notes" "--ref=mutate" "list" (head world name))))))

(defn- notes-copy [world path name text]
  (let [file (fs/path (worktree world name) path)]
    (fs/delete-if-exists file)
    (sf world "manifest" "load" (task-id world name))
    (file-holds world file text)))

(defn- as-committed [world path name]
  (check-equal "" (git world (worktree world name) "status" "--porcelain" "--" path))
  world)

(defn- line-count [world line]
  (let [file (fs/path (:repo world) ".gitignore")]
    (check-equal 1 (count (filter #{line} (str/split-lines (slurp (str file))))))
    world))

(def ^:private q "\"([^\"]*)\"")
(def ^:private task "task \"([A-Z])\"")

(defn- step [& parts]
  (re-pattern (str "^" (apply str parts) "$")))

(def suite
  {:new-world new-world
   :close-world close-world
   :handlers
   [[(step "a project with swarmforge initialized and \\.metrics/ ignored") project]
    [(step "a git repository with one commit and no swarmforge") bare-repository]
    [(step task " was created") created]
    [(step task " was started") started]
    [(step "task \"([A-Z])\" on route " q " was started") started]
    [(step task " was finished") finished]
    [(step task " finished with " q " saved as " q)
     (fn [world name path text]
       (-> (started world name)
           (write-worktree-file name path text)
           (saved-at-new-commit name)
           (finished name)))]
    [(step "the worktree file " q " of " task " is written with \"(.*)\"")
     (fn [world path name text] (write-worktree-file world name path text))]
    [(step "the worktree file " q " of " task " was deleted")
     (fn [world path name] (fs/delete (fs/path (worktree world name) path)) world)]
    [(step "the worktree of " task " has (\\d+) manifest files")
     (fn [world name n]
       (reduce #(write-worktree-file %1 name (str ".metrics/mutate/f" %2 ".edn") (str "manifest " %2))
               world (range 1 (inc (parse-long n)))))]
    [(step "the manifests of " task " were saved at a new commit") saved-at-new-commit]
    [(step "a commit was made in the worktree of " task)
     (fn [world name] (git world (worktree world name) "commit" "-q" "--allow-empty" "-m" "work") world)]
    [(step "the main checkout committed a \\.gitignore without \\.metrics/") gitignore-without-metrics]
    [(step "role \"([a-z-]+)\" has the audit flag in roles\\.conf") audit-flag]
    [(step "the clj-mutate manifest " q " of " task " names source " q " with forms killed " q ", survived " q " and uncovered " q)
     (fn [world path name source k s u] (clj-mutate-manifest world path name source k s u))]
    [(step "the gherkin-mutator manifest of feature " q " in " task " has scenarios killed " q " and survived " q)
     (fn [world feature name k s] (gherkin-mutator-manifest world feature name k s))]
    [(step "the worktree of " task " has the committed file " q " that holds \"(.*)\"")
     (fn [world name path text]
       (let [world (write-worktree-file world name path text)]
         (git world (worktree world name) "add" "--" path)
         (git world (worktree world name) "commit" "-q" "-m" (str "Add " path))
         world))]
    [(step "gherkin-mutator is a fake that writes the block \"(.*)\" and exits with status (\\d+)") fake-gherkin-mutator]
    [(step "I ran " q) (fn [world command] (dissoc (run-command world command) :result))]
    [(step "I run " q) (fn [world command] (run-command world command))]
    [(step "I run " q " in the worktree of " task)
     (fn [world command name] (run-command world (worktree world name) command name))]
    [(step "I run install\\.sh into it (\\d+) times") run-install]
    [(step "the exit status is (\\d+)")
     (fn [world status]
       (check (= (parse-long status) (result world :exit))
              "expected " status ", got " (result world :exit) "; stderr: " (str/trim (result world :err)))
       world)]
    [(step "the output is \"(.*)\"")
     (fn [world text] (check-equal (expected world text) (str/trim-newline (result world :out))) world)]
    [(step "the output is empty") (fn [world] (check-equal "" (result world :out)) world)]
    [(step "the output has the line " q) (fn [world line] (has-line world :out line))]
    [(step "the output has the line " q " before the line \"---\"") line-before-separator]
    [(step "the error output has the line " q) (fn [world line] (has-line world :err line))]
    [(step "the worktree file " q " of " task " holds \"(.*)\"")
     (fn [world path name text] (file-holds world (fs/path (worktree world name) path) text))]
    [(step "the worktree file " q " of " task " does not exist")
     (fn [world path name]
       (check (not (fs/exists? (fs/path (worktree world name) path))) path " exists")
       world)]
    [(step "the HEAD commit of " task " has a note in refs/notes/mutate")
     (fn [world name] (check (note-on-head? world name) "HEAD has no note") world)]
    [(step "the HEAD commit of " task " has no note in refs/notes/mutate")
     (fn [world name] (check (not (note-on-head? world name)) "HEAD has a note") world)]
    [(step "the notes' copy of " q " for " task " is \"(.*)\"") notes-copy]
    [(step "the fake saw the feature file holding \"(.*)\"")
     (fn [world text]
       (file-holds world (fs/path (worktree world (:command-task world)) "tmp" "seen.feature") text))]
    [(step "the file " q " of " task " is as committed") as-committed]
    [(step "the \\.gitignore of the repository has the line " q " exactly once") line-count]]})
