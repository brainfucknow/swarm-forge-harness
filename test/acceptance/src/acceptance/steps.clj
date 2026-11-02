(ns acceptance.steps
  "Step handlers for test/features/lore_trailers. The scratch repositories come
  from acceptance.support."
  (:require [acceptance.support :as support :refer [run must unescape check require-ok]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- sf [world] (support/sf-path (:repo world)))

(defn new-world [] {:names {}})

(defn- fill-names
  "text with ID and the commit names (C1, L1, ...) replaced by their values."
  [world text]
  (str/replace text #"\b(ID|[CL]\d+)\b"
               (fn [[whole name]]
                 (or (if (= "ID" name) (:id world) (get-in world [:names name]))
                     (throw (ex-info (str "no value for " whole) {}))))))

(defn- place-dir [world place]
  (case place
    "main checkout" (:repo world)
    "task worktree" (:worktree world)
    "second task worktree" (:worktree2 world)
    "directory outside git" (:outside world)
    "another git repository" (:other world)))

(defn- upstream-env
  "A git url.<dir>.insteadOf rule, given in the environment, that makes the
  world's upstream stand in for its GitHub URL; an unreachable upstream points
  at a path that does not exist."
  [{:keys [upstream]}]
  (when upstream
    {"GIT_CONFIG_COUNT" "1"
     "GIT_CONFIG_KEY_0" (str "url." (if (:reachable upstream) (:dir upstream) (str (:dir upstream) "-missing")) ".insteadOf")
     "GIT_CONFIG_VALUE_0" (str "https://github.com/unclebob/" (:name upstream) ".git")}))

(defn- tool-env
  "SWARMFORGE_TOOL_SRC when the world has a tool source override, else
  GIT_TERMINAL_PROMPT=0 so a clone that should not happen fails fast; and the
  upstream's insteadOf rule."
  [world]
  (merge (if-let [src (:src world)] {"SWARMFORGE_TOOL_SRC" src} {"GIT_TERMINAL_PROMPT" "0"})
         (upstream-env world)))

(defn- run-command
  "Runs command, split on spaces, from the place; sf is the scratch project's sf."
  [world command place]
  (let [[program & args] (str/split command #" ")]
    (select-keys (run (place-dir world place) (cons (if (= "sf" program) (sf world) program) args) (tool-env world))
                 [:exit :out :err])))

(defn- commit
  "Runs git commit --allow-empty -m message in dir; flags come first."
  [dir message env & flags]
  (run dir (concat ["git" "commit" "-q" "--allow-empty"] flags ["-m" (unescape message)]) env))

(defn- lore-id [dir rev]
  (str/trim (must dir "git" "log" "-1" "--format=%(trailers:key=Lore-id,valueonly)" rev)))

(defn- name-commit [world name]
  (let [dir (:worktree world)
        lore (lore-id dir "HEAD")]
    (cond-> (assoc-in world [:names name] (must dir "git" "rev-parse" "--short" "HEAD"))
      (seq lore) (assoc-in [:names (str/replace name #"^C" "L")] lore))))

(defn- output-lines [world]
  (str/split-lines (:out world)))

(def ^:private task-handlers
  [[#"a project with swarmforge initialized"
    (fn [world]
      (assoc world :repo (support/project true)))]

   [#"the main checkout has the commit \"(.*)\""
    (fn [world message]
      (require-ok (commit (:repo world) message {}) "git commit")
      world)]

   [#"a task at stage \"([^\"]+)\" started with sf start"
    (fn [world stage]
      (let [id (must (:repo world) (sf world) "new" "t" "-m" "t" "--route" stage)]
        (must (:repo world) (sf world) "start" id)
        (assoc world :id id :worktree (str (fs/path (:repo world) ".worktrees" id)))))]

   [#"I commit \"(.*)\" in the (task worktree|main checkout)(?: with SWARMFORGE_ROLE \"([^\"]+)\")?( twice)?"
    (fn [world message place role twice]
      (let [dir (place-dir world place)
            env (if role {"SWARMFORGE_ROLE" role} {})
            results (doall (repeatedly (if twice 2 1) #(commit dir message env)))]
        (assoc world :dir dir :exit (some #(when-not (zero? (:exit %)) (:exit %)) results)
               :err (str/join (map :err results)))))]

   [#"role \"([^\"]+)\" committed \"(.*)\" in the task worktree as (C\d+)"
    (fn [world role message name]
      (require-ok (commit (:worktree world) message {"SWARMFORGE_ROLE" role}) "git commit")
      (name-commit world name))]

   [#"\"(.*)\" was committed in the task worktree with the hook skipped as (C\d+)"
    (fn [world message name]
      (require-ok (commit (:worktree world) message {} "--no-verify") "git commit")
      (name-commit world name))]

   [#"the task was finished with sf finish"
    (fn [world]
      (doseq [command ["review" "approve" "finish"]]
        (must (:repo world) (sf world) command (:id world)))
      world)]

   [#"I run \"(.*)\" in the (main checkout|task worktree)"
    (fn [world command place]
      (merge world (run-command world (fill-names world command) place)))]

   [#"the exit status is (\d+)"
    (fn [world status]
      (check (= (parse-long status) (or (:exit world) 0))
             (str "exit status " (or (:exit world) 0) ", stderr: " (str/trim (str (:err world)))))
      world)]

   [#"the commit message is \"(.*)\""
    (fn [world expected]
      (let [actual (str/trim-newline (must (:dir world) "git" "log" "-1" "--format=%B"))
            pattern (re-pattern (str/join "(\\S+)" (map #(java.util.regex.Pattern/quote %)
                                                        (str/split (unescape expected) #"LID" -1))))
            match (re-matches pattern actual)]
        (check match (str "commit message was " (pr-str actual)))
        (cond-> world (vector? match) (assoc :lid (second match)))))]

   [#"LID is 8 lowercase hexadecimal digits"
    (fn [world]
      (check (re-matches #"[0-9a-f]{8}" (str (:lid world))) (str "LID was " (pr-str (:lid world))))
      world)]

   [#"git reads the trailers of the commit as \"(.*)\""
    (fn [world expected]
      (let [actual (str/replace (must (:dir world) "git" "log" "-1" "--format=%(trailers:only,unfold)") #"\s+\z" "")]
        (check (= (unescape expected) actual) (str "git read " (pr-str actual)))
        world))]

   [#"the two commits have different Lore-ids"
    (fn [world]
      (let [ids [(lore-id (:dir world) "HEAD") (lore-id (:dir world) "HEAD~1")]]
        (check (and (every? seq ids) (apply not= ids)) (str "Lore-ids were " (pr-str ids)))
        world))]

   [#"the output ends with the lines \"(.*)\""
    (fn [world expected]
      (let [expected (str/split-lines (fill-names world (unescape expected)))
            actual (output-lines world)
            lore? #(str/starts-with? % "LORE:")]
        (check (= expected (take-last (count expected) actual))
               (str "output ended with " (pr-str (vec (take-last (count expected) actual)))))
        (check (= (count (filter lore? expected)) (count (filter lore? actual)))
               (str "LORE: lines were " (pr-str (filterv lore? actual))))
        world))]

   [#"the output has no line (containing|starting with) \"(.*)\""
    (fn [world how text]
      (let [found (filter #(if (= how "containing") (str/includes? % text) (str/starts-with? % text))
                          (output-lines world))]
        (check (empty? found) (str "found " (pr-str (vec found))))
        world))]])

;; sf tool (test/features/sf_tool)

(defn- fill-paths
  "text with MAIN, SRC, CFG and CLONE replaced by the scratch project's paths,
  and PIN and NEXT by the upstream's commits of those names."
  [world text]
  (str/replace text #"\b(MAIN|SRC|CFG|CLONE|PIN|NEXT)\b"
               (fn [[_ name]]
                 (case name
                   "MAIN" (:repo world)
                   "SRC" (or (:src world) (:clone world) (throw (ex-info "no tool source" {})))
                   "CFG" (str (fs/path (:repo world) ".swarmforge" "tools" "crap4clj-coverage" "bb.edn"))
                   "CLONE" (or (:clone world) (throw (ex-info "no upstream" {})))
                   (or (get-in world [:upstream :commits name]) (throw (ex-info (str "no commit " name) {})))))))

(defn- scratch-dir [prefix]
  (str (fs/create-temp-dir {:dir @support/scratch :prefix prefix})))

(defn- write-script [file text]
  (fs/create-dirs (fs/parent file))
  (spit (str file) (str "#!/usr/bin/env bash\n" text "\n"))
  (fs/set-posix-file-permissions file "rwxr-xr-x"))

(defn- other-repository
  "A git repository of its own whose swarmforge/bin/sf-cs prints \"other\"."
  []
  (let [dir (scratch-dir "other-")]
    (must dir "git" "init" "--quiet")
    (write-script (fs/path dir "swarmforge" "bin" "sf-cs") "echo other")
    dir))

(defn- tool-source [world bb-edn]
  (let [dir (scratch-dir "tool-src-")]
    (when bb-edn (spit (str (fs/path dir "bb.edn")) bb-edn))
    (assoc world :src dir)))

(defn- words [text]
  (remove str/blank? (str/split text #" ")))

(defn- wrapper-path [world tool]
  (fs/path (:repo world) ".swarmforge" "bin" tool))

(defn- wrapper-env
  "PATH for a wrapper run: the fake bb, a fake clj when present, and no real clj."
  [world]
  (let [system (remove #(fs/executable? (fs/path % "clj"))
                       (str/split (System/getenv "PATH") #":"))]
    {"PATH" (str/join ":" (concat [(:fakebin world)] (when (:clj world) [(:cljbin world)]) system))}))

(defn- run-wrapper [world tool dir args extra-env]
  (when-let [ran (:bb-ran world)] (fs/delete-if-exists ran))
  (select-keys (run dir (cons (str (wrapper-path world tool)) args) (merge (wrapper-env world) extra-env))
               [:exit :out :err]))

(defn- add-worktree [world n]
  (let [dir (str (fs/path (:repo world) ".worktrees" (str "t" n)))]
    (must (:repo world) "git" "worktree" "add" "-q" dir "-b" (str "swarm/t" n))
    dir))

(defn- commit-all [dir message]
  (require-ok (run dir ["git" "add" "-A"]) "git add")
  (require-ok (run dir ["git" "commit" "-q" "-m" message] {"SWARMFORGE_ROLE" "tester"}) "git commit"))

(def ^:private stale-wrapper "echo stale")

(defn- tree-state
  "Every path under dir with its permissions, and each file's content; nil
  when dir does not exist."
  [dir]
  (when (fs/exists? dir {:nofollow-links true})
    (into {} (for [path (fs/glob dir "**" {:hidden true})]
               [(str (fs/relativize dir path))
                [(fs/posix-file-permissions path) (when (fs/regular-file? path) (seq (fs/read-all-bytes path)))]]))))

(defn- git-state
  "The main checkout's HEAD commit, branch, status with untracked and ignored
  files, and FETCH_HEAD."
  [world]
  (let [repo (:repo world)
        fetch-head (fs/path repo ".git" "FETCH_HEAD")]
    [(must repo "git" "rev-parse" "HEAD")
     (must repo "git" "branch" "--show-current")
     (must repo "git" "status" "--porcelain" "--untracked-files=all" "--ignored")
     (when (fs/exists? fetch-head) (slurp (str fetch-head)))]))

(defn- new-upstream
  "A repository with one commit per name, each with a bb.edn defining the task
  repo and a file version holding the name."
  [repo names]
  (let [dir (scratch-dir "upstream-")]
    (must dir "git" "init" "-q")
    {:name repo :dir dir :reachable true
     :commits (into {} (for [name names]
                         (do (spit (str (fs/path dir "bb.edn")) (str "{:tasks {" repo " {}}}"))
                             (spit (str (fs/path dir "version")) name)
                             (must dir "git" "add" "-A")
                             (must dir "git" "commit" "-q" "-m" name)
                             [name (must dir "git" "rev-parse" "HEAD")])))}))

(defn- earlier-clone
  "Makes the world's clone a clone of its upstream in the state the step names."
  [world state]
  (let [{:keys [dir commits]} (:upstream world)
        clone (:clone world)
        at-pin (fn []
                 (must @support/scratch "git" "clone" "-q" dir clone)
                 (must clone "git" "checkout" "-q" "--detach" (commits "PIN")))]
    (fs/create-dirs (fs/parent clone))
    (case state
      "HEAD at NEXT" (do (must @support/scratch "git" "clone" "-q" dir clone)
                         (must clone "git" "checkout" "-q" "--detach" (commits "NEXT")))
      "a local commit on top of PIN" (do (at-pin)
                                         (spit (str (fs/path clone "version")) "local")
                                         (must clone "git" "commit" "-q" "-am" "local"))
      "an edited tracked file" (do (at-pin) (spit (str (fs/path clone "version")) "edited"))
      "an untracked file" (do (at-pin)
                              (fs/create-dirs (fs/path clone "src"))
                              (spit (str (fs/path clone "src" "extra.clj")) "(ns extra)\n"))
      "only NEXT and an unreachable origin remote"
      (do (must @support/scratch "git" "clone" "-q" "--depth" "1" (str "file://" dir) clone)
          (must clone "git" "remote" "set-url" "origin" (str dir "-missing"))))
    world))

(defn- set-ref
  "The tools.conf text with the ref column of the row of tool replaced."
  [text tool ref]
  (str/join (for [line (str/split text #"(?<=\n)")]
              (let [fields (str/split (str/trim line) #"\s+")]
                (if (= tool (first fields))
                  (str (str/join " " (assoc fields 3 ref)) "\n")
                  line)))))

(defn- fake-bb-args [world]
  (check (some? (:out world)) "no wrapper ran")
  (check (zero? (:exit world)) (str "wrapper exited " (:exit world) ": " (str/trim (str (:err world)))))
  (str/split-lines (:out world)))

(def ^:private tool-handlers
  [[#"a fake bb on PATH"
    (fn [world]
      (let [fakebin (scratch-dir "fakebin-")
            cljbin (scratch-dir "cljbin-")
            ran (str (fs/path (scratch-dir "bb-ran-") "dir"))]
        (write-script (fs/path fakebin "bb") (str "pwd -P > '" ran "'\nprintf '%s\\n' \"$@\""))
        (write-script (fs/path cljbin "clj") "exit 0")
        (assoc world :fakebin fakebin :cljbin cljbin :bb-ran ran)))]

   [#"a tool source whose bb.edn defines the tasks \"([^\"]*)\""
    (fn [world tasks]
      (tool-source world (str "{:tasks {" (str/join " " (map #(str % " {}") (words tasks))) "}}")))]

   [#"the tool source has the namespace \"([^\"]+)\""
    (fn [world namespace]
      (let [file (fs/path (:src world) "src" (str (-> namespace (str/replace "." "/") (str/replace "-" "_")) ".clj"))]
        (fs/create-dirs (fs/parent file))
        (spit (str file) (str "(ns " namespace ")\n"))
        world))]

   [#"a tool source whose bb.edn is \"([^\"]*)\""
    (fn [world text] (tool-source world text))]

   [#"a tool source without bb.edn"
    (fn [world] (tool-source world nil))]

   [#"no tool source override"
    (fn [world] (dissoc world :src))]

   [#"the directory \"([^\"]+)\" exists without bb.edn"
    (fn [world dir]
      (let [dir (fill-paths world dir)]
        (fs/create-dirs dir)
        (spit (str (fs/path dir "README.md")) "not a Babashka tool\n")
        world))]

   [#"an earlier install left a wrapper for \"([^\"]+)\""
    (fn [world tool]
      (write-script (wrapper-path world tool) stale-wrapper)
      world)]

   [#"the wrapper for \"([^\"]+)\" is unchanged"
    (fn [world tool]
      (let [path (wrapper-path world tool)]
        (check (fs/executable? path) (str path " is not executable"))
        (check (= (str "#!/usr/bin/env bash\n" stale-wrapper "\n") (slurp (str path))) (str path " changed"))
        world))]

   [#"a snapshot of \"([^\"]+)\""
    (fn [world dir]
      (assoc-in world [:snapshots dir] (tree-state (fill-paths world dir))))]

   [#"\"([^\"]+)\" matches the snapshot"
    (fn [world dir]
      (check (contains? (:snapshots world) dir) (str "no snapshot of " dir))
      (check (= (get-in world [:snapshots dir]) (tree-state (fill-paths world dir))) (str dir " changed"))
      world)]

   [#"a snapshot of the main checkout's git state"
    (fn [world] (assoc world :git-state (git-state world)))]

   [#"the main checkout's git state matches the snapshot"
    (fn [world]
      (check (= (:git-state world) (git-state world)) (str "git state was " (pr-str (git-state world))))
      world)]

   [#"\"(.*)\" ran in the (main checkout|task worktree)"
    (fn [world command place]
      (require-ok (run-command world command place) command)
      world)]

   [#"the upstream \"([^\"]+)\" has the commits \"([^\"]+)\""
    (fn [world repo names]
      (assoc world :upstream (new-upstream repo (words names))
             :clone (str (fs/path (:repo world) ".swarmforge" "tools" repo))))]

   [#"the upstream \"([^\"]+)\" is (reachable|unreachable)"
    (fn [world _ how]
      (assoc-in world [:upstream :reachable] (= "reachable" how)))]

   [#"the main checkout's tools.conf pins \"([^\"]+)\" at \"([^\"]+)\""
    (fn [world tool ref]
      (let [file (str (fs/path (:repo world) "swarmforge" "tools.conf"))]
        (spit file (set-ref (slurp file) tool (fill-paths world ref)))
        world))]

   [#"an earlier clone with (.+)"
    (fn [world state] (earlier-clone world state))]

   [#"the clone has a post-checkout hook that writes \"([^\"]+)\""
    (fn [world file]
      (write-script (fs/path (:clone world) ".git" "hooks" "post-checkout") (str "touch '" (fill-paths world file) "'"))
      world)]

   [#"the clone is clean at \"([^\"]+)\""
    (fn [world ref]
      (let [clone (:clone world)]
        (check (= (fill-paths world ref) (must clone "git" "rev-parse" "HEAD")) (str "HEAD is " (must clone "git" "log" "-1" "--format=%s")))
        (check (not (zero? (:exit (run clone ["git" "symbolic-ref" "-q" "HEAD"])))) "HEAD is not detached")
        (check (= "" (must clone "git" "status" "--porcelain" "--untracked-files=all")) "the clone has changes")
        world))]

   [#"there is no file \"([^\"]+)\""
    (fn [world file]
      (check (not (fs/exists? (fill-paths world file) {:nofollow-links true})) (str (fill-paths world file) " exists"))
      world)]

   [#"the output has a line with the words \"(.*)\""
    (fn [world expected]
      (let [expected (words (fill-paths world expected))]
        (check (some #(= expected (remove str/blank? (str/split % #"\s+"))) (output-lines world))
               (str "stdout was " (pr-str (:out world))))
        world))]

   [#"\"(.*)\" in the (task worktree|main checkout) prints \"(.*)\""
    (fn [world command place expected]
      (let [{:keys [out]} (run-command world command place)]
        (check (some #{(fill-paths world expected)} (str/split-lines out)) (str "stdout was " (pr-str out)))
        world))]

   [#"a task worktree"
    (fn [world] (assoc world :worktree (add-worktree world 1)))]

   [#"a second task worktree without \"([^\"]+)\""
    (fn [world file]
      (let [dir (add-worktree world 2)]
        (fs/delete (fs/path dir file))
        (commit-all dir (str "Delete " file))
        (assoc world :worktree2 dir)))]

   [#"the (task worktree|main checkout)'s tools.conf adds the line \"(.*)\""
    (fn [world place line]
      (let [dir (place-dir world place)]
        (spit (str (fs/path dir "swarmforge" "tools.conf")) (str line "\n") :append true)
        (when (= "task worktree" place) (commit-all dir "Add a tool"))
        world))]

   [#"the (task worktree|main checkout)'s \"([^\"]+)\" prints \"(.*)\""
    (fn [world place file text]
      (write-script (fs/path (place-dir world place) file) (str "echo " text))
      world)]

   [#"the tool \"([^\"]+)\" is listed: (yes|no)"
    (fn [world tool listed]
      (let [found (some #(re-find (re-pattern (str "^" (java.util.regex.Pattern/quote tool) " +")) %) (output-lines world))]
        (check (= (= "yes" listed) (boolean found)) (str "list was " (pr-str (:out world))))
        world))]

   [#"the output has the line \"(.*)\""
    (fn [world expected]
      (check (some #{(fill-paths world expected)} (output-lines world)) (str "stdout was " (pr-str (:out world))))
      world)]

   [#"the output is \"(.*)\""
    (fn [world expected]
      (check (= (str expected "\n") (:out world)) (str "stdout was " (pr-str (:out world))))
      world)]

   [#"the output does not mention \"(.*)\""
    (fn [world text]
      (check (not (str/includes? (str (:out world) (:err world)) text)) (str "output mentions " (pr-str text)))
      world)]

   [#"the error output (contains|does not mention) \"(.*)\""
    (fn [world how text]
      (let [found (str/includes? (str (:err world)) (fill-paths world text))]
        (check (= found (= "contains" how)) (str "stderr was " (pr-str (:err world))))
        world))]

   [#"there is no wrapper for \"([^\"]+)\""
    (fn [world tool]
      (check (not (fs/exists? (wrapper-path world tool))) (str (wrapper-path world tool) " exists"))
      world)]

   [#"the file \"([^\"]+)\" exists"
    (fn [world file]
      (check (fs/regular-file? (fill-paths world file)) (str (fill-paths world file) " is not a file"))
      world)]

   [#"the file \"([^\"]+)\" (mentions|does not mention) \"(.*)\""
    (fn [world file how text]
      (check (= (= "mentions" how) (str/includes? (slurp (fill-paths world file)) text))
             (str file (if (= "mentions" how) " does not mention " " mentions ") (pr-str text)))
      world)]

   [#"clj is (present|absent) on PATH"
    (fn [world how] (assoc world :clj (= "present" how)))]

   [#"deps.edn is (present|absent)"
    (fn [world how]
      (when (= "present" how) (spit (str (fs/path (:repo world) "deps.edn")) "{}"))
      world)]

   [#"the directory \"([^\"]+)\" has (deps.edn|no deps.edn)"
    (fn [world dir deps]
      (let [file (fs/path (:repo world) dir "deps.edn")]
        (if (= "deps.edn" deps) (spit (str file) "{}") (fs/delete-if-exists file))
        world))]

   [#"the directories \"([^\"]*)\" exist"
    (fn [world dirs]
      (doseq [dir (words dirs)] (fs/create-dirs (fs/path (:repo world) dir)))
      world)]

   [#"I run the (\S+) wrapper with \"(.*)\""
    (fn [world tool args]
      (merge world (run-wrapper world tool (:repo world) (words (fill-paths world args)) {})))]

   [#"I run the sf-cs wrapper in the (task worktree|main checkout|second task worktree|directory outside git|another git repository)"
    (fn [world place]
      (let [outside? (= "directory outside git" place)
            world (cond-> world
                    outside? (assoc :outside (scratch-dir "outside-"))
                    (= "another git repository" place) (assoc :other (other-repository)))]
        (merge world (run-wrapper world "sf-cs" (place-dir world place) []
                                  (if outside? {"GIT_CEILING_DIRECTORIES" @support/scratch} {})))))]

   [#"bb received \"([^\"]*)\""
    (fn [world expected]
      (let [actual (str/join " " (fake-bb-args world))]
        (check (= (str/trimr (fill-paths world expected)) actual) (str "bb received " (pr-str actual)))
        world))]

   [#"bb ran in \"([^\"]+)\""
    (fn [world dir]
      (check (fs/exists? (:bb-ran world)) "bb did not run")
      (let [actual (str/trim (slurp (:bb-ran world)))]
        (check (= (str (fs/canonicalize (fill-paths world dir))) actual) (str "bb ran in " actual))
        world))]

   [#"bb did not run"
    (fn [world]
      (check (not (fs/exists? (:bb-ran world))) "bb ran")
      world)]

   [#"bb received \"([^\"]*)\" and the coverage command \"([^\"]*)\""
    (fn [world expected command]
      (let [expected (concat (words (fill-paths world expected)) ["--coverage-command" (fill-paths world command)])
            actual (fake-bb-args world)]
        (check (= expected actual) (str "bb received " (pr-str actual)))
        world))]])

(def handlers (into task-handlers tool-handlers))
