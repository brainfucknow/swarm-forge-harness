(ns sfcore.changes
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sfcore.shell :as shell]))

(defn- hunk-header [line]
  (re-find #"^@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@" line))

(defn- note-line [{:keys [current cursor] :as state}]
  (cond-> (-> state (update :pending-new dec) (update :cursor inc))
    current (update-in [:result current] (fnil conj (sorted-set)) cursor)))

(defn- start-file [state raw]
  (let [name (str/replace (subs raw 4) #"\t.*$" "")
        file (when-not (= name "/dev/null") (str/replace name #"^b/" ""))]
    (cond-> (assoc state :current file)
      file (update :result #(if (contains? % file) % (assoc % file (sorted-set)))))))

(defn- read-diff-line [{:keys [pending-new pending-old] :as state} raw]
  (cond
    (or (pos? pending-new) (pos? pending-old))
    (cond (str/starts-with? raw "+") (note-line state)
          (str/starts-with? raw "-") (update state :pending-old dec)
          :else state)

    (str/starts-with? raw "+++ ") (start-file state raw)

    :else
    (if-let [[_ old-count new-start new-count] (hunk-header raw)]
      (assoc state
             :pending-old (if old-count (parse-long old-count) 1)
             :cursor (parse-long new-start)
             :pending-new (if new-count (parse-long new-count) 1))
      state)))

(defn changed-lines-from-diff
  "Reads `git diff -U0` output into a map from new-side path to the sorted set
  of line numbers that were added or rewritten."
  [text]
  (:result (reduce read-diff-line
                   {:current nil :pending-new 0 :pending-old 0 :cursor 0 :result {}}
                   (str/split-lines text))))

(defn default-base [dir]
  (or (some (fn [ref] (when (and ref (shell/ref-exists? ref dir)) ref))
            [(System/getenv "SWARMFORGE_BASE") "main" "master"])
      (throw (shell/usage-error "no base branch found; pass --base <ref> or set SWARMFORGE_BASE"))))

(defn- whole-file-lines [root file]
  (let [count (count (str/split (slurp (str (fs/file root file))) #"\n" -1))]
    (into (sorted-set) (range 1 (inc count)))))

(defn collect-changed-lines
  "Changed source lines for the task: everything since the merge base with
  `base`, committed or not, plus whole untracked files. `pathspecs` are git
  pathspecs naming the source files, such as *.cs. Paths are repo-relative."
  [{:keys [base dir pathspecs]}]
  (let [root (shell/repo-root :dir dir)
        merge-base (str/trim (shell/git ["merge-base" base "HEAD"] :dir dir))
        committed (changed-lines-from-diff
                   (shell/git (into ["diff" "-U0" "--no-color" "--no-ext-diff" merge-base "--"] pathspecs) :dir root))
        untracked (remove str/blank?
                          (str/split-lines
                           (shell/git (into ["ls-files" "--others" "--exclude-standard" "--full-name" "--"] pathspecs) :dir root)))]
    (reduce (fn [changed file] (assoc changed file (whole-file-lines root file))) committed untracked)))

(defn relative-path
  "Normalizes a path reported by a tool (absolute, relative, Windows) to one
  relative to `root` when it lies under it, with forward slashes."
  [reported root]
  (let [slashes #(str/replace % "\\" "/")
        prefix (when root (str (str/replace (slashes root) #"/+$" "") "/"))
        candidate (slashes reported)
        candidate (if (and prefix (str/starts-with? candidate prefix)) (subs candidate (count prefix)) candidate)]
    (str/replace candidate #"^\./" "")))

(defn to-changed-path
  "Normalizes a path reported by a tool (absolute, relative, Windows) to a
  repo-relative changed path, or nil when it is not a changed file."
  [reported changed root]
  (let [candidate (relative-path reported root)]
    (if (contains? changed candidate)
      candidate
      (some (fn [file] (when (str/ends-with? candidate (str "/" file)) file)) (keys changed)))))

(defn overlaps? [file start end changed]
  (boolean (when-let [lines (get changed file)]
             (some lines (range start (inc end))))))
