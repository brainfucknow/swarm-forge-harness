(ns sf.harvest
  "The JSON line that sf harvest prints for a task: its request, its records
  and its Lore, as plain values."
  (:require [cheshire.core :as json]
            [sf.flow :as flow]
            [sf.lore :as lore]
            [sf.record :as record]))

(defn- route-of [route]
  (when route (flow/route-list route)))

(defn- refusal-count [value]
  (some-> (re-matches #"\d+" (str value)) parse-long))

(defn- rejected-entry [value]
  (let [{:keys [finding reason kind severity]} (record/rejected-finding value)]
    (array-map "finding" finding "reason" reason "kind" kind "severity" severity)))

(defn- record-entry
  "The record with every key of a harvested record, null where it does not apply."
  [{:keys [record from stage needs route commit text merged-into rejected audit-refusals spec-verdict] :as rec}]
  (array-map
   "type" record
   "from" from
   "stage" stage
   "needs" needs
   "route" (route-of route)
   "commit" (when (record/milestone? rec) (not-empty commit))
   "text" (when (not= "created" record) text)
   "merged_into" (when (= "finished" record) merged-into)
   "rejected" (when (= "approval" record) (mapv rejected-entry rejected))
   "audit_refusals" (when (#{"review" "handoff"} record) (refusal-count audit-refusals))
   "spec_verdict" (when (#{"spec-verdict" "changes-requested"} record) spec-verdict)))

(defn- lore-entry [{:keys [sha role key value lore-id]}]
  (array-map "commit" sha "role" (when (not= "-" role) role) "key" key "value" value "lore_id" lore-id))

(defn line
  "The JSON text, on one line, of task: its :id, :status (open or finished),
  :truncated, :title, :request, :records oldest first and the :commits whose
  Lore trailers make its LORE: lines, as lore/read-log reads them."
  [{:keys [id status truncated title request records commits]}]
  (let [state (last records)]
    (json/generate-string
     (array-map
      "id" id
      "title" (or title "")
      "request" (or request "")
      "status" status
      "truncated" (boolean truncated)
      "stage" (:stage state)
      "needs" (:needs state)
      "route" (route-of (:route state))
      "records" (mapv record-entry records)
      "lore" (mapv lore-entry (lore/entries commits))))))
