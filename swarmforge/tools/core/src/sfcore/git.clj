(ns sfcore.git
  "The task's changed lines, read from git: the IO side of sfcore.changes."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]))

(defn git
  "Runs git with the given arguments and returns its stdout; throws on failure."
  [args & {:keys [dir]}]
  (let [result (apply p/shell {:out :string :err :string :dir (or dir shell/*dir*) :continue true}
                      "git" "-c" "core.quotePath=false" args)]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "git " (str/join " " args) ": " (str/trim (:err result))) {})))
    (:out result)))

(defn repo-root [& {:keys [dir]}]
  (str/trim (git ["rev-parse" "--show-toplevel"] :dir dir)))

(defn ref-exists? [ref dir]
  (zero? (:exit (p/shell {:out :string :err :string :dir (or dir shell/*dir*) :continue true}
                         "git" "rev-parse" "--verify" "--quiet" (str ref "^{commit}")))))

(defn first-existing
  "The first of `refs` that is non-nil and satisfies `exists?`."
  [refs exists?]
  (some (fn [ref] (when (and ref (exists? ref)) ref)) refs))

(defn default-base [dir]
  (or (first-existing [(System/getenv "SWARMFORGE_BASE") "main" "master"] #(ref-exists? % dir))
      (throw (outcome/usage-error "no base branch found; pass --base <ref> or set SWARMFORGE_BASE"))))

(defn base-ref
  "--base when given, else the default base."
  [opts]
  (or (:base opts) (default-base nil)))

(defn- whole-file-lines [root file]
  (let [count (count (str/split (slurp (str (fs/file root file))) #"\n" -1))]
    (into (sorted-set) (range 1 (inc count)))))

(defn collect-changed-lines
  "Changed source lines for the task: everything since the merge base with
  `base`, committed or not, plus whole untracked files. `pathspecs` are git
  pathspecs naming the source files, such as *.cs. Paths are repo-relative."
  [{:keys [base dir pathspecs]}]
  (let [root (repo-root :dir dir)
        merge-base (str/trim (git ["merge-base" base "HEAD"] :dir dir))
        committed (changes/changed-lines-from-diff
                   (git (into ["diff" "-U0" "--no-color" "--no-ext-diff" merge-base "--"] pathspecs) :dir root))
        untracked (remove str/blank?
                          (str/split-lines
                           (git (into ["ls-files" "--others" "--exclude-standard" "--full-name" "--"] pathspecs) :dir root)))]
    (reduce (fn [changed file] (assoc changed file (whole-file-lines root file))) committed untracked)))

(defn changed-since-base
  "The command's changed-line scope: lines under `pathspecs` changed since
  the base, or nil with --all."
  [opts pathspecs]
  (when-not (:all opts)
    (collect-changed-lines {:base (base-ref opts) :pathspecs pathspecs})))
