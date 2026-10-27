;; Persistent runner adapter for gherkin-mutator:
;;
;;   gherkin-mutator --runner-worker "bb test/acceptance/manifests/runner.clj" ...
;;
;; Reads one JSON job per line on stdin and writes one JSON result per line on
;; stdout. A job runs the generated entry points of its generated_dir against
;; the job's feature_json. The entry points run from the repository root.
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def ^:private floor-ms
  "A feature runs each scenario with real sf, git and git-bug processes, so the
  mutator's default job timeout of 30s is too short."
  600000)

(defn- timeout-ms [text]
  (let [[_ n unit] (re-matches #"(\d+)(ms|s|m)?" (str text))
        requested (if n (* (parse-long n) (case unit "ms" 1 "m" 60000 1000)) 0)]
    (max floor-ms requested)))

(defn- run-tests [generated-dir feature-json timeout-ms]
  (let [tests (sort (map str (fs/glob generated-dir "*_test.clj")))]
    (if (empty? tests)
      {:outcome "infrastructure_error" :output "" :error (str "no generated tests in " generated-dir)}
      (let [results (for [test tests]
                      (let [proc (p/process {:cmd ["bb" "-cp" "test" test feature-json]
                                             :out :string :err :string})
                            result (deref proc timeout-ms ::timeout)]
                        (if (= ::timeout result)
                          (do (p/destroy-tree proc) {:timeout true})
                          {:exit (:exit result) :output (str (:out result) (:err result))})))
            output (str/join "\n" (map :output results))]
        (cond (some :timeout results) {:outcome "infrastructure_error" :output output :error "timeout"}
              (every? #(zero? (:exit %)) results) {:outcome "test_success" :output output :error ""}
              (and (some #(= 1 (:exit %)) results) (re-find #"(?m)^FAIL " output))
              {:outcome "test_failure" :output output :error ""}
              :else {:outcome "infrastructure_error" :output output :error "test process failed to run"})))))

(defn- changed-cells
  "Descriptions of the example cells of the mutated IR that differ from the
  base IR next to its mutations/ directory."
  [work-dir feature-json]
  (let [base (json/parse-string (slurp (str (fs/path work-dir ".." ".." "base" "feature.json"))))
        mutated (json/parse-string (slurp feature-json))
        examples (fn [ir] (for [[s scenario] (map-indexed vector (get ir "scenarios"))
                                [e example] (map-indexed vector (get scenario "examples"))
                                [k v] example]
                            [(str "$.scenarios[" s "].examples[" e "]." k) v]))]
    (for [[[path old] [_ new]] (map vector (examples base) (examples mutated))
          :when (not= old new)]
      (str path ": " old " -> " new))))

(defn- report-to-stderr
  "gherkin-mutator can lose its report on stdout when it exits with status 1,
  so the adapter names every mutation that survived on stderr."
  [{:strs [id work_dir feature_json]} result]
  (when (not= "test_failure" (:outcome result))
    (binding [*out* *err*]
      (println (str (:outcome result) " " id " "
                    (try (str/join "; " (changed-cells work_dir feature_json))
                         (catch Exception e (str "(" e ")")))
                    (when (seq (:error result)) (str " [" (:error result) "]")))))))

(defn- answer [{:strs [id feature_json generated_dir timeout] :as job}]
  (let [start (System/nanoTime)
        result (try (run-tests generated_dir feature_json (timeout-ms timeout))
                    (catch Exception e
                      {:outcome "infrastructure_error" :output "" :error (str e)}))]
    (report-to-stderr job result)
    (json/generate-string (assoc result :id id :duration (- (System/nanoTime) start)))))

(doseq [line (line-seq (java.io.BufferedReader. *in*))
        :when (not (str/blank? line))]
  (println (answer (json/parse-string line)))
  (flush))
