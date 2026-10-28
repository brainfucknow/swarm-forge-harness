(ns sf.metrics
  "Mutation manifests are a cache on the orphan branch swarmforge/metrics,
  whose tree mirrors .metrics/ by path. Only the manifests under mutate/
  (clj-mutate) and gherkin/ (the gherkin-mutator block) are cached. A load
  copies the ones a checkout lacks; a save commits the new and changed ones
  without a checkout. A manifest carries the hashes of its source, so a stale
  copy costs a re-run, never a wrong result."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.git :as git]
            [sf.shell :as shell]))

(def ^:private branch "refs/heads/swarmforge/metrics")
(def ^:private kinds ["mutate" "gherkin"])

(defn- cached
  "The blob of each manifest at rev, by path."
  [rev]
  (into {} (for [line (shell/lines (apply git/git "ls-tree" "-r" rev "--" kinds))
                 :let [[_ blob path] (re-matches #"\S+ blob (\S+)\t(.+)" line)]
                 :when blob]
             [path blob])))

(defn- local-paths
  "The manifests under the .metrics/ of checkout, as paths in it."
  [checkout]
  (let [root (fs/path checkout ".metrics")]
    (sort (for [kind kinds
                file (when (fs/directory? (fs/path root kind)) (fs/glob (fs/path root kind) "**"))
                :when (fs/regular-file? file)]
            (str (fs/relativize root file))))))

(defn load!
  "Copies each cached manifest that the .metrics/ of checkout lacks."
  [checkout]
  (when-let [rev (git/rev branch)]
    (doseq [[path blob] (cached rev)
            :let [file (fs/path checkout ".metrics" path)]
            :when (not (fs/exists? file))]
      (fs/create-dirs (fs/parent file))
      (spit (str file) (git/out {} "cat-file" "blob" blob)))))

(defn- stale
  "The manifests of checkout whose blob is not the one at rev, by path."
  [rev checkout]
  (let [paths (local-paths checkout)
        files (map #(str (fs/path checkout ".metrics" %)) paths)
        blobs (when (seq paths) (shell/lines (apply git/git "hash-object" "--" files)))
        known (if rev (cached rev) {})]
    (into {} (for [[path file blob] (map vector paths files blobs)
                   :when (not= blob (known path))]
               [path (slurp file)]))))

(defn save!
  "Commits the new and changed manifests of checkout on top of the tip, or as
  the first commit of the branch. When another save moves the branch first,
  sf reads it again and retries once; then it warns and saves nothing."
  [id checkout]
  (let [{:keys [error]}
        (git/advance! branch
                      (fn [rev]
                        (let [files (stale rev checkout)]
                          (when (seq files)
                            {:commit (git/commit-tree (git/tree-with rev [] files) (if rev [rev] [])
                                                      (str "sf metrics: " id "\n\nSwarmForge-Task: " id "\n"))}))))]
    (when error (shell/warn "manifests not saved: " (str/trim error)))))
