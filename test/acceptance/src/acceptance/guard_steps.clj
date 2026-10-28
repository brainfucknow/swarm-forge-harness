(ns acceptance.guard-steps
  "Step handlers for test/features/manifest_guard: this checkout's
  test/no_manifests.sh against a scratch git repository."
  (:require [acceptance.support :as support :refer [run must unescape check]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(defn new-world [] {})

(defn- git-repository [ignored-line]
  (let [repo (str (fs/create-temp-dir {:dir @support/scratch :prefix "guard-"}))]
    (must repo "git" "init" "-q" "-b" "main" ".")
    (must repo "git" "config" "user.name" "tester")
    (must repo "git" "config" "user.email" "tester@example.com")
    (spit (str (fs/path repo ".gitignore")) (str ignored-line "\n"))
    (must repo "git" "add" ".gitignore")
    (must repo "git" "commit" "-q" "-m" "seed")
    repo))

(defn- strip-last-newline [text]
  (str/replace (str text) #"\n\z" ""))

(def handlers
  [[#"a git repository whose \.gitignore has the line \"(.*)\""
    (fn [world line] (assoc world :repo (git-repository line)))]

   [#"the file \"([^\"]+)\" holds \"(.*)\"(?: and is (committed|staged))?"
    (fn [world path content state]
      (let [repo (:repo world)
            file (fs/path repo path)]
        (fs/create-dirs (fs/parent file))
        (spit (str file) (str (unescape content) "\n"))
        (when state (must repo "git" "add" "-f" path))
        (when (= "committed" state) (must repo "git" "commit" "-q" "-m" path))
        world))]

   [#"I run the manifest guard(?: in the directory \"([^\"]+)\")?"
    (fn [world dir]
      (let [script (str (fs/path support/checkout "test" "no_manifests.sh"))]
        (merge world (select-keys (run (fs/path (:repo world) (or dir ".")) ["bash" script])
                                  [:exit :out :err]))))]

   [#"the exit status is (\d+)"
    (fn [world status]
      (check (= (parse-long status) (:exit world))
             (str "exit status " (:exit world) ", stderr: " (str/trim (str (:err world)))))
      world)]

   [#"the (output|error output) is \"(.*)\""
    (fn [world which expected]
      (let [actual (strip-last-newline (if (= "output" which) (:out world) (:err world)))]
        (check (= (unescape expected) actual) (str which " was " (pr-str actual)))
        world))]

   [#"there is no output"
    (fn [world]
      (check (= "" (str (:out world) (:err world)))
             (str "stdout " (pr-str (:out world)) ", stderr " (pr-str (:err world))))
      world)]])
