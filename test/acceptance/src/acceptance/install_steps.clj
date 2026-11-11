(ns acceptance.install-steps
  "Step handlers for test/features/install (see its README for the step
  meanings): the steps of acceptance.metrics-steps, and the ones that write
  files in the scratch repository and read what git add -A stages there."
  (:require [acceptance.metrics-steps :as metrics]
            [acceptance.store-steps :refer [write-file]]
            [acceptance.support :as support :refer [check unescape]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def new-world metrics/new-world)

(defn- in-repo [world & cmd]
  (apply support/must (:repo world) cmd))

(defn- lines-of [text]
  (sort (remove str/blank? (str/split-lines text))))

(defn- staged-under [world dir]
  (in-repo world "git" "add" "-A")
  (lines-of (in-repo world "git" "ls-files" "--" dir)))

(defn- file-lines [world path]
  (str/split-lines (slurp (str (fs/path (:repo world) path)))))

(defn- path-without-bb
  "The PATH of the clean environment with each directory that holds bb replaced
  by a scratch directory of links to its other programs."
  []
  (let [path (get (support/clean-env nil) "PATH")]
    (str/join ":" (for [[index dir] (map-indexed vector (str/split path #":"))]
                    (if-not (fs/exists? (fs/path dir "bb"))
                      dir
                      (let [links (fs/path @support/scratch "path-without-bb" (str index))]
                        (when-not (fs/exists? links)
                          (fs/create-dirs links)
                          (doseq [program (fs/list-dir dir)
                                  :when (not= "bb" (str (fs/file-name program)))]
                            (fs/create-sym-link (fs/path links (fs/file-name program)) program)))
                        (str links)))))))

(defn- err-lines [world]
  (str/split-lines (str (:err world))))

(def ^:private install-handlers
  [;; Given
   [#"no directory on PATH holds bb"
    (fn [world] (assoc world :path (path-without-bb)))]

   [#"a working directory outside the repository holds a bb.edn that requires Babashka \"([^\"]+)\""
    (fn [world version]
      (let [dir (str (fs/path @support/scratch (str "workdir-" (random-uuid))))]
        (write-file dir "bb.edn" (pr-str {:min-bb-version version}))
        (assoc world :workdir dir)))]

   [#"the file \"([^\"]+)\" in the repository holds \"(.*)\""
    (fn [world path text]
      (write-file (:repo world) path (str (unescape text) "\n"))
      world)]

   ;; Then
   [#"the exit status is not 0"
    (fn [world]
      (check (not= 0 (:exit world)) (str "exit status 0, stderr: " (str/trim (str (:err world)))))
      world)]

   [#"the error output has a line containing \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world one other]
      (check (some #(and (str/includes? % one) (str/includes? % other)) (err-lines world))
             (str "error output was " (pr-str (:err world))))
      world)]

   [#"the error output has no line containing \"([^\"]+)\""
    (fn [world text]
      (let [found (filter #(str/includes? % text) (err-lines world))]
        (check (empty? found) (str "error output has " (pr-str found)))
        world))]

   [#"the repository has the same files with the same contents as before install.sh ran"
    (fn [world]
      (let [before (:snapshot world)
            after (metrics/snapshot (:repo world))
            changed (sort (filter #(not= (get before %) (get after %)) (set (concat (keys before) (keys after)))))]
        (check (empty? changed) (str "changed: " (pr-str changed)))
        world))]

   [#"the repository has no file \"([^\"]+)\""
    (fn [world path]
      (check (not (fs/exists? (fs/path (:repo world) path))) (str path " exists"))
      world)]

   [#"git add -A in the repository stages every file under \"([^\"]+)\""
    (fn [world dir]
      (let [present (lines-of (in-repo world "find" dir "-type" "f"))
            staged (staged-under world dir)]
        (check (seq present) (str "no file under " dir))
        (check (= present staged) (str "staged " (pr-str staged) " of " (pr-str present)))
        world))]

   [#"git add -A in the repository stages no file under \"([^\"]+)\""
    (fn [world dir]
      (let [staged (staged-under world dir)]
        (check (empty? staged) (str "staged " (pr-str staged)))
        world))]

   [#"the file \"([^\"]+)\" in the repository has no line that starts with \"(.*)\""
    (fn [world path prefix]
      (let [found (filter #(str/starts-with? % prefix) (file-lines world path))]
        (check (empty? found) (str path " has " (pr-str found)))
        world))]

   [#"the file \"([^\"]+)\" in the repository has no line twice"
    (fn [world path]
      (let [twice (keep (fn [[line n]] (when (> n 1) line)) (frequencies (file-lines world path)))]
        (check (empty? twice) (str path " repeats " (pr-str twice)))
        world))]

   [#"the file \"([^\"]+)\" in the repository has the line \"(.*)\""
    (fn [world path line]
      (check (some #{line} (file-lines world path)) (str path " has no line " (pr-str line)))
      world)]])

(def handlers (concat metrics/handlers install-handlers))
