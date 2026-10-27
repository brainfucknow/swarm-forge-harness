(ns sf.manifests
  "Moves mutation manifests between a task worktree's .metrics/ and the git
  notes ref refs/notes/mutate, and the gherkin-mutator block between a feature
  file and .metrics/gherkin/."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.feature-block :as feature-block]
            [sf.manifest :as manifest]
            [sf.shell :as shell]))

(def ^:private notes-flag (str "--ref=" manifest/notes-ref))

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" args)))

(defn- note-blobs
  "The note blob of each annotated commit."
  [dir]
  (into {} (map #(vec (reverse (str/split % #"\s+"))))
        (shell/lines (git dir "notes" notes-flag "list"))))

(defn- read-note [dir blob]
  (manifest/decode-note (git dir "cat-file" "blob" blob)))

(defn- reachable-notes
  "The notes on the commits that HEAD reaches, newest first."
  [dir]
  (let [blobs (note-blobs dir)]
    (if (empty? blobs)
      []
      (->> (shell/lines (git dir "rev-list" "HEAD"))
           (keep blobs)
           (map #(read-note dir %))))))

(defn- notes-copies [dir]
  (manifest/newest-copies (reachable-notes dir)))

(defn- worktree-files
  "Each file under .metrics/, by its path relative to the worktree."
  [dir]
  (let [metrics (fs/path dir manifest/root)]
    (if-not (fs/directory? metrics)
      {}
      (into {} (for [file (fs/glob metrics "**" {:hidden true})
                     :when (fs/regular-file? file)]
                 [(str/join "/" (map str (fs/relativize dir file))) (slurp (str file))])))))

(defn- ignored? [dir]
  (shell/ok? (shell/run {:dir dir} "git" "check-ignore" "-q" manifest/root)))

(defn load!
  "Writes the notes' copy of each path that the worktree does not have.
  Returns the MANIFESTS: line."
  [dir]
  (if-not (ignored? dir)
    "MANIFESTS: .metrics/ is not ignored by git; nothing loaded"
    (let [files (manifest/to-load (notes-copies dir) (keys (worktree-files dir)))]
      (doseq [[path content] files
              :let [file (fs/path dir path)]]
        (fs/create-dirs (fs/parent file))
        (spit (str file) content))
      (str "MANIFESTS: " (count files) " loaded from " manifest/notes-ref))))

(defn- unsaved [dir]
  (manifest/to-save (worktree-files dir) (notes-copies dir)))

(defn save!
  "Adds the files that differ from the notes' copy to the note on HEAD.
  Returns the MANIFESTS: line."
  [dir]
  (let [files (unsaved dir)]
    (if (empty? files)
      "MANIFESTS: nothing to save"
      (let [head (git dir "rev-parse" "HEAD")
            current (some->> (get (note-blobs dir) head) (read-note dir))
            note (manifest/encode-note (merge current files))
            blob (shell/chomp (shell/must {:dir dir :in note} "git" "hash-object" "-w" "--stdin"))]
        (git dir "notes" notes-flag "add" "-f" "-C" blob head)
        (str "MANIFESTS: " (count files) " saved to " manifest/notes-ref " on "
             (git dir "rev-parse" "--short" "HEAD"))))))

(defn tested
  "The Tested: trailers of the files that save! would save."
  [dir]
  (manifest/tested-lines (unsaved dir)))

(defn- saved-block-path [feature]
  (str (fs/path manifest/root "gherkin" feature)))

(defn block-in
  "Puts the saved gherkin-mutator block at the top of a feature without one."
  [feature]
  (let [saved (saved-block-path feature)]
    (when (and (fs/exists? saved) (fs/exists? feature))
      (let [text (slurp feature)]
        (when-not (feature-block/block-lines text)
          (spit feature (feature-block/with-block (slurp saved) text)))))))

(defn block-out
  "Moves the gherkin-mutator block of a feature to .metrics/gherkin/<feature>."
  [feature]
  (when (fs/exists? feature)
    (let [text (slurp feature)]
      (when-let [lines (feature-block/block-lines text)]
        (let [saved (saved-block-path feature)]
          (fs/create-dirs (fs/parent saved))
          (spit saved (str (str/join "\n" lines) "\n"))
          (spit feature (feature-block/without-block text)))))))
