(ns sf.flow
  "How records move a task along its route, as plain values. state is the
  newest record of a task: its :stage, :route and :needs. A refusal is the
  message a command stops with, or nil to go on."
  (:require [clojure.string :as str]
            [sf.record :as record]))

(defn route-list [route]
  (if (str/blank? route) [] (str/split route #",")))

(defn waiting?
  "True when the task waits on the operator."
  [{:keys [needs]}]
  (not= "none" needs))

(defn next-stage
  "to when it is given, else the role after the stage on the route, else done."
  [{:keys [stage route]} to]
  (or (not-empty to) (second (drop-while #(not= stage %) (route-list route))) "done"))

(defn handoff-refusal [state]
  (when (waiting? state)
    (str "task is waiting on the operator (needs:" (:needs state) "); handoff refused")))

(defn approval-refusal [state]
  (when (not= "approval" (:needs state))
    "task is not waiting for approval"))

(defn route-refusal
  "Refuses a route that drops the stage of an open task."
  [{:keys [stage]} route]
  (when-not (or (= "done" stage) (some #{stage} (route-list route)))
    (str "route '" route "' does not contain the current stage '" stage "'")))

(defn created
  "The first record of a task on route."
  [title route]
  {:record "created" :from "operator" :text title
   :stage (first (route-list route)) :route route :needs "none"})

(defn follow
  "The record of type by from after state: its stage, route and needs, with
  fields over them."
  [state type from fields]
  (merge (select-keys state [:stage :route :needs]) {:record type :from from} fields))

(defn handoff
  "The record that hands the role's commit head from from to to. A blank
  summary says the work is complete."
  [state from to head summary]
  {:record "handoff" :from from :stage to :route (:route state) :needs "none" :commit head
   :text (or (not-empty (str/trim (str summary))) (str from " work complete"))})

(defn finished
  "The record of the merge into target."
  [state target]
  {:record "finished" :from "operator" :stage "done" :route (:route state) :needs "none"
   :merged-into target})

(defn resume-action
  "What sf finish does with a task whose archive is in HEAD: :keep HEAD when
  recorded? (HEAD's history has the finished record), :reword HEAD when it is
  the merge of the branch tip without that record, else nil: the task is
  finished. tip is nil when the task has no branch."
  [recorded? tip head-parents]
  (cond (nil? tip) nil
        recorded? :keep
        (and (= 2 (count head-parents)) (= tip (second head-parents))) :reword))

(defn role-commit
  "The role's commit at the branch tip: the tip itself, or the commit under the
  newest record when the tip is that record."
  [tip newest]
  (if (= tip (:sha newest)) (:commit newest) tip))

(defn created-commit
  "The commit the task branch started from."
  [records]
  (:commit (first (filter #(= "created" (:record %)) records))))

(defn last-milestone-commit
  "The role's commit of the newest created, review or handoff record."
  [records]
  (:commit (last (filter record/milestone? records))))

(defn finished-record [records]
  (last (filter #(= "finished" (:record %)) records)))
