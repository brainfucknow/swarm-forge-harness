(ns property-support
  "defspec for Babashka, which bundles test.check without its clojure.test
  glue: a deftest that runs quick-check and reports the shrunk failure."
  (:require [clojure.test :as t]
            [clojure.test.check :as tc]))

(defn check [trials property]
  (let [{:keys [pass? shrunk seed] :as result} (tc/quick-check trials property)]
    (t/is pass? (str "seed " seed ", smallest failing input " (pr-str (:smallest shrunk))
                     (when-let [e (:result shrunk)] (when (instance? Throwable e) (str "\n" (ex-message e))))))
    result))

(defmacro defspec [name trials property]
  `(t/deftest ~name (check ~trials ~property)))
