(ns acceptance.steps
  "Step handlers for test/features. Each execution works in a fresh copy of a
  scratch git repository that holds this checkout's swarmforge/ and has had
  sf init. The real sf, git, and git-bug run there. Scratch files go under
  ./tmp/acceptance/ and are removed when the process exits."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def ^:private checkout
  (str (fs/canonicalize (fs/path (fs/parent *file*) ".." ".." ".." ".."))))

(def ^:private scratch
  (delay (let [base (fs/path checkout "tmp" "acceptance")
               _ (fs/create-dirs base)
               dir (str (fs/create-temp-dir {:dir base :prefix "work-"}))]
           (.addShutdownHook (Runtime/getRuntime) (Thread. #(fs/delete-tree dir)))
           dir)))

(defn- clean-env
  "The process environment without SWARMFORGE_ and GIT_ variables, and with HOME
  in the scratch directory."
  []
  (-> (into {} (remove (fn [[k _]] (re-find #"^(SWARMFORGE_|GIT_)" k)) (System/getenv)))
      (assoc "HOME" (str (fs/path @scratch "home")))))

(defn- run
  "Runs cmd in dir and returns {:exit :out :err}. extra-env is added to the
  clean environment."
  ([dir cmd] (run dir cmd {}))
  ([dir cmd extra-env]
   (p/shell {:cmd (vec cmd) :dir (str dir) :out :string :err :string :continue true
             :env (merge (clean-env) extra-env)})))

(defn- must [dir & cmd]
  (let [{:keys [exit out err]} (run dir cmd)]
    (when-not (zero? exit)
      (throw (ex-info (str (str/join " " cmd) " exited " exit ": " (str/trim (str out err))) {})))
    (str/trim-newline out)))

(defn- sf [world] (str (fs/path (:repo world) "swarmforge" "bin" "sf")))

(def ^:private template
  (delay (let [home (fs/path @scratch "home")
               repo (str (fs/path @scratch "template"))]
           (fs/create-dirs home)
           (fs/create-dirs repo)
           (spit (str (fs/path home ".gitconfig"))
                 "[user]\n\tname = tester\n\temail = tester@example.com\n[init]\n\tdefaultBranch = main\n")
           (must repo "git" "init" "-q" ".")
           (fs/copy-tree (fs/path checkout "swarmforge") (fs/path repo "swarmforge"))
           (must repo "git" "add" "-A")
           (must repo "git" "commit" "-q" "-m" "seed")
           (must repo (str (fs/path repo "swarmforge" "bin" "sf")) "init")
           repo)))

(def ^:private copies (atom 0))

(defn new-world [] {:names {}})

(defn- unescape [text]
  (str/replace text "\\n" "\n"))

(defn- fill-names
  "text with ID and the commit names (C1, L1, ...) replaced by their values."
  [world text]
  (str/replace text #"\b(ID|[CL]\d+)\b"
               (fn [[whole name]]
                 (or (if (= "ID" name) (:id world) (get-in world [:names name]))
                     (throw (ex-info (str "no value for " whole) {}))))))

(defn- place-dir [world place]
  (if (= "main checkout" place) (:repo world) (:worktree world)))

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

(defn- require-ok [{:keys [exit err]} what]
  (when-not (zero? exit)
    (throw (ex-info (str what " exited " exit ": " (str/trim err)) {}))))

(defn- check [ok? message]
  (when-not ok? (throw (ex-info message {}))))

(defn- output-lines [world]
  (str/split-lines (:out world)))

(def handlers
  [[#"a project with swarmforge initialized"
    (fn [world]
      (let [repo (str (fs/path @scratch (str "run-" (swap! copies inc))))]
        (must @scratch "cp" "-a" @template repo)
        (assoc world :repo repo)))]

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

   [#"I run \"(.*)\" in the main checkout"
    (fn [world command]
      (let [[program & args] (str/split (fill-names world command) #" ")
            {:keys [exit out err]} (run (:repo world) (cons (if (= "sf" program) (sf world) program) args))]
        (assoc world :exit exit :out out :err err)))]

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

   [#"the output has no line (containing|starting with) \"(.*)\""
    (fn [world how text]
      (let [found (filter #(if (= how "containing") (str/includes? % text) (str/starts-with? % text))
                          (output-lines world))]
        (check (empty? found) (str "found " (pr-str (vec found))))
        world))]])
