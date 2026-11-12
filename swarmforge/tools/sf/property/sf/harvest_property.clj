(ns sf.harvest-property
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.harvest :as harvest]
            [sf.lore :as lore]
            [sf.property-support :refer [commit-with hex7 passes? role sentence word]]
            [sf.record :as record]))

(def ^:private kind (gen/elements ["DEFECT" "PREMISE"]))

(def ^:private severity (gen/elements ["CRITICAL" "HIGH" "MEDIUM" "LOW"]))

(def ^:private finding
  "Finding text, sometimes with the `<KIND>, <SEVERITY>: ` prefix, sometimes
  holding ` | ` itself."
  (gen/let [prefix (gen/one-of [(gen/return nil) (gen/tuple kind severity)])
            parts (gen/not-empty (gen/vector sentence 1 3))]
    {:prefix prefix
     :text (str (when-let [[k s] prefix] (str k ", " s ": ")) (str/join " | " parts))}))

(describe "rejected-finding"
  (it "splits a value at its last ' | ' back into the finding and the reason"
    (should (passes? (prop/for-all [{:keys [text]} finding reason sentence]
                       (let [{f :finding r :reason} (record/rejected-finding (str text " | " reason))]
                         (= [text reason] [f r]))))))

  (it "parses the kind and severity exactly when the finding starts with them"
    (should (passes? (prop/for-all [{:keys [prefix text]} finding reason sentence]
                       (let [{k :kind s :severity} (record/rejected-finding (str text " | " reason))]
                         (= (or prefix [nil nil]) [k s]))))))

  (it "keeps a value with no ' | ' whole as the finding, with no reason"
    (should (passes? (prop/for-all [text sentence]
                       (= [text nil] ((juxt :finding :reason) (record/rejected-finding text))))))))

(def ^:private lore-trailer
  (gen/fmap (fn [[k v]] (str k ": " v))
            (gen/tuple (gen/elements ["Constraint" "Rejected" "Directive" "Tested" "Not-tested"
                                      "Supersedes" "Co-Authored-By"])
                       sentence)))

(def ^:private commit (commit-with hex7 lore-trailer))

(defn- lore-trailers [{:keys [trailers]}]
  (remove #(= "Lore-id" (lore/trailer-key %))
          (filter #(lore/lore-key? (lore/trailer-key %)) trailers)))

(describe "lore entries"
  (it "are the LORE lines but the Lore-id ones, in order"
    (should (passes? (prop/for-all [commits (gen/vector commit 0 5)]
                       (= (remove #(str/includes? % " Lore-id: ") (lore/lines commits))
                          (for [{:keys [sha role key value]} (lore/entries commits)]
                            (str "LORE: " sha " " role " " key ": " value)))))))

  (it "carry the Lore-id of the commit that holds them"
    (should (passes? (prop/for-all [commits (gen/vector commit 0 5)]
                       (= (for [c commits _ (lore-trailers c)] [(:sha c) (:id c)])
                          (map (juxt :sha :lore-id) (lore/entries commits))))))))

(def ^:private record-type
  (gen/elements ["created" "question" "answer" "changes-requested" "review" "approval" "handoff"
                 "route" "start" "moved" "finished"]))

(def ^:private route (gen/fmap #(str/join "," %) (gen/not-empty (gen/vector role 1 4))))

(def ^:private rec
  (gen/let [type record-type
            from role
            stage role
            needs (gen/elements ["none" "operator" "approval"])
            route route
            sha hex7
            commit (gen/one-of [(gen/return "") hex7])
            text (gen/one-of [(gen/return nil) sentence])
            refusals (gen/one-of [(gen/return nil) (gen/fmap str gen/nat) word])
            rejected (gen/vector (gen/fmap :text finding) 0 3)]
    (cond-> {:record type :from from :stage stage :needs needs :route route :sha sha :commit commit
             :merged-into "main"}
      text (assoc :text text)
      refusals (assoc :audit-refusals refusals)
      (= "approval" type) (assoc :rejected rejected))))

(def ^:private task
  (gen/let [id (gen/fmap #(str "t-" %) hex7)
            status (gen/elements ["open" "finished"])
            truncated gen/boolean
            title (gen/one-of [(gen/return nil) sentence])
            records (gen/not-empty (gen/vector rec 1 8))
            commits (gen/vector commit 0 4)]
    {:id id :status status :truncated truncated :title title :request title
     :records records :commits commits}))

(def ^:private record-keys
  #{"type" "from" "stage" "needs" "route" "commit" "text" "merged_into" "rejected" "audit_refusals" "verdict"})

(describe "line"
  (it "is one line of JSON that reads back the task's id, status and newest state"
    (should (passes? (prop/for-all [t task]
                       (let [text (harvest/line t)
                             parsed (json/parse-string text)
                             state (last (:records t))]
                         (and (not (str/includes? text "\n"))
                              (= [(:id t) (:status t) (boolean (:truncated t)) (:stage state) (:needs state)
                                  (str/split (:route state) #",")]
                                 (mapv parsed ["id" "status" "truncated" "stage" "needs" "route"]))))))))

  (it "keeps every record in order, each with exactly the record keys"
    (should (passes? (prop/for-all [t task]
                       (let [records (get (json/parse-string (harvest/line t)) "records")]
                         (and (= (map :record (:records t)) (map #(get % "type") records))
                              (every? #(= record-keys (set (keys %))) records)))))))

  (it "gives audit_refusals as an integer or null, only on review and handoff"
    (should (passes? (prop/for-all [t task]
                       (every? (fn [[r out]]
                                 (let [n (get out "audit_refusals")]
                                   (if (and (#{"review" "handoff"} (:record r))
                                            (re-matches #"\d+" (str (:audit-refusals r))))
                                     (= (parse-long (:audit-refusals r)) n)
                                     (nil? n))))
                               (map vector (:records t)
                                    (get (json/parse-string (harvest/line t)) "records")))))))

  (it "gives a commit only on created, review and handoff, never an empty one"
    (should (passes? (prop/for-all [t task]
                       (every? (fn [[r out]]
                                 (let [c (get out "commit")]
                                   (if (and (record/milestone? r) (seq (:commit r)))
                                     (= (:commit r) c)
                                     (nil? c))))
                               (map vector (:records t)
                                    (get (json/parse-string (harvest/line t)) "records")))))))

  (it "exports one lore element per Lore trailer but Lore-id"
    (should (passes? (prop/for-all [t task]
                       (= (count (mapcat lore-trailers (:commits t)))
                          (count (get (json/parse-string (harvest/line t)) "lore"))))))))
