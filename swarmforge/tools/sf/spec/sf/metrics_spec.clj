(ns sf.metrics-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [speclj.core :refer [around describe it should= should-not should-be-nil]]
            [sf.metrics :as metrics]
            [sf.repo-support :refer [*dir* git with-repo]]))

(defn- dir
  "A scratch checkout in the repository, ignored by git."
  [name]
  (spit (str (fs/path *dir* ".git" "info" "exclude")) "/a/\n/b/\n")
  (str (fs/path *dir* name)))

(defn- write [checkout path text]
  (let [file (fs/path checkout ".metrics" path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text)))

(defn- read-file [checkout path]
  (let [file (fs/path checkout ".metrics" path)]
    (when (fs/exists? file) (slurp (str file)))))

(defn- tip [] (git *dir* "rev-parse" "swarmforge/metrics"))

(defn- on-branch [path] (git *dir* "show" (str "swarmforge/metrics:" path)))

(defn- changes []
  (str/split-lines (git *dir* "diff-tree" "--root" "--no-commit-id" "-r" "--name-status" "swarmforge/metrics")))

(defn- branch? []
  (zero? (:exit (p/shell {:dir *dir* :continue true :out :string :err :string}
                                        "git" "rev-parse" "--verify" "--quiet" "refs/heads/swarmforge/metrics"))))

(defn- sh [opts & cmd]
  (str/trim-newline (:out (p/shell (merge {:dir *dir* :out :string} opts) (str/join " " cmd)))))

(defn- cache!
  "Commits path with text onto swarmforge/metrics, past sf."
  [path text]
  (let [env {:extra-env {"GIT_INDEX_FILE" (str (fs/path *dir* ".git" "spec-index"))}}
        parent (when (branch?) (tip))
        blob (sh {:in text} "git hash-object -w --stdin")]
    (when parent (sh env "git read-tree" parent))
    (sh env "git update-index --add --cacheinfo" (str "100644," blob "," path))
    (let [commit (sh {:in "x"} "git -c user.name=t -c user.email=t@x commit-tree" (sh env "git write-tree")
                     (if parent (str "-p " parent) ""))]
      (sh {} "git update-ref refs/heads/swarmforge/metrics" commit))))

(describe "saving manifests to swarmforge/metrics"
  (around [it] (with-repo it))

  (it "makes the branch with one parentless commit on the first save"
    (write (dir "a") "mutate/sf/task.edn" "v1\n")
    (metrics/save! "t-1" (dir "a"))
    (should= "v1" (on-branch "mutate/sf/task.edn"))
    (should= [(tip)] (str/split (git *dir* "rev-list" "--parents" "swarmforge/metrics") #" "))
    (should= "sf metrics: t-1\n\nSwarmForge-Task: t-1" (git *dir* "log" "-1" "--format=%B" "swarmforge/metrics"))
    (should= "" (git *dir* "status" "--porcelain")))

  (it "saves only new and changed manifests on top of the tip"
    (write (dir "a") "mutate/a.edn" "v1\n")
    (write (dir "a") "mutate/b.edn" "w1\n")
    (metrics/save! "t-1" (dir "a"))
    (let [first-tip (tip)]
      (write (dir "a") "mutate/b.edn" "w2\n")
      (write (dir "a") "gherkin/f/x.feature" "g1\n")
      (metrics/save! "t-1" (dir "a"))
      (should= first-tip (git *dir* "rev-parse" "swarmforge/metrics^"))
      (should= ["A\tgherkin/f/x.feature" "M\tmutate/b.edn"] (changes))))

  (it "does not move the branch when nothing changed"
    (write (dir "a") "mutate/a.edn" "v1\n")
    (metrics/save! "t-1" (dir "a"))
    (let [before (tip)]
      (metrics/save! "t-1" (dir "a"))
      (should= before (tip))))

  (it "saves only mutate/ and gherkin/, and makes no branch without them"
    (write (dir "a") "crap.edn" "c1\n")
    (write (dir "a") "other/x.edn" "o1\n")
    (metrics/save! "t-1" (dir "a"))
    (should-not (branch?))
    (metrics/save! "t-1" (dir "missing"))
    (should-not (branch?)))

  (it "keeps a manifest deleted from the checkout"
    (write (dir "a") "mutate/a.edn" "v1\n")
    (metrics/save! "t-1" (dir "a"))
    (write (dir "b") "mutate/b.edn" "w1\n")
    (metrics/save! "t-2" (dir "b"))
    (should= ["A\tmutate/b.edn"] (changes))
    (should= "v1" (on-branch "mutate/a.edn"))))

(describe "loading manifests from swarmforge/metrics"
  (around [it] (with-repo it))

  (it "copies nothing without the branch"
    (metrics/load! (dir "b"))
    (should-not (fs/exists? (dir "b"))))

  (it "copies the cached manifests the checkout lacks and keeps the ones it has"
    (write (dir "a") "mutate/a.edn" "cached a\n")
    (write (dir "a") "mutate/b.edn" "cached b\n")
    (write (dir "a") "gherkin/f/x.feature" "g1\n")
    (metrics/save! "t-1" (dir "a"))
    (write (dir "b") "mutate/a.edn" "local a\n")
    (metrics/load! (dir "b"))
    (should= "local a\n" (read-file (dir "b") "mutate/a.edn"))
    (should= "cached b\n" (read-file (dir "b") "mutate/b.edn"))
    (should= "g1\n" (read-file (dir "b") "gherkin/f/x.feature")))

  (it "copies a manifest whose path git would quote, and does not save it again"
    (write (dir "a") "mutate/é \"x\".edn" "cached\n")
    (metrics/save! "t-1" (dir "a"))
    (let [tip (git *dir* "rev-parse" "refs/heads/swarmforge/metrics")]
      (metrics/save! "t-2" (dir "a"))
      (should= tip (git *dir* "rev-parse" "refs/heads/swarmforge/metrics")))
    (metrics/load! (dir "b"))
    (should= "cached\n" (read-file (dir "b") "mutate/é \"x\".edn")))

  (it "copies only files under mutate/ and gherkin/"
    (cache! "crap.edn" "c1\n")
    (cache! "mutate/a.edn" "v1\n")
    (metrics/load! (dir "b"))
    (should= "v1\n" (read-file (dir "b") "mutate/a.edn"))
    (should-be-nil (read-file (dir "b") "crap.edn"))))
