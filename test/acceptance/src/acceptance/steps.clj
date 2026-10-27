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

(defn- output-lines [world]
  (str/split-lines (:out world)))

(def handlers
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
