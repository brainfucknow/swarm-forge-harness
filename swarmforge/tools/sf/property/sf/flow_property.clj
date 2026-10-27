(ns sf.flow-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.flow :as flow]
            [sf.property-support :refer [passes? role sentence]]))

(def ^:private catalogue ["specifier" "coder" "cleaner" "architect" "hardener" "qa"])

(def ^:private route
  "A route: a non-empty subsequence of the catalogue."
  (gen/fmap #(str/join "," (filter (set %) catalogue)) (gen/not-empty (gen/set (gen/elements catalogue)))))

(def ^:private needs (gen/elements ["none" "operator" "approval"]))

(def ^:private state
  (gen/let [r route
            stage (gen/elements (flow/route-list r))
            n needs]
    {:stage stage :route r :needs n}))

(describe "next-stage"
  (it "walks the route in order from its first role to done"
    (should (passes? (prop/for-all [r route]
                       (let [roles (flow/route-list r)
                             walk (take (inc (count roles))
                                        (iterate #(flow/next-stage {:stage % :route r} nil) (first roles)))]
                         (= (concat roles ["done"]) walk))))))

  (it "goes where --to says"
    (should (passes? (prop/for-all [s state to role]
                       (= to (flow/next-stage s to)))))))

(describe "refusals"
  (it "refuse a handoff exactly when the task waits on the operator"
    (should (passes? (prop/for-all [s state]
                       (= (flow/waiting? s) (some? (flow/handoff-refusal s)))))))

  (it "keep any route that holds the stage"
    (should (passes? (prop/for-all [s state]
                       (nil? (flow/route-refusal s (:route s))))))))

(describe "records"
  (it "carry the stage, route and needs on unless their fields change them"
    (should (passes? (prop/for-all [s state from role fields (gen/map (gen/elements [:needs :route :text]) sentence)]
                       (= (merge s fields {:record "answer" :from from})
                          (flow/follow (assoc s :sha "abc" :commit "def") "answer" from fields))))))

  (it "hand off with a summary that is never blank, waiting on no one"
    (should (passes? (prop/for-all [s state from role summary (gen/one-of [(gen/return nil) (gen/return " ") sentence])]
                       (let [rec (flow/handoff s from (flow/next-stage s nil) "abc1234" summary)]
                         (and (not (str/blank? (:text rec)))
                              (not (flow/waiting? rec))
                              (= (:route s) (:route rec)))))))))
