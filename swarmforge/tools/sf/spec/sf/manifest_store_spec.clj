(ns sf.manifest-store-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [around describe it should should= should-contain]]
            [sf.manifest-store :as store]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)

(defn- git [& args]
  (shell/chomp (apply shell/must {:dir *dir*} "git" args)))

(defn- write [path text]
  (let [file (fs/path *dir* path)]
    (fs/create-dirs (fs/parent file))
    (spit (str file) text)))

(defn- read-file [path]
  (slurp (str (fs/path *dir* path))))

(defn- commit []
  (git "commit" "-q" "--allow-empty" "-m" "work"))

(defn- repository [ignore]
  (git "init" "-q" "-b" "main")
  (git "config" "user.name" "tester")
  (git "config" "user.email" "tester@example.com")
  (write ".gitignore" ignore)
  (git "add" "-A")
  (commit))

(defn- in-repository [ignore example]
  (fs/with-temp-dir [dir {:prefix "manifest-store-spec"}]
    (binding [*dir* (str dir)]
      (repository ignore)
      (example))))

(def clj-manifest (pr-str {:version 2 :source "src/a.clj" :forms [{:killed 2 :survived 1 :uncovered 0}]}))
(def block "# acceptance-mutation-manifest-begin\n# {\"scenarios\":[]}\n# acceptance-mutation-manifest-end\n")

(describe "load! and save!"
  (around [example] (in-repository ".metrics/\n" example))

  (it "loads nothing when no note exists"
    (should= "MANIFESTS: 0 loaded from refs/notes/mutate" (store/load! *dir*)))

  (it "saves a manifest as a note on HEAD and loads it back"
    (write ".metrics/mutate/a.edn" clj-manifest)
    (should= (str "MANIFESTS: 1 saved to refs/notes/mutate on " (git "rev-parse" "--short" "HEAD"))
             (store/save! *dir*))
    (fs/delete (fs/path *dir* ".metrics/mutate/a.edn"))
    (should= "MANIFESTS: 1 loaded from refs/notes/mutate" (store/load! *dir*))
    (should= clj-manifest (read-file ".metrics/mutate/a.edn")))

  (it "saves nothing when every file equals its copy"
    (write ".metrics/mutate/a.edn" clj-manifest)
    (store/save! *dir*)
    (commit)
    (should= "MANIFESTS: nothing to save" (store/save! *dir*))
    (should (not (shell/ok? (shell/run {:dir *dir*} "git" "notes" "--ref=mutate" "list" "HEAD")))))

  (it "keeps the files of the note on HEAD when it saves more"
    (write ".metrics/mutate/a.edn" "a")
    (store/save! *dir*)
    (write ".metrics/mutate/b.edn" "b")
    (store/save! *dir*)
    (fs/delete-tree (fs/path *dir* ".metrics"))
    (should= "MANIFESTS: 2 loaded from refs/notes/mutate" (store/load! *dir*)))

  (it "loads the newest copy among the notes on the commits that HEAD reaches"
    (write ".metrics/mutate/a.edn" "old")
    (store/save! *dir*)
    (commit)
    (write ".metrics/mutate/a.edn" "new")
    (store/save! *dir*)
    (commit)
    (fs/delete (fs/path *dir* ".metrics/mutate/a.edn"))
    (store/load! *dir*)
    (should= "new" (read-file ".metrics/mutate/a.edn")))

  (it "does not load notes of commits that HEAD does not reach"
    (git "checkout" "-q" "-b" "other")
    (commit)
    (write ".metrics/mutate/a.edn" "other")
    (store/save! *dir*)
    (git "checkout" "-q" "main")
    (should= "MANIFESTS: 0 loaded from refs/notes/mutate" (store/load! *dir*)))

  (it "keeps a file that the worktree already has"
    (write ".metrics/mutate/a.edn" "saved")
    (store/save! *dir*)
    (write ".metrics/mutate/a.edn" "local")
    (should= "MANIFESTS: 0 loaded from refs/notes/mutate" (store/load! *dir*))
    (should= "local" (read-file ".metrics/mutate/a.edn")))

  (it "ignores a note that is not a manifest note"
    (git "notes" "--ref=mutate" "add" "-m" "free text" "HEAD")
    (should= "MANIFESTS: 0 loaded from refs/notes/mutate" (store/load! *dir*))))

(describe "load! in a repository that tracks .metrics/"
  (around [example] (in-repository "" example))

  (it "loads nothing and says so"
    (write ".metrics/mutate/a.edn" "a")
    (should= "MANIFESTS: .metrics/ is not ignored by git; nothing loaded" (store/load! *dir*))))

(describe "tested"
  (around [example] (in-repository ".metrics/\n" example))

  (it "gives the Tested: line of an unsaved manifest and none once it is saved"
    (write ".metrics/mutate/a.edn" clj-manifest)
    (should= ["Tested: clj-mutate src/a.clj | 2 killed, 1 survived"] (store/tested *dir*))
    (store/save! *dir*)
    (should= [] (store/tested *dir*))))

(describe "block-out and block-in"
  (around [example] (in-repository ".metrics/\n" example))

  (it "move the block of a feature to .metrics/gherkin/ and back"
    (write "f.feature" (str block "\nFeature: f\n"))
    (store/block-out *dir* "f.feature")
    (should= "Feature: f\n" (read-file "f.feature"))
    (should= block (read-file ".metrics/gherkin/f.feature"))
    (store/block-in *dir* "f.feature")
    (should= (str block "\nFeature: f\n") (read-file "f.feature")))

  (it "leave a feature without a block as it is"
    (write "f.feature" "Feature: f\n")
    (store/block-out *dir* "f.feature")
    (should= "Feature: f\n" (read-file "f.feature"))
    (should (not (fs/exists? (fs/path *dir* ".metrics/gherkin/f.feature")))))

  (it "do nothing for a feature that does not exist"
    (store/block-out *dir* "missing.feature")
    (store/block-in *dir* "missing.feature")
    (should (not (fs/exists? (fs/path *dir* "missing.feature")))))

  (it "keep the block that a feature has instead of the saved one"
    (write ".metrics/gherkin/f.feature" "# saved\n")
    (write "f.feature" (str block "\nFeature: f\n"))
    (store/block-in *dir* "f.feature")
    (should= (str block "\nFeature: f\n") (read-file "f.feature")))

  (it "keep a feature that has no saved block as it is"
    (write "f.feature" "Feature: f\n")
    (store/block-in *dir* "f.feature")
    (should= "Feature: f\n" (read-file "f.feature"))
    (should-contain "f.feature" (git "ls-files" "--others" "--exclude-standard"))))
