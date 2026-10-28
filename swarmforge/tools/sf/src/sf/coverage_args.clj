(ns sf.coverage-args
  "The crap4clj wrapper's argument rewrite, on plain values. crap4clj runs in
  the directory of the first --source-root or -s value, with every source root
  made relative to it, and gets --coverage-command for the Babashka coverage
  config unless the caller settled coverage or clj and deps.edn are there.
  Absolute paths are written as bash writes them: no trailing slash, and the
  root as the empty path."
  (:require [clojure.string :as str]))

(defn- parent
  "The path without its last part, as bash's ${path%/*}."
  [path]
  (if-let [i (str/last-index-of path "/")] (subs path 0 i) path))

(defn- base-name [path]
  (subs path (inc (or (str/last-index-of path "/") -1))))

(defn- child [dir name]
  (str dir "/" name))

(defn absolute
  "path from the directory here, without empty, . and .. parts."
  [here path]
  (reduce (fn [out part]
            (case part
              ("" ".") out
              ".." (parent out)
              (child out part)))
          ""
          (str/split (if (str/starts-with? path "/") path (child here path)) #"/")))

(defn relative
  "The path that leads from the directory base to path."
  [path base]
  (loop [base base up ""]
    (if (or (= path base) (str/starts-with? path (child base "")))
      (let [rest (str up (str/replace-first (subs path (count base)) #"^/" ""))]
        (if (= "" rest) "." (str/replace rest #"/$" "")))
      (recur (parent base) (str "../" up)))))

(defn- target-of
  "The target of path through directory, a function from an absolute path to
  its target when it is a directory, else nil."
  [directory path]
  (when (seq path) (directory path)))

(defn physical
  "The absolute path with its symlinks followed: the target of the directory,
  else the name in the target of its parent, else the path itself."
  [directory path]
  (or (target-of directory path)
      (when-let [dir (target-of directory (parent path))]
        (child (str/replace dir #"/$" "") (base-name path)))
      path))

(defn- options
  "The index of each --source-root and -s value in args, and whether the caller
  passed --coverage-command or --use-existing-coverage. --lcov values are no
  options."
  [args]
  (loop [k 0 roots [] settled? false]
    (if (< k (count args))
      (case (args k)
        ("--source-root" "-s") (recur (+ k 2) (cond-> roots (< (inc k) (count args)) (conj (inc k))) settled?)
        "--lcov" (recur (+ k 2) roots settled?)
        ("--coverage-command" "--use-existing-coverage") (recur (inc k) roots true)
        (recur (inc k) roots settled?))
      {:roots roots :settled? settled?})))

(defn- coverage-command [config roots has-dir?]
  (str/join " " (concat ["bb --config" config "coverage"]
                        (when (has-dir? "spec") ["-r speclj"])
                        (mapcat #(vector "-p" %) (or (seq roots) ["src"]))
                        (mapcat #(vector "-s" %) (filter has-dir? ["spec" "test"]))
                        ["--lcov"])))

(defn plan
  "What the wrapper does from the directory here with args: {:dir :args}, where
  dir is the directory to run crap4clj in (nil to stay), or {:error}.
  directory maps an absolute path to its target when it is a directory, file?
  tells a regular file, clj? whether the Clojure CLI is on PATH, and config is
  the Babashka coverage config."
  [{:keys [here args directory file? clj? config]}]
  (let [here (absolute here ".")
        args (vec args)
        {:keys [roots settled?]} (options args)
        roots (filter #(seq (args %)) roots)
        targets (map #(physical directory (absolute here (args %))) roots)
        dir (some-> (first targets) parent)]
    (if (and (seq dir) (nil? (target-of directory dir)))
      {:error (str "no such directory: " dir)}
      (let [args (reduce (fn [args [k target]] (assoc args k (relative target dir)))
                         args (map vector roots targets))
            at (or dir here)
            has-dir? #(some? (target-of directory (child at %)))
            settled? (or settled? (and clj? (file? (child at "deps.edn"))))]
        {:dir (when dir (if (= "" dir) "/" dir))
         :args (cond-> args
                 (not settled?) (conj "--coverage-command" (coverage-command config (map args roots) has-dir?)))}))))
