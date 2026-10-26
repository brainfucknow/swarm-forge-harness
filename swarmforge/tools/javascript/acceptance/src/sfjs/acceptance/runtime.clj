(ns sfjs.acceptance.runtime
  "Acceptance runtime: expands a scenario of the JSON IR into executions,
  resolves example values, and dispatches each step to a handler."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :as t]))

(def ^:dynamic *feature*
  "A parsed IR that replaces the one embedded in the generated entry points;
  the mutation worker binds it to each mutated IR."
  nil)

(defn feature [ir-json]
  (or *feature* (json/parse-string ir-json)))

(defn executions
  "One execution per example row (one with no values when there are none),
  background steps first."
  [feature index]
  (let [scenario (nth (get feature "scenarios") index)
        steps (concat (get feature "background") (get scenario "steps"))
        examples (get scenario "examples")]
    (for [[i example] (map-indexed vector (if (seq examples) examples [{}]))]
      {:name (str (get scenario "name") "/example_" (inc i)) :steps steps :example example})))

(defn resolve-step
  "The step text with each <parameter> replaced by its example value."
  [step example]
  (reduce (fn [text parameter]
            (if-let [value (get example parameter)]
              (str/replace text (str "<" parameter ">") value)
              (throw (ex-info (str "no example value for <" parameter "> in: " (get step "text")) {}))))
          (get step "text")
          (get step "parameters")))

(defn dispatch [steps world text]
  (let [matches (keep (fn [[regex handler]]
                        (when-let [found (re-matches regex text)]
                          [handler (if (string? found) [] (drop 1 found))]))
                      steps)]
    (case (count matches)
      0 (throw (ex-info (str "unsupported step: " text) {}))
      1 (let [[handler captures] (first matches)] (apply handler world captures))
      (throw (ex-info (str "ambiguous step: " text) {})))))

(defn run-execution
  "Runs one execution in a fresh world; returns nil or the failure."
  [{:keys [steps fresh cleanup]} {:keys [example] :as execution}]
  (let [world (volatile! (fresh))]
    (try
      (doseq [step (:steps execution)]
        (let [text (resolve-step step example)]
          (try
            (vswap! world #(dispatch steps % text))
            (catch Exception e
              (throw (ex-info (str (get step "keyword") " " text ": " (ex-message e)) {}))))))
      nil
      (catch Exception e (ex-message e))
      (finally (cleanup @world)))))

(defn run-scenario
  "Runs every execution of scenario `index`, reporting each to clojure.test."
  [handlers ir-json index]
  (doseq [execution (executions (feature ir-json) index)]
    (let [failure (run-execution handlers execution)]
      (t/is (nil? failure) (str (:name execution) "\n  " failure)))))
