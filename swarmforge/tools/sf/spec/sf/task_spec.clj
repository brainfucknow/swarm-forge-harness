(ns sf.task-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should= should-be-nil around]]
            [sf.bug :as bug]
            [sf.shell :as shell]
            [sf.task :as task]))

(def ^:dynamic *dir* nil)

(def ^:private created-shown "  d1 #1 operator <o@x>\n\n  type: created\nfrom: operator\ncommit: %s\n")

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" "-c" "user.name=t" "-c" "user.email=t@x" args)))

(defn- commit-on-branch [dir branch & messages]
  (git dir "checkout" "-q" "-b" branch)
  (doseq [message messages] (git dir "commit" "-q" "--allow-empty" "-m" message))
  (git dir "rev-parse" "--short" "HEAD"))

(defn- with-repo [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "task-spec-"}))]
    (try
      (git dir "init" "-q" "-b" "main")
      (git dir "commit" "-q" "--allow-empty" "-m" "seed")
      (with-redefs [shell/repo-root (constantly dir)
                    bug/short-id (fn [id] (subs id 0 7))]
        (binding [*dir* dir] (run-spec)))
      (finally (fs/delete-tree dir)))))

(def ^:private lore-lines @#'sf.task/lore-lines)

(describe "lore-lines"
  (around [it] (with-repo it))

  (it "prints the LORE: lines of the commits after the created record"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")
          head (commit-on-branch *dir* "swarm/abcdef1"
                                 "Code\n\nBy coder.\n\nConstraint: keep it\n")]
      (should= [(str "LORE: " head " coder Constraint: keep it")]
               (lore-lines "abcdef1xxxx" (format created-shown base)))))

  (it "is nil when the task has no created record"
    (commit-on-branch *dir* "swarm/abcdef1" "Code")
    (should-be-nil (lore-lines "abcdef1xxxx" "no records")))

  (it "is nil when the task branch is gone"
    (should-be-nil (lore-lines "abcdef1xxxx" (format created-shown "abc1234")))))
