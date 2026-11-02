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

(def ^:private install-handlers
  [;; Given
   [#"the file \"([^\"]+)\" in the repository holds \"(.*)\""
    (fn [world path text]
      (write-file (:repo world) path (str (unescape text) "\n"))
      world)]

   ;; Then
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
