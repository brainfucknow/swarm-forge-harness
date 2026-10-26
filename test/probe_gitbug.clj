#!/usr/bin/env bb
;; Probe: does the next git-bug process see a label that git-bug just wrote?
;; Prints one line per round. When the label is not visible, also prints the raw
;; ref and git views.
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.string :as str])

(def rounds (parse-long (or (first *command-line-args*) "30")))
(def scratch (fs/create-temp-dir))
(def repo (fs/path scratch "repo"))
(def env (assoc (into {} (System/getenv)) "HOME" (str (fs/path scratch "home"))))

(defn sh [& cmd]
  (str/trim-newline (:out (p/shell {:cmd (vec cmd) :dir (str repo) :extra-env env :out :string :err :string}))))

(defn read-or [fallback & path]
  (let [file (apply fs/path repo path)]
    (if (fs/exists? file) (str/trim-newline (slurp (str file))) fallback)))

(fs/create-dirs (get env "HOME"))
(fs/create-dirs repo)
(sh "git" "config" "--global" "user.name" "tester")
(sh "git" "config" "--global" "user.email" "tester@example.com")
(sh "git" "config" "--global" "init.defaultBranch" "main")
(sh "git" "init" "-q" ".")
(sh "git" "commit" "-q" "--allow-empty" "-m" "seed")
(sh "git-bug" "user" "new" "--non-interactive" "-n" "op" "-e" "op@example.com")
(def full (sh "git-bug" "bug" "show" (first (str/split (sh "git-bug" "bug" "new" "--non-interactive" "-t" "probe" "-m" "probe") #"\s+")) "--field" "id"))
(def ref (str "refs/bugs/" full))

(defn label-count [label]
  (count (filter #{label} (str/split-lines (sh "git-bug" "bug" "show" full "--field" "labels")))))

;; Each round adds a label, reads, removes it, reads, and adds it again, as
;; `sf reject` and then `sf review` do. Each read must agree with the last write.
(defn check [round step label expect]
  (let [seen (label-count label)]
    (if (= expect seen)
      (do (println (str "round " round " " step ": ok")) 0)
      (let [later (do (Thread/sleep 1000) (label-count label))]
        (println (str "round " round " " step ": STALE (expected " expect ", read " seen "; after 1s: " later ") ref=" (sh "git" "rev-parse" ref)))
        (println (str "  file: " (read-or "missing" ".git" ref)))
        (println (str "  for-each-ref: " (str/replace (sh "git" "for-each-ref" ref) "\t" " ")))
        (println (str "  lock: " (read-or "none" ".git" "git-bug" "lock")))
        (println (str "  commits on ref: " (sh "git" "rev-list" "--count" ref)))
        1))))

(try
  (let [stale (reduce + (for [round (range 1 (inc rounds))
                              :let [label (str "probe:" round)]
                              [op step expect] [["new" "add" 1] ["rm" "rm" 0] ["new" "re-add" 1]]]
                          (do (sh "git-bug" "bug" "label" op full label)
                              (check round step label expect))))]
    (println (str "stale reads: " stale " / " (* rounds 3))))
  (finally (fs/delete-tree scratch)))
