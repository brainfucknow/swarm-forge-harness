(ns sf.clone
  "Puts sf tool's cache clone of a GitHub source at its pinned commit. The
  directory is sf's: it must be the top level of its own git clone, and ensure
  discards what it holds besides the pinned commit. git runs there with the
  clone's hooks, fsmonitor and ext transport off, fetches only from the URL it
  is given, and only when the clone lacks the commit."
  (:require [babashka.fs :as fs]
            [sf.shell :as shell]))

(def ^:private untrusted-off
  ["-c" "core.hooksPath=/dev/null" "-c" "core.fsmonitor=false" "-c" "protocol.ext.allow=never"])

(defn- git [opts dir & args]
  (apply shell/run opts "git" "-C" (str dir) (concat untrusted-off args)))

(defn- git-out
  "The trimmed stdout of a read-only git command in dir, or nil when it fails."
  [dir & args]
  (let [result (apply git {} dir args)]
    (when (shell/ok? result) (shell/chomp (:out result)))))

(defn- own-clone?
  "True when dir, not a symbolic link, is the top level of a git clone whose
  .git directory is its own."
  [dir]
  (let [real (str (fs/canonicalize dir))]
    (and (not (fs/sym-link? dir))
         (fs/directory? (fs/path dir ".git") {:nofollow-links true})
         (= real (git-out dir "rev-parse" "--show-toplevel"))
         (= (str (fs/path real ".git")) (git-out dir "rev-parse" "--absolute-git-dir")))))

(defn- has-commit? [dir sha]
  (some? (git-out dir "rev-parse" "--verify" "--quiet" "--end-of-options" (str sha "^{commit}"))))

(defn- must [tool what result]
  (when-not (shell/ok? result)
    (shell/die tool ": " what)))

(defn- init [tool dir]
  (fs/create-dirs (fs/parent dir))
  (must tool (str "cannot create a git clone in " dir)
        (shell/run {:err :inherit} "git" "init" "--quiet" "--template=" "--" (str dir))))

(defn ensure-at
  "Puts dir, the cache clone of the GitHub repository at url, at the commit
  sha: detached, with tracked files as committed and untracked files that are
  not ignored removed. Creates a missing clone and fetches sha from url only
  when the clone lacks it. Stops with an error naming tool, before any git
  command that changes state, when dir is not the top level of its own clone."
  [tool dir url sha]
  (when-not (fs/exists? dir {:nofollow-links true})
    (init tool dir))
  (when-not (own-clone? dir)
    (shell/die tool ": " dir " is not the top level of a git clone"))
  (when-not (has-commit? dir sha)
    (must tool (str "cannot fetch " sha " from " url)
          (git {:err :inherit} dir "fetch" "--quiet" "--no-tags" "--no-recurse-submodules" "--depth" "1" "--" url sha)))
  (must tool (str "cannot check out " sha " in " dir)
        (git {:err :inherit} dir "checkout" "--quiet" "--force" "--detach" sha "--"))
  (must tool (str "cannot remove untracked files in " dir)
        (git {:err :inherit} dir "clean" "--quiet" "-f" "-f" "-d"))
  (when-not (= sha (git-out dir "rev-parse" "--verify" "HEAD"))
    (shell/die tool ": " dir " is not at " sha " after checkout")))
