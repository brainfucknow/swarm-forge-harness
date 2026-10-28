(ns acceptance.metrics-steps
  "Step handlers for test/features/metrics_cache (see its README for the step
  meanings): the steps of acceptance.store-steps, and the ones for the
  swarmforge/metrics branch, the gherkin-mutator wrapper and install.sh."
  (:require [acceptance.store-steps :as store :refer [expected git must-in run-in when-step worktree write-file]]
            [acceptance.support :as support :refer [check require-ok]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def new-world store/new-world)

(def ^:private branch "refs/heads/swarmforge/metrics")

(defn- tip [world]
  (get (store/tips world) branch))

(defn- hold
  "Commits path with text and a newline on top of the metrics branch, or as its
  first commit, past sf and without a checkout."
  [world path text]
  (let [scratch (fs/path (:repo world) ".git" "acceptance")
        env {"GIT_INDEX_FILE" (str (fs/path scratch "index"))}
        indexed (fn [& cmd]
                  (let [result (run-in world (:repo world) cmd env)]
                    (require-ok result (str/join " " cmd))
                    (str/trim-newline (:out result))))
        parent (tip world)]
    (fs/delete-tree scratch)
    (write-file scratch "blob" (str text "\n"))
    (if parent (indexed "git" "read-tree" parent) (indexed "git" "read-tree" "--empty"))
    (indexed "git" "update-index" "--add" "--cacheinfo"
             (str "100644," (git world "hash-object" "-w" (str (fs/path scratch "blob"))) "," path))
    (let [commit (apply indexed "git" "commit-tree" (indexed "git" "write-tree") "-m" "seed metrics"
                        (when parent ["-p" parent]))]
      (git world "update-ref" branch commit))
    world))

(defn- fake-gherkin-mutator
  "A tool source whose gherkin-mutator task saves the --feature file it sees,
  puts block on it in place of any block, and exits with status."
  [world block status]
  (let [dir (str (:repo world) "-fake")
        script (str (fs/path dir "fake.clj"))]
    (fs/create-dirs dir)
    (spit script (pr-str `(let [args# (vec *command-line-args*)
                                feature# (get args# (inc (.indexOf args# "--feature")))
                                text# (slurp feature#)
                                body# (->> (clojure.string/split text# #"\n" -1)
                                           (drop-while #(clojure.string/starts-with? % "#"))
                                           (drop-while clojure.string/blank?))]
                            (spit ~(str (fs/path dir "seen.feature")) text#)
                            (spit feature# (str ~block "\n\n" (clojure.string/join "\n" body#)))
                            (System/exit ~status))))
    (spit (str (fs/path dir "bb.edn")) (pr-str {:tasks {'gherkin-mutator (list 'load-file script)}}))
    (require-ok (run-in world (:repo world) ["sf" "tool" "ensure" "gherkin-mutator"] {"SWARMFORGE_TOOL_SRC" dir})
                "sf tool ensure gherkin-mutator")
    (assoc world :fake dir)))

(defn- holds? [file text]
  (and (fs/exists? file) (= (str text "\n") (slurp (str file)))))

(def ^:private metrics-handlers
  [;; Given
   [#"a project with swarmforge initialized and .metrics/ ignored"
    (fn [world]
      (let [repo (support/project true)]
        (spit (str (fs/path repo ".gitignore")) ".metrics/\n" :append true)
        (support/must repo "git" "commit" "-q" "--amend" "-a" "--no-edit")
        (assoc world :repo repo)))]

   [#"the metrics branch holds the file \"([^\"]+)\" with \"([^\"]*)\""
    (fn [world path text] (hold world path (expected world text)))]

   [#"the tip of the metrics branch is named (M\d+)"
    (fn [world tip-name] (assoc-in world [:names tip-name] (tip world)))]

   [#"the file \"([^\"]+)\" in the worktree of task \"(\w)\" was deleted"
    (fn [world path name]
      (fs/delete (fs/path (worktree world name) path))
      world)]

   [#"the worktree of task \"(\w)\" has the committed file \"([^\"]+)\" that holds \"([^\"]*)\""
    (fn [world name path text]
      (let [dir (worktree world name)]
        (write-file dir path (str (expected world text) "\n"))
        (must-in world dir "git" "add" path)
        (must-in world dir "git" "commit" "-q" "-m" (str "Add " path))
        world))]

   [#"gherkin-mutator is a fake that writes the block \"([^\"]*)\" and exits with status (\d+)"
    (fn [world block status] (fake-gherkin-mutator world (expected world block) (parse-long status)))]

   [#"a git repository with one commit and no swarmforge"
    (fn [world]
      (let [repo (str (fs/path @support/scratch (str "bare-" (random-uuid))))]
        (fs/create-dirs repo)
        (support/must repo "git" "init" "-q" "-b" "main" ".")
        (support/must repo "git" "-c" "user.name=t" "-c" "user.email=t@x" "commit" "-q" "--allow-empty" "-m" "one")
        (assoc world :repo repo)))]

   ;; When
   [#"I run the gherkin-mutator wrapper with \"([^\"]*)\" in (?:the directory \"([^\"]+)\" of )?the worktree of task \"(\w)\""
    (fn [world args dir name]
      (let [wrapper (str (fs/path (:repo world) ".swarmforge" "bin" "gherkin-mutator"))]
        (when-step world #(run-in % (str (fs/path (worktree % name) (or dir ""))) (cons wrapper (str/split args #" "))))))]

   [#"I run install.sh into it (\d+) times"
    (fn [world times]
      (let [install (str (fs/path support/checkout "install.sh"))
            result (last (repeatedly (parse-long times) #(support/run (:repo world) [install (:repo world)])))]
        (assoc world :exit (:exit result) :out (:out result) :err (:err result))))]

   ;; Then
   [#"the file \"([^\"]+)\" in the worktree of task \"(\w)\" holds \"(.*)\""
    (fn [world path name text]
      (let [file (fs/path (worktree world name) path)]
        (check (holds? file (expected world text))
               (str path " holds " (pr-str (when (fs/exists? file) (slurp (str file))))))
        world))]

   [#"the worktree of task \"(\w)\" has no file \"([^\"]+)\""
    (fn [world name path]
      (check (not (fs/exists? (fs/path (worktree world name) path))) (str path " exists"))
      world)]

   [#"the file \"([^\"]+)\" in the worktree of task \"(\w)\" is as committed"
    (fn [world path name]
      (let [status (must-in world (worktree world name) "git" "status" "--porcelain" "--" path)]
        (check (str/blank? status) (str path ": " status))
        world))]

   [#"the fake saw the feature file holding \"(.*)\""
    (fn [world text]
      (let [file (fs/path (:fake world) "seen.feature")]
        (check (holds? file (expected world text)) (str "the fake saw " (pr-str (when (fs/exists? file) (slurp (str file))))))
        world))]

   [#"the file \"([^\"]+)\" on the metrics branch is \"(.*)\""
    (fn [world path text]
      (let [{:keys [exit out]} (run-in world (:repo world) ["git" "show" (str branch ":" path)])]
        (check (and (zero? exit) (= (expected world text) (str/trim-newline out))) (str path " was " (pr-str out)))
        world))]

   [#"the metrics branch is one commit with no parent"
    (fn [world]
      (let [commits (str/split-lines (git world "rev-list" branch))]
        (check (= [(first commits)] commits) (str "commits: " commits))
        (check (= (first commits) (git world "rev-list" "--parents" "-n" "1" branch)) "the tip has a parent")
        world))]

   [#"the tip commit of the metrics branch has the message \"(.*)\""
    (fn [world text]
      (let [actual (str/replace (git world "log" "-1" "--format=%B" branch) #"\n+\z" "")]
        (check (= (expected world text) actual) (str "message was " (pr-str actual)))
        world))]

   [#"the tip commit of the metrics branch changes the files \"(.*)\""
    (fn [world text]
      (let [actual (str/replace (git world "diff-tree" "--root" "--no-commit-id" "-r" "--name-status" "--no-renames" branch) "\t" " ")]
        (check (= (expected world text) actual) (str "it changes " (pr-str actual)))
        world))]

   [#"the parent of the tip commit of the metrics branch is (M\d+)"
    (fn [world tip-name]
      (check (= (get-in world [:names tip-name]) (git world "rev-parse" (str branch "^"))) "another parent")
      world)]

   [#"the metrics branch did not move"
    (fn [world]
      (check (= (get-in world [:before branch]) (tip world)) "the metrics branch moved")
      world)]

   [#"the .gitignore of the repository has the line \"([^\"]+)\" exactly once"
    (fn [world line]
      (let [found (filter #{line} (str/split-lines (slurp (str (fs/path (:repo world) ".gitignore")))))]
        (check (= 1 (count found)) (str line " is there " (count found) " times"))
        world))]])

(def handlers (concat store/handlers metrics-handlers))
