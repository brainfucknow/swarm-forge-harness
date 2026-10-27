(ns acceptance.manifests.runtime
  "Runs the scenario executions of a gherkin-parser JSON IR through regex step
  handlers. A suite is {:handlers [[regex handler] ...] :new-world f
  :close-world f}. A handler gets the world and the regex groups and returns
  the next world; it throws to fail the step."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn fail! [& parts]
  (throw (ex-info (apply str parts) {})))

(defn- fill-example [text example]
  (str/replace text #"<([A-Za-z0-9_]+)>"
               (fn [[_ name]]
                 (or (get example name) (fail! "no example value for <" name ">")))))

(defn- handler-for [handlers text]
  (let [hits (keep (fn [[regex handler]]
                     (when-let [match (re-matches regex text)]
                       [handler (if (string? match) [] (rest match))]))
                   handlers)]
    (case (count hits)
      0 (fail! "unsupported step")
      1 (first hits)
      (fail! "ambiguous step: " (count hits) " handlers match"))))

(defn- run-step [handlers world example {:strs [text]}]
  (try
    (let [text (fill-example text example)
          [handler args] (handler-for handlers text)]
      (apply handler world args))
    (catch Exception e
      (fail! (pr-str text) ": " (or (ex-message e) (str e))))))

(defn executions
  "One execution per example row of the scenario at index, or one without
  examples. Background steps come first."
  [ir index]
  (let [{:strs [name steps examples]} (get-in ir ["scenarios" index])
        steps (concat (get ir "background") steps)]
    (map-indexed (fn [i example]
                   {:name (str name "/example_" (inc i)) :steps steps :example example})
                 (if (seq examples) examples [{}]))))

(defn- run-execution
  "nil when the execution passes, else the failure message."
  [{:keys [handlers new-world close-world]} {:keys [steps example]}]
  (let [world (atom nil)]
    (try
      (reset! world (new-world))
      (doseq [step steps]
        (reset! world (run-step handlers @world example step)))
      nil
      (catch Exception e (ex-message e))
      (finally (when @world (close-world @world))))))

(defn run-scenarios
  "Runs the scenarios at indexes and returns the count of failed executions."
  [suite ir-path indexes]
  (let [ir (json/parse-string (slurp ir-path))]
    (count
     (for [index indexes
           execution (executions ir index)
           :let [failure (run-execution suite execution)
                 _ (if failure
                     (println (str "FAIL " (:name execution) ": " failure))
                     (println (str "ok   " (:name execution))))]
           :when failure]
       failure))))
