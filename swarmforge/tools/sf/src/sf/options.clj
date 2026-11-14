(ns sf.options
  "The flags and free text of the task commands."
  (:require [clojure.string :as str]
            [sf.record :as record]
            [sf.shell :as shell]))

(defn read-file [path]
  (shell/chomp (slurp path)))

(defn- require-value
  "Stops when flag, the head of args, has nothing after it."
  [[flag :as args]]
  (when (< (count args) 2)
    (shell/die flag " needs " (if (= flag "-F") "a file" "a value"))))

(defn parse
  "Reads flags into a map. values maps a flag that takes a value to its key, or
  to [key transform]; switches maps a flag without a value to its key. A flag
  in once may not be given twice; any other keeps its last value."
  ([command args values switches] (parse command args values switches #{}))
  ([command args values switches once]
   (loop [opts {} seen #{} [flag value :as args] args]
     (cond
       (empty? args) opts
       (switches flag) (recur (assoc opts (switches flag) true) seen (rest args))
       (values flag) (let [[key transform] (if (vector? (values flag)) (values flag) [(values flag) identity])]
                       (require-value args)
                       (when (and (once flag) (seen flag)) (shell/die flag " given twice"))
                       (recur (assoc opts key (transform value)) (conj seen flag) (drop 2 args)))
       :else (shell/die "unknown " command " option '" flag "'")))))

(defn free-text
  "The arguments as one text; stops with complaint when there are none."
  [args complaint]
  (let [text (record/free-text args)]
    (when (str/blank? text) (shell/die complaint))
    text))

(defn message
  "-m and the words after it, -F and a file, or the words; default when none."
  [default [flag value :as args]]
  (case flag
    (nil "") default
    "-m" (do (require-value args) (str/join " " (rest args)))
    "-F" (do (require-value args) (read-file value))
    (str/join " " args)))

(defn handoff
  "--to and its role anywhere in args; the rest is the summary."
  [args]
  (let [at (.indexOf (vec args) "--to")]
    (if (neg? at)
      {:summary (message nil args)}
      (do (when (= at (dec (count args))) (shell/die "--to needs a value"))
          {:to (nth args (inc at))
           :summary (message nil (concat (take at args) (drop (+ at 2) args)))}))))

(defn rejections
  "The values of the `--rejected <finding> | <reason>` options that make up
  args, each trimmed, in order; stops on any other argument and on a value that
  record/rejected-refusal refuses."
  [args]
  (loop [values [] [flag value :as args] args]
    (cond
      (empty? args) values
      (not= "--rejected" flag) (shell/die "unknown approve argument '" flag "'")
      :else (let [value (do (require-value args) (str/trim value))]
              (some-> (record/rejected-refusal value) shell/die)
              (recur (conj values value) (drop 2 args))))))
