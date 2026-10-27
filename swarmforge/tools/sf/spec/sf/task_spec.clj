(ns sf.task-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should= should-be-nil around]]
            [sf.shell :as shell]
            [sf.task]))

(def ^:dynamic *dir* nil)

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" "-c" "user.name=t" "-c" "user.email=t@x" args)))

(defn- with-repo [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "task-spec-"}))]
    (try
      (git dir "init" "-q" "-b" "main")
      (git dir "commit" "-q" "--allow-empty" "-m" "Base\n\nTested: base")
      (with-redefs [shell/repo-root (constantly dir)]
        (binding [*dir* dir] (run-spec)))
      (finally (fs/delete-tree dir)))))

(def ^:private lore-lines @#'sf.task/lore-lines)

(describe "lore-lines"
  (around [it] (with-repo it))

  (it "prints the LORE: lines of the first-parent commits after the created record"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")
          _ (git *dir* "commit" "-q" "--allow-empty" "-m" "Code\n\nBy coder.\n\nConstraint: keep it\n")
          head (git *dir* "rev-parse" "--short" "HEAD")]
      (should= [(str "LORE: " head " coder Constraint: keep it")]
               (lore-lines "HEAD" [{:record "created" :commit base}]))))

  (it "leaves out the commits that a merge brings in"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")]
      (git *dir* "checkout" "-q" "-b" "other")
      (git *dir* "commit" "-q" "--allow-empty" "-m" "Other\n\nTested: other\n")
      (git *dir* "checkout" "-q" "main")
      (git *dir* "merge" "-q" "--no-ff" "--no-edit" "other")
      (should= [] (lore-lines "HEAD" [{:record "created" :commit base}]))))

  (it "is nil when there is no created record"
    (should-be-nil (lore-lines "HEAD" [{:record "question"}]))))
