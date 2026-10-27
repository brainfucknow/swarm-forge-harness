(ns acceptance.runtime
  "Runs every scenario execution of a feature in APS JSON IR form: background
  steps first, one execution per example row, each with a fresh world. A step
  goes to the one handler whose pattern matches its text."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn executions [ir]
  (for [{:strs [name steps examples]} (get ir "scenarios")
        [index example] (map-indexed vector (if (seq examples) examples [{}]))]
    {:label (str name " [example_" (inc index) "]")
     :steps (concat (get ir "background") steps)
     :example example}))

(defn fill
  "The step text with each <parameter> of the step replaced by its example value."
  [{:strs [text parameters]} example]
  (let [parameters (set parameters)]
    (str/replace text #"<([^<>\s]+)>"
                 (fn [[whole name]]
                   (cond (not (parameters name)) whole
                         (contains? example name) (get example name)
                         :else (throw (ex-info (str "no example value for <" name ">") {})))))))

(defn- run-step [handlers world text]
  (let [matches (keep (fn [[pattern handler]]
                        (when-let [groups (re-matches pattern text)]
                          [handler (if (string? groups) [] (rest groups))]))
                      handlers)]
    (case (count matches)
      0 (throw (ex-info (str "unsupported step: " text) {}))
      1 (let [[handler groups] (first matches)] (apply handler world groups))
      (throw (ex-info (str "ambiguous step: " text) {})))))

(defn run-execution
  "nil when the execution passes, else the failure text."
  [handlers new-world {:keys [steps example]}]
  (try
    (reduce (fn [world step]
              (let [text (fill step example)]
                (try (run-step handlers world text)
                     (catch Exception e
                       (throw (ex-info (str (get step "keyword") " " text ": " (ex-message e)) {}))))))
            (new-world) steps)
    nil
    (catch Exception e (ex-message e))))

(defn run-feature
  "Runs the feature and prints one ok or FAIL line per execution. Returns the
  number of failures."
  [ir handlers new-world]
  (count (for [execution (executions ir)
               :let [failure (run-execution handlers new-world execution)]
               :when (do (println (if failure
                                    (str "FAIL " (:label execution) "\n     " failure)
                                    (str "ok   " (:label execution))))
                         (flush)
                         failure)]
           failure)))

(defn main
  "Runs the feature in the IR file and exits 1 when an execution fails."
  [ir-path handlers new-world]
  (let [failures (run-feature (json/parse-string (slurp ir-path)) handlers new-world)]
    (System/exit (if (zero? failures) 0 1))))
