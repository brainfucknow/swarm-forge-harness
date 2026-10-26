(ns sfcore.changes
  "Changed-line sets: reading them from a unified diff and asking whether a
  tool-reported file and line range touch one. sfcore.git collects them."
  (:require [clojure.string :as str]))

(defn- hunk-header [line]
  (re-find #"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@" line))

(defn- note-line [{:keys [current cursor] :as state}]
  (cond-> (-> state (update :pending-new dec) (update :cursor inc))
    current (update-in [:result current] (fnil conj (sorted-set)) cursor)))

(defn- start-file [state raw]
  (let [name (str/replace (subs raw 4) #"\t.*$" "")
        file (when-not (= name "/dev/null") (str/replace name #"^b/" ""))]
    (cond-> (assoc state :current file)
      file (update :result #(if (contains? % file) % (assoc % file (sorted-set)))))))

(defn- read-hunk-line [state raw]
  (if (str/starts-with? raw "+") (note-line state) state))

(defn- read-header-line [state raw]
  (if-let [[_ new-start new-count] (hunk-header raw)]
    (assoc state
           :cursor (parse-long new-start)
           :pending-new (if new-count (parse-long new-count) 1))
    state))

(defn- read-diff-line [{:keys [pending-new] :as state} raw]
  (cond
    (pos? pending-new) (read-hunk-line state raw)
    (str/starts-with? raw "+++ ") (start-file state raw)
    :else (read-header-line state raw)))

(defn changed-lines-from-diff
  "Reads `git diff -U0` output into a map from new-side path to the sorted set
  of line numbers that were added or rewritten."
  [text]
  (:result (reduce read-diff-line
                   {:current nil :pending-new 0 :result {}}
                   (str/split-lines text))))

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
