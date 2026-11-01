(ns sf.clone-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [speclj.core :refer [describe context it should= should should-not should-contain with around]]
            [sf.clone :as clone]
            [sf.shell :as shell]))

(def ^:dynamic *root* nil)

(defn- git [dir & args]
  (let [{:keys [exit out err]} (p/shell {:dir (str dir) :out :string :err :string :continue true
                                         :cmd (into ["git" "-c" "user.name=t" "-c" "user.email=t@t" "-c" "init.defaultBranch=main"] args)})]
    (when-not (zero? exit) (throw (ex-info (str "git " (str/join " " args) ": " err) {})))
    (str/trim out)))

(defn- upstream
  "A repository with one commit per name, each writing the name to version;
  returns {:dir dir name sha ...}."
  [& names]
  (let [dir (str (fs/path *root* "upstream"))]
    (fs/create-dirs dir)
    (git dir "init" "-q")
    (reduce (fn [acc name]
              (spit (str (fs/path dir "version")) name)
              (spit (str (fs/path dir ".gitignore")) "cache/\n")
              (git dir "add" "-A")
              (git dir "commit" "-q" "-m" name)
              (assoc acc name (git dir "rev-parse" "HEAD")))
            {:dir dir} names)))

(defn- clone-dir [] (str (fs/path *root* "project" ".swarmforge" "tools" "x")))

(defn- ensure-at
  "Runs clone/ensure-at; returns [status stderr]."
  [url sha]
  (let [err (java.io.StringWriter.)
        status (binding [*err* err] (shell/main-with "sf tool: " #(clone/ensure-at "x" (clone-dir) url sha)))]
    [status (str err)]))

(defn- head [] (git (clone-dir) "rev-parse" "HEAD"))
(defn- status [] (git (clone-dir) "status" "--porcelain" "--untracked-files=all"))
(defn- detached? [] (str/blank? (:out (p/shell {:dir (clone-dir) :out :string :continue true} "git symbolic-ref -q HEAD"))))

(defn- clean-at? [sha]
  (and (= sha (head)) (detached?) (= "" (status))))

(describe "ensure-at"
  (around [it]
    (let [root (str (fs/canonicalize (fs/create-temp-dir {:prefix "clone-spec-"})))]
      (try
        (git root "init" "-q" "project")
        (binding [*root* root] (it))
        (finally (fs/delete-tree root)))))

  (it "creates a missing clone at the pinned commit"
    (let [{:keys [dir] :as up} (upstream "PIN" "NEXT")]
      (should= [0 ""] (ensure-at dir (up "PIN")))
      (should (clean-at? (up "PIN")))
      (should= "PIN" (slurp (str (fs/path (clone-dir) "version"))))))

  (it "moves a clone, drops local commits, edits and untracked files, and keeps ignored ones"
    (let [{:keys [dir] :as up} (upstream "PIN" "NEXT")]
      (git *root* "clone" "-q" dir (clone-dir))
      (spit (str (fs/path (clone-dir) "version")) "edited")
      (git (clone-dir) "commit" "-qam" "local")
      (spit (str (fs/path (clone-dir) "version")) "edited again")
      (fs/create-dirs (fs/path (clone-dir) "src"))
      (spit (str (fs/path (clone-dir) "src" "extra.clj")) "x")
      (fs/create-dirs (fs/path (clone-dir) "cache"))
      (spit (str (fs/path (clone-dir) "cache" "kept")) "x")
      (should= [0 ""] (ensure-at dir (up "PIN")))
      (should (clean-at? (up "PIN")))
      (should-not (fs/exists? (fs/path (clone-dir) "src")))
      (should (fs/exists? (fs/path (clone-dir) "cache" "kept")))))

  (it "uses a commit the clone has without the network, and never its origin"
    (let [{:keys [dir] :as up} (upstream "PIN" "NEXT")]
      (git *root* "clone" "-q" dir (clone-dir))
      (git (clone-dir) "remote" "set-url" "origin" (str *root* "/nowhere"))
      (should= [0 ""] (ensure-at (str *root* "/nowhere") (up "PIN")))
      (should (clean-at? (up "PIN")))))

  (it "fetches a missing commit from the URL it is given"
    (let [{:keys [dir] :as up} (upstream "PIN" "NEXT")]
      (git *root* "clone" "-q" "--depth" "1" (str "file://" dir) (clone-dir))
      (git (clone-dir) "remote" "set-url" "origin" (str *root* "/nowhere"))
      (should= [0 ""] (ensure-at dir (up "PIN")))
      (should (clean-at? (up "PIN")))))

  (it "runs no hook of the clone"
    (let [{:keys [dir] :as up} (upstream "PIN" "NEXT")
          marker (fs/path *root* "hooked")]
      (git *root* "clone" "-q" dir (clone-dir))
      (spit (str (fs/path (clone-dir) ".git" "hooks" "post-checkout")) (str "#!/bin/sh\ntouch '" marker "'\n"))
      (fs/set-posix-file-permissions (fs/path (clone-dir) ".git" "hooks" "post-checkout") "rwxr-xr-x")
      (should= [0 ""] (ensure-at dir (up "PIN")))
      (should-not (fs/exists? marker))))

  (context "fails"
    (it "when the commit cannot be fetched"
      (let [{:keys [dir] :as up} (upstream "PIN")
            missing (apply str (repeat 40 "a"))]
        (let [[status err] (ensure-at dir missing)]
          (should= 1 status)
          (should-contain (str "sf tool: x: cannot fetch " missing " from " dir) err))
        (should= [1 (str "sf tool: x: cannot fetch " (up "PIN") " from " *root* "/nowhere\n")]
                 (do (fs/delete-tree (clone-dir)) (ensure-at (str *root* "/nowhere") (up "PIN"))))))

    (with not-clone-message (str "sf tool: x: " (clone-dir) " is not the top level of a git clone\n"))

    (it "untouched when the directory is not its own clone"
      (let [{:keys [dir] :as up} (upstream "PIN")]
        (fs/create-dirs (clone-dir))
        (spit (str (fs/path (clone-dir) "README.md")) "x")
        (should= [1 @not-clone-message] (ensure-at dir (up "PIN")))
        (should= ["README.md"] (map (comp str fs/file-name) (fs/list-dir (clone-dir))))
        (should-not (fs/exists? (fs/path *root* "project" ".git" "FETCH_HEAD")))))

    (it "when the directory is a link to a clone"
      (let [{:keys [dir] :as up} (upstream "PIN")]
        (fs/create-dirs (fs/parent (clone-dir)))
        (fs/create-sym-link (clone-dir) dir)
        (should= [1 @not-clone-message] (ensure-at dir (up "PIN")))))

    (it "when the clone's .git points at another repository"
      (let [{:keys [dir] :as up} (upstream "PIN")]
        (fs/create-dirs (clone-dir))
        (spit (str (fs/path (clone-dir) ".git")) (str "gitdir: " dir "/.git\n"))
        (should= [1 @not-clone-message] (ensure-at dir (up "PIN")))))))
