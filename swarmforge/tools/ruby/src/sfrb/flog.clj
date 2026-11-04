(ns sfrb.flog
  "Method complexity as flog's ABC-style score, read from `flog -a`."
  (:require [clojure.string :as str]
            [sfcore.changes :as changes]))

(defn args
  "`bundle` arguments scoring every method of `files`."
  [files]
  (into ["exec" "flog" "-a" "--"] files))

(def ^:private method-line #"^\s*(\d+(?:\.\d+)?): (\S+)\s+(.+):(\d+)-(\d+)\s*$")

(defn parse-report
  "The methods in flog's text report; totals and code outside methods
  (`#none`) carry no location and are left out."
  [text root]
  (vec (for [line (str/split-lines text)
             :let [[_ score name file start end] (re-find method-line line)]
             :when (and score (not (str/ends-with? name "#none")))]
         {:file (changes/relative-path file root)
          :line (parse-long start)
          :last-line (parse-long end)
          :name name
          :complexity (parse-double score)})))
