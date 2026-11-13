(ns sfcs.measurement
  "File-level evidence policy for .NET CRAP checks."
  (:require [sfcore.changes :as changes]))

(defn- usable? [{:keys [first-line last-line complexity coverage]}]
  (and (pos-int? first-line) (pos-int? last-line)
       (number? complexity) (<= 0 complexity Double/MAX_VALUE)
       (number? coverage) (<= 0 coverage 1)))

(defn missing-files
  "Candidates without usable method evidence in any report, excluding declared no-code files."
  [root scope exempt methods]
  (let [paths (zipmap scope (repeat true))
        measured (into #{} (keep #(changes/to-changed-path (:file %) paths root)) (filter usable? methods))]
    (remove #(or (contains? measured %) (contains? exempt %)) scope)))
