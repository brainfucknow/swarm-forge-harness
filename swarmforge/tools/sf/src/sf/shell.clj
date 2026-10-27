(ns sf.shell
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn fail
  "Stops the command with an exit status. The launcher prints a non-blank message to stderr."
  [status message]
  (throw (ex-info (or message "") {:sf/exit status})))

(defn die [& parts]
  (fail 1 (apply str parts)))

(defn warn [& parts]
  (binding [*out* *err*] (println (apply str "sf: " parts))))

(defn quietly
  "Calls f with its stdout discarded."
  [f]
  (binding [*out* (java.io.StringWriter.)] (f)))

(defn env
  "The variable's value, or the default when it is unset or empty."
  [name default]
  (let [value (System/getenv name)]
    (if (str/blank? value) default value)))

(defn run
  "Runs a command and captures its output. A program that cannot start exits 127."
  [{:keys [dir in err] :or {err :string}} & cmd]
  (try
    (p/shell (cond-> {:cmd (vec cmd) :out :string :err err :continue true :dir (some-> dir str)}
               in (assoc :in in)))
    (catch java.io.IOException e
      {:exit 127 :out "" :err (ex-message e)})))

(defn ok? [result]
  (zero? (:exit result)))

(defn chomp [text]
  (str/trim-newline text))

(defn must
  "Stdout of a command that must succeed. On failure, stops with its exit status;
  its stderr goes straight to the terminal."
  [opts & cmd]
  (let [result (apply run (assoc opts :err :inherit) cmd)]
    (when-not (ok? result)
      (fail (:exit result) nil))
    (:out result)))

(defn lines [text]
  (remove str/blank? (str/split-lines (or text ""))))

(defn executable?
  "True when `command -v` would find the program."
  [program]
  (if (str/includes? program "/")
    (fs/executable? program)
    (some? (fs/which program))))

(defn repo-root
  "The main checkout, also from inside a task worktree."
  []
  (-> (must {} "git" "rev-parse" "--path-format=absolute" "--git-common-dir")
      chomp fs/path fs/parent str))

(defn checkout-root
  "The checkout the command runs in: the task worktree or the main checkout."
  []
  (chomp (must {} "git" "rev-parse" "--show-toplevel")))

(defn conf-rows
  "The whitespace-separated fields of each line, without blank and comment lines."
  [file]
  (when (fs/exists? file)
    (->> (str/split-lines (slurp (str file)))
         (remove #(re-find #"^\s*(#|$)" %))
         (map #(str/split (str/trim %) #"\s+")))))

(defn template [name]
  (slurp (io/resource (str "sf/" name))))

(defn main-with
  "Runs body-fn and returns the exit status. Messages go to stderr after prefix."
  [prefix body-fn]
  (try
    (body-fn)
    0
    (catch Exception e
      (let [status (:sf/exit (ex-data e))]
        (when-not (str/blank? (ex-message e))
          (binding [*out* *err*] (println (str prefix (ex-message e)))))
        (or status 1)))
    (finally (flush))))
