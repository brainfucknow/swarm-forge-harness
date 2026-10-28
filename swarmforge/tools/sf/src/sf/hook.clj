(ns sf.hook
  "The commit-msg hook that adds the `By <role>.` byline and a Lore-id in task
  worktrees."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.lore :as lore]
            [sf.record :as record]
            [sf.shell :as shell]))

(def marker "# swarmforge commit-msg hook")

(defn worktree-dir []
  (shell/env "SWARMFORGE_WORKTREE_DIR" ".worktrees"))

(defn- body []
  (-> (shell/template "commit-msg")
      (str/replace "{{marker}}" marker)
      (str/replace "{{worktree-dir}}" (worktree-dir))))

(defn install
  "Writes the hook unless core.hooksPath is set or another hook is in place."
  [root]
  (let [hooks-path (shell/chomp (:out (shell/run {:dir root} "git" "config" "--get" "core.hooksPath")))
        hooks (fs/path (shell/chomp (shell/must {:dir root} "git" "rev-parse" "--path-format=absolute" "--git-common-dir")) "hooks")
        hook (fs/path hooks "commit-msg")]
    (cond
      (seq hooks-path)
      (println "HOOK: core.hooksPath is set; run 'sf commit-msg <role> <file>' on each commit message")

      (and (fs/exists? hook) (not (str/includes? (slurp (str hook)) marker)))
      (println "HOOK: existing commit-msg hook left alone; run 'sf commit-msg <role> <file>' on each commit message")

      :else
      (do (fs/create-dirs hooks)
          (spit (str hook) (body))
          (fs/set-posix-file-permissions hook "rwxr-xr-x")))))

(defn- comment-char
  "git's core.commentChar; # when it is not set or is auto, as git
  interpret-trailers reads it."
  []
  (let [char (shell/chomp (:out (shell/run {} "git" "config" "--get" "core.commentChar")))]
    (if (or (str/blank? char) (= "auto" char)) "#" char)))

(defn commit-msg
  "Refuses a commit message in file whose trailer block has a SwarmForge-
  trailer; only sf writes those. Otherwise adds the byline for role and a
  Lore-id, as sf.lore/sign-commit does, except at stage done. git
  interpret-trailers finds the trailer block as git log reads it: a --- line in
  the message is text, not a patch divider."
  [[role file]]
  (when (or (str/blank? role) (str/blank? file))
    (shell/die "commit-msg needs a role and a message file"))
  (let [message (slurp file)
        trailers (shell/lines (shell/must {:in message} "git" "interpret-trailers" "--parse" "--no-divider"))
        keys (keep lore/trailer-key trailers)]
    (when-let [key (record/record-trailer keys)]
      (shell/die key " is a SwarmForge record trailer; only sf writes those"))
    (when (not= "done" role)
      (let [signed (lore/sign-commit message (comment-char) role keys lore/new-id)]
        (when (not= signed message)
          (spit file signed))))))
