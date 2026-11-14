;; Run with the pinned crap4clj coverage bb.edn; delegate metrics to Cloverage.
(require '[babashka.tasks :as tasks]
         '[clojure.string :as str]
         '[cloverage.coverage :as coverage]
         'speclj.cloverage)

(defmethod coverage/runner-fn :combined [opts]
  (let [legacy (coverage/runner-fn (assoc opts :runner :clojure.test))
        specs (coverage/runner-fn
                (assoc opts :runner :speclj
                       :runner-opts {:args (into ["-c"] (:test-ns-path opts))}))]
    (fn [namespaces]
      (let [legacy-result (legacy (remove #(str/ends-with? (name %) "-spec") namespaces))
            spec-result (specs namespaces)]
        {:errors (+ (:errors legacy-result) (:errors spec-result))}))))

(tasks/run 'coverage)
