(ns acceptance.store-steps
  "Step handlers for test/features/task_store (see its README for the step
  meanings and placeholders). The scratch repositories come from
  acceptance.support. acceptance.metrics-steps builds on them."
  (:require [acceptance.support :as support :refer [run unescape check require-ok]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(defn new-world [] {:ids {} :created {} :names {} :env {}})

(defn id-of
  "The id of task name, or of the task that sf new printed when name is nil."
  [world name]
  (or (if name (get-in world [:ids name]) (:new world))
      (throw (ex-info (str "no task " (or name "NEW")) {}))))

(defn worktree [world name]
  (str (fs/path (:repo world) ".worktrees" (id-of world name))))

(defn- branch-ref [world name]
  (str "refs/heads/swarm/" (id-of world name)))

(defn fill
  "text with $A, $A:hex, $A:created, NEW, ROOT and named commits filled in."
  [world text]
  (-> text
      (str/replace #"\$([A-Z])(:hex|:created)?"
                   (fn [[_ name part]]
                     (let [id (id-of world name)]
                       (case part
                         ":hex" (last (str/split id #"-"))
                         ":created" (get-in world [:created name])
                         id))))
      (str/replace #"\bNEW\b" (fn [_] (id-of world nil)))
      (str/replace #"\b[CLTM]\d+\b" (fn [name] (or (get-in world [:names name])
                                                   (throw (ex-info (str "no value for " name) {})))))
      (str/replace "ROOT" (:repo world))))

(defn expected [world text]
  (fill world (unescape text)))

(defn run-in
  "Runs program and args in dir with the world's environment and env."
  ([world dir cmd] (run-in world dir cmd {}))
  ([world dir [program & args] env]
   (run dir (cons (if (= "sf" program) (support/sf-path (:repo world)) program) args)
        (merge (:env world) env))))

(defn must-in [world dir & cmd]
  (let [result (run-in world dir cmd)]
    (require-ok result (str/join " " cmd))
    (str/trim-newline (:out result))))

(defn git [world & args]
  (apply must-in world (:repo world) "git" args))

(defn tips
  "The tip of each branch, by ref."
  [world]
  (into {} (for [line (str/split-lines (git world "for-each-ref" "--format=%(refname) %(objectname)" "refs/heads/"))]
             (str/split line #" "))))

(defn- under-test
  "The world after the step under test: its result, and the branch tips before it."
  [world result]
  (assoc world :before (:tips world) :exit (:exit result) :out (:out result) :err (:err result)))

(defn when-step
  "Runs (f world) as the step under test."
  [world f]
  (let [world (assoc world :tips (tips world))]
    (under-test world (f world))))

(defn- create-task [world name title request route]
  (let [id (must-in world (:repo world) "sf" "new" title "-m" (unescape request) "--route" route)
        world (assoc-in world [:ids name] id)]
    (assoc-in world [:created name] (git world "rev-parse" "--short" (branch-ref world name)))))

(defn- start-task [world name]
  (must-in world (:repo world) "sf" "start" (id-of world name))
  world)

(defn- handoff-to-done [world name]
  (let [id (id-of world name)
        stage (must-in world (:repo world) "sf" "stage" id)]
    (when (not= "done" stage)
      (must-in world (:repo world) "sf" "handoff" id "--to" "done"))))

(defn- commit [world name message env]
  (run-in world (worktree world name) ["git" "commit" "-q" "--allow-empty" "-m" (unescape message)] env))

(defn- name-commit [world dir name]
  (let [sha (must-in world dir "git" "rev-parse" "--short" "HEAD")
        lore (str/trim (must-in world dir "git" "log" "-1" "--format=%(trailers:key=Lore-id,valueonly)"))]
    (cond-> (assoc-in world [:names name] sha)
      (seq lore) (assoc-in [:names (str/replace name #"^C" "L")] lore))))

(defn write-file [dir path text]
  (let [file (fs/path dir path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text)))

(defn- lines [text]
  (str/split-lines (str/trim-newline (or text ""))))

(defn- index-of-lines
  "The index where the lines of wanted start as consecutive lines of actual, or nil."
  [actual wanted]
  (first (for [i (range (inc (- (count actual) (count wanted))))
               :when (= wanted (subvec actual i (+ i (count wanted))))]
           i)))

(defn- show-file [world name path]
  (run-in world (:repo world) ["git" "show" (str (branch-ref world name) ":" path)]))

(defn- date-pattern
  "A regular expression that matches text, with DATE matching any date."
  [text]
  (re-pattern (str/join "\\d{4}-\\d{2}-\\d{2}" (map #(java.util.regex.Pattern/quote %) (str/split text #"DATE" -1)))))

(defn- commit-message [world rev]
  (str/replace (git world "log" "-1" "--format=%B" rev) #"\n+\z" ""))

(defn- diff-tree [world name & flags]
  (apply git world "diff-tree" "--no-commit-id" "-r" (concat flags [(branch-ref world name)])))

(def ^:private task #"task (?:\"(\w)\"|NEW)")

(defn- step
  "A step pattern with {task} standing for a task reference."
  [text]
  (re-pattern (str/replace text "{task}" (str task))))

(def handlers
  [;; Given
   [#"a project with swarmforge initialized"
    (fn [world] (assoc world :repo (support/project true)))]

   [#"a git repository with a copy of swarmforge and no git-bug on PATH"
    (fn [world] (assoc world :repo (support/project false)))]

   [#"the git identity of the project is \"(.*)\""
    (fn [world identity]
      (if (= "-" identity)
        (let [home (str (fs/path (:repo world) ".." (str (fs/file-name (:repo world)) "-home")))]
          (fs/create-dirs home)
          (spit (str (fs/path home ".gitconfig")) support/gitconfig)
          (run-in world (:repo world) ["git" "config" "--unset" "user.name"])
          (run-in world (:repo world) ["git" "config" "--unset" "user.email"])
          (assoc-in world [:env "HOME"] home))
        (let [[_ name email] (re-matches #"(.*) <(.*)>" identity)]
          (git world "config" "user.name" name)
          (git world "config" "user.email" email)
          world)))]

   [#"task \"(\w)\" titled \"([^\"]*)\"(?: with the request \"([^\"]*)\")? was (created|started) on route \"([^\"]*)\""
    (fn [world name title request how route]
      (cond-> (create-task world name title (or request title) route)
        (= "started" how) (start-task name)))]

   [#"task \"(\w)\" was finished"
    (fn [world name]
      (handoff-to-done world name)
      (must-in world (:repo world) "sf" "finish" (id-of world name))
      world)]

   [#"task \"(\w)\" was handed off to done and squash-merged into main"
    (fn [world name]
      (handoff-to-done world name)
      (git world "merge" "--squash" (str "swarm/" (id-of world name)))
      (git world "commit" "-q" "-m" (str "Squash " name))
      world)]

   [#"the worktree and the branch of task \"(\w)\" were removed"
    (fn [world name]
      (git world "worktree" "remove" "--force" (worktree world name))
      (git world "branch" "-D" (str "swarm/" (id-of world name)))
      world)]

   [#"role \"([^\"]+)\" committed (?:the file \"([^\"]+)\" with )?\"([^\"]*)\" in the worktree of task \"(\w)\" as (C\d+)"
    (fn [world role path message name commit-name]
      (let [dir (worktree world name)]
        (when path
          (write-file dir path "x\n")
          (must-in world dir "git" "add" path))
        (require-ok (commit world name message {"SWARMFORGE_ROLE" role}) "git commit")
        (name-commit world dir commit-name)))]

   [#"the worktree of task \"(\w)\" has the staged new file \"([^\"]+)\" and the unstaged change \"([^\"]+)\""
    (fn [world name staged unstaged]
      (let [dir (worktree world name)]
        (write-file dir unstaged "1\n")
        (must-in world dir "git" "add" unstaged)
        (require-ok (commit world name (str "Add " unstaged) {}) "git commit")
        (spit (str (fs/path dir unstaged)) "2\n" :append true)
        (write-file dir staged "s\n")
        (must-in world dir "git" "add" staged)
        world))]

   [#"the file \"([^\"]+)\" in the worktree of task \"(\w)\" is written with \"([^\"]*)\""
    (fn [world path name text]
      (write-file (worktree world name) path (str (unescape text) "\n"))
      world)]

   [#"the worktree of task \"(\w)\" and the branch \"([^\"]+)\" changed the files \"([^\"]*)\" differently"
    (fn [world name branch files]
      (let [dir (worktree world name)
            repo (:repo world)
            paths (str/split files #" ")]
        (doseq [path paths]
          (write-file dir path "ours\n")
          (must-in world dir "git" "add" path)
          (require-ok (commit world name (str "Ours " path) {}) "git commit"))
        (when-not (zero? (:exit (run-in world repo ["git" "rev-parse" "--verify" "--quiet" (str "refs/heads/" branch)])))
          (git world "branch" branch))
        (git world "checkout" "-q" branch)
        (doseq [path paths]
          (write-file repo path "theirs\n")
          (git world "add" path)
          (git world "commit" "-q" "-m" (str "Theirs " path)))
        (git world "checkout" "-q" "main")
        world))]

   [#"the operator resolved the conflicts and (?:staged them|committed the merge with (?:the message \"(.*)\"|(git's prepared message)))"
    (fn [world message prepared]
      (doseq [path (lines (git world "diff" "--name-only" "--diff-filter=U"))]
        (write-file (:repo world) path "resolved\n")
        (git world "add" path))
      (cond message (git world "commit" "-q" "-m" (unescape message))
            prepared (git world "commit" "-q" "--no-edit"))
      world)]

   [#"the HEAD of the main checkout is named (M\d+)"
    (fn [world head-name]
      (assoc-in world [:names head-name] (git world "rev-parse" "--short" "HEAD")))]

   [#"the tip of the branch of task \"(\w)\" is named (T\d+)"
    (fn [world name tip-name]
      (assoc-in world [:names tip-name] (git world "rev-parse" "--short" (branch-ref world name))))]

   [#"the command environment sets \"([^\"]+)\" to \"([^\"]*)\""
    (fn [world name value] (assoc-in world [:env name] value))]

   ;; Given (I ran) and When (I run)
   [#"I (run|ran) \"([^\"]*)\"(?: in the worktree of task \"(\w)\")?(?: with the text \"(.*)\")?"
    (fn [world tense command name text]
      (let [dir (if name (worktree world name) (:repo world))
            cmd (cond-> (str/split (fill world command) #" ") text (conj (unescape text)))]
        (if (= "ran" tense)
          (do (run-in world dir cmd) world)
          (when-step world #(run-in % dir cmd)))))]

   [#"I run sf new with the title \"([^\"]*)\"(?:, the request \"([^\"]*)\" and the route \"([^\"]*)\")?"
    (fn [world title request route]
      (let [world (when-step world #(run-in % (:repo %) (concat ["sf" "new" title]
                                                                 (when request ["-m" (unescape request) "--route" route]))))]
        (assoc world :new (str/trim (:out world)))))]

   [#"I commit \"([^\"]*)\" in the (?:worktree of task \"(\w)\"|main checkout)(?: with SWARMFORGE_ROLE \"([^\"]*)\")?"
    (fn [world message name role]
      (when-step world #(if name
                          (commit % name message (if role {"SWARMFORGE_ROLE" role} {}))
                          (run-in % (:repo %) ["git" "commit" "-q" "--allow-empty" "-m" (unescape message)]))))]

   ;; Then: status and output
   [#"the exit status is (\d+)"
    (fn [world status]
      (check (= (parse-long status) (:exit world))
             (str "exit status " (:exit world) ", stderr: " (str/trim (str (:err world)))))
      world)]

   [#"the output (is|is not) \"(.*)\""
    (fn [world how text]
      (let [actual (str/trim-newline (:out world))
            want (expected world text)]
        (check (= (= "is" how) (= want actual)) (str "output was " (pr-str actual)))
        world))]

   [#"the output matches \"(.*)\""
    (fn [world pattern]
      (let [actual (str/trim-newline (:out world))]
        (check (re-find (re-pattern (fill world pattern)) actual) (str "output was " (pr-str actual)))
        world))]

   [#"the (output|error output) has the line \"(.*)\""
    (fn [world stream text]
      (let [actual (lines ((if (= "output" stream) :out :err) world))]
        (check (some #{(expected world text)} actual) (str stream " was " (pr-str actual)))
        world))]

   [#"the output has the lines \"(.*?)\"(?: before the line \"(.*)\")?"
    (fn [world text before]
      (let [actual (vec (lines (:out world)))
            want (lines (expected world text))
            at (index-of-lines actual want)
            limit (when before (first (keep-indexed #(when (= before %2) %1) actual)))]
        (check (and at (or (nil? before) (and limit (<= (+ at (count want)) limit))))
               (str "output was " (pr-str actual)))
        world))]

   [#"the output after the line \"(.*)\" is \"(.*)\""
    (fn [world line text]
      (let [actual (lines (:out world))
            after (str/join "\n" (rest (drop-while #(not= line %) actual)))]
        (check (= (expected world text) after) (str "output after " line " was " (pr-str after)))
        world))]

   [#"the output ends with the lines \"(.*)\""
    (fn [world text]
      (let [actual (lines (:out world))
            want (lines (expected world text))]
        (check (= want (take-last (count want) actual))
               (str "output ended with " (pr-str (vec (take-last (count want) actual)))))
        world))]

   [#"the output has no line containing \"(.*)\""
    (fn [world text]
      (let [found (filter #(str/includes? % (fill world text)) (lines (:out world)))]
        (check (empty? found) (str "found " (pr-str (vec found))))
        world))]

   ;; Then: branches and commits
   [#"the branch \"([^\"]+)\" (exists|does not exist)"
    (fn [world branch how]
      (let [found (zero? (:exit (run-in world (:repo world) ["git" "rev-parse" "--verify" "--quiet" (str "refs/heads/" (fill world branch))])))]
        (check (= found (= "exists" how)) (str "branch " branch (if found " exists" " is missing")))
        world))]

   [#"the branch of task \"(\w)\" did not move"
    (fn [world name]
      (let [ref (branch-ref world name)]
        (check (= (get-in world [:before ref]) (get (tips world) ref)) "the branch moved"))
      world)]

   [(step "the (parent of the )?tip commit of {task} has the message \"(.*)\"")
    (fn [world parent name text]
      (let [actual (commit-message world (str (branch-ref world name) (when parent "^")))]
        (check (= (expected world text) actual) (str "message was " (pr-str actual)))
        world))]

   [(step "the tip commit of {task} has the author \"(.*)\"")
    (fn [world name text]
      (let [actual (git world "log" "-1" "--format=%an <%ae>" (branch-ref world name))]
        (check (= text actual) (str "author was " actual))
        world))]

   [(step "the parent of the tip commit of {task} is (C\\d+|the HEAD of the main checkout)")
    (fn [world name commit]
      (let [want (git world "rev-parse" (if (= "the HEAD of the main checkout" commit) "HEAD" (fill world commit)))]
        (check (= want (git world "rev-parse" (str (branch-ref world name) "^"))) "another parent")
        world))]

   [(step "the tip commit of {task} changes no file")
    (fn [world name]
      (let [actual (diff-tree world name)]
        (check (str/blank? actual) (str "it changes " (pr-str actual)))
        world))]

   [(step "the tip commit of {task} (adds only the file|changes the files) \"(.*)\"")
    (fn [world name how text]
      (let [actual (str/replace (diff-tree world name "--name-status" "--no-renames") "\t" " ")
            want (if (= "adds only the file" how) (str "A " (expected world text)) (expected world text))]
        (check (= want actual) (str "it changes " (pr-str actual)))
        world))]

   [(step "the file \"([^\"]+)\" on the branch of {task} (is|has the line) \"(.*)\"")
    (fn [world path name how text]
      (let [{:keys [exit out]} (show-file world name (fill world path))
            want (expected world text)]
        (check (zero? exit) (str path " is missing"))
        (check (if (= "is" how)
                 (re-matches (date-pattern want) (str/trim-newline out))
                 (some #{want} (lines out)))
               (str path " was " (pr-str out)))
        world))]

   [(step "the file \"([^\"]+)\" does not exist on the branch of {task}")
    (fn [world path name]
      (check (not (zero? (:exit (show-file world name (fill world path))))) (str path " exists"))
      world)]

   [#"the file \"([^\"]+)\" in the worktree of task \"(\w)\" still holds \"(.*)\""
    (fn [world path name text]
      (let [actual (slurp (str (fs/path (worktree world name) path)))]
        (check (= (str (unescape text) "\n") actual) (str path " holds " (pr-str actual)))
        world))]

   [#"the main checkout has no file \"([^\"]+)\" and no uncommitted change"
    (fn [world path]
      (check (not (fs/exists? (fs/path (:repo world) (fill world path)))) (str path " exists"))
      (check (str/blank? (git world "status" "--porcelain")) "the main checkout has changes")
      world)]

   [#"the main checkout has the file \"([^\"]+)\" and no file \"([^\"]+)\""
    (fn [world present absent]
      (check (fs/exists? (fs/path (:repo world) (fill world present))) (str present " is missing"))
      (check (not (fs/exists? (fs/path (:repo world) (fill world absent)))) (str absent " exists"))
      world)]

   [#"the HEAD of the main checkout is a merge whose second parent is (T\d+)"
    (fn [world tip-name]
      (check (= (git world "rev-parse" (fill world tip-name)) (git world "rev-parse" "HEAD^2")) "another second parent")
      world)]

   [#"the HEAD of the main checkout is (M\d+)"
    (fn [world head-name]
      (check (= (git world "rev-parse" (fill world head-name)) (git world "rev-parse" "HEAD")) "HEAD moved")
      world)]

   [#"the HEAD of the main checkout has the parents and the tree of (M\d+)"
    (fn [world head-name]
      (let [shape #(git world "log" "-1" "--format=%P %T" %)]
        (check (= (shape (fill world head-name)) (shape "HEAD")) (str "HEAD is " (shape "HEAD")))
        world))]

   [#"the main checkout is on the branch \"([^\"]+)\" merging (T\d+) with (?:the conflicted files \"(.*)\"|no conflicted file)"
    (fn [world branch tip-name files]
      (check (= branch (git world "branch" "--show-current")) "on another branch")
      (check (= (git world "rev-parse" (fill world tip-name)) (git world "rev-parse" "MERGE_HEAD")) "merging another commit")
      (let [actual (git world "diff" "--name-only" "--diff-filter=U")]
        (check (= (if files (str/replace files " " "\n") "") actual) (str "conflicted: " (pr-str actual))))
      world)]

   [#"the HEAD of the main checkout has the message \"(.*)\""
    (fn [world text]
      (let [actual (commit-message world "HEAD")]
        (check (= (expected world text) actual) (str "message was " (pr-str actual)))
        world))]

   [#"the worktree of task \"(\w)\" has no uncommitted change"
    (fn [world name]
      (let [actual (must-in world (worktree world name) "git" "status" "--porcelain")]
        (check (str/blank? actual) (str "changes: " (pr-str actual)))
        world))]

   [#"the worktree of task \"(\w)\" (exists|does not exist)"
    (fn [world name how]
      (let [found (fs/directory? (worktree world name))]
        (check (= found (= "exists" how)) (str "the worktree " (if found "exists" "is missing")))
        world))]

   [#"the worktree of task \"(\w)\" still has the staged new file \"([^\"]+)\" and the unstaged change \"([^\"]+)\""
    (fn [world name staged unstaged]
      (let [dir (worktree world name)
            status (set (str/split-lines (:out (run-in world dir ["git" "status" "--porcelain"]))))]
        (check (= #{(str "A  " staged) (str " M " unstaged)} status) (str "status was " (pr-str status)))
        (check (= "s\n" (slurp (str (fs/path dir staged)))) (str staged " changed"))
        (check (= "1\n2\n" (slurp (str (fs/path dir unstaged)))) (str unstaged " changed"))
        world))]])
