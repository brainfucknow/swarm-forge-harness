(ns sf.coverage-rewrite
  "The file-system edge of sf.coverage-args for the crap4clj wrapper. Usage:
  bb -cp <sf src> -m sf.coverage-rewrite <coverage config> <args>... prints the
  bash lines that move the wrapper into the directory crap4clj runs in and set
  its arguments, or exits 1."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.coverage-args :as coverage-args]
            [sf.shell :as shell]
            [sf.wrapper :as wrapper]))

(defn bash
  "The lines that carry out the plan in the wrapper."
  [{:keys [dir args]}]
  (str (when dir (str "cd " (wrapper/sq dir) " || exit 1\n"))
       (str/join " " (cons "set --" (map wrapper/sq args))) "\n"))

(defn- directory [path]
  (when (fs/directory? path) (str (fs/real-path path))))

(defn main [[config & args]]
  (let [plan (coverage-args/plan {:here (str (fs/real-path "."))
                                  :args args
                                  :directory directory
                                  :file? #(fs/regular-file? %)
                                  :clj? (shell/executable? "clj")
                                  :config config})]
    (when-let [error (:error plan)] (shell/die error))
    (print (bash plan))))

(defn -main [& args]
  (System/exit (shell/main-with "crap4clj: " #(main args))))
