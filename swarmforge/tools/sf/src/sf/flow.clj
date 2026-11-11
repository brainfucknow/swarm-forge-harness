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

(defn to-refusal
  "Refuses a --to that skips past the route: to must be the stage, a later role
  on the route, or done after the route's last role."
  [{:keys [stage route] :as state} to]
  (when-not (or (str/blank? to)
                (some #{to} (drop-while #(not= stage %) (route-list route)))
                (= to (next-stage state nil)))
    (if (= "done" to)
      (str "--to done skips the rest of route '" route "' after stage:" stage "; hand off to the next role")
      (str "--to " to " is not on route '" route "' at or after stage:" stage))))

(defn handoff-refusal
  ([state] (handoff-refusal state (:commit state)))
  ([state head]
   (cond
     (waiting? state)
     (str "task is waiting on the operator (needs:" (:needs state) "); handoff refused")
     (= "specifier" (:stage state))
     (cond
       (not (and (= "approval" (:record state)) (= "operator" (:from state))))
       "specification requires operator approval; run sf review and sf approve"
       (not= (:commit state) head)
       "specification changed since approval; run sf review again"))))

(defn approval-refusal
  ([state]
   (when (not= "approval" (:needs state)) "task is not waiting for approval"))
  ([state reviewed head]
   (or (approval-refusal state)
       (when-not (record/same-commit? reviewed head)
         "specification changed since review; run sf review again"))))

(defn operator-refusal
  "Refuses the operator's command when dispatched names the role or gate that
  sf dispatch runs: new tasks, approval, answers, rejections, routes and
  finishing are the operator's."
  [command dispatched]
  (when-not (str/blank? dispatched)
    (str command " is the operator's; the dispatched " dispatched " cannot run it")))

(defn operator-record
  "The first record that the operator wrote among records, or nil."
  [records]
  (first (filter #(= "operator" (:from %)) records)))

(defn reviewed-commit
  "The commit the newest review record reviewed: the one it names, else, for a
  review made before sf named it, the commit the record sits on. A rebase
  moves the record onto another commit but keeps the one it names."
  [records]
  (let [{:keys [reviewed commit]} (last (filter #(= "review" (:record %)) records))]
    (or reviewed commit)))

(defn route-refusal
  "Refuses a route that drops the stage of an open task."
  [{:keys [stage]} route]
  (when-not (or (= "done" stage) (some #{stage} (route-list route)))
    (str "route '" route "' does not contain the current stage '" stage "'")))

(defn created
  "The first record of a task on route; its free text holds the title and the request."
  [title request route]
  {:record "created" :from "operator" :text (record/created-text title request)
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

(defn finish-action
  "What sf finish does with a done task, from whether HEAD's history has its
  finished record (recorded?), its branch tip (nil when it has no branch) and
  whether HEAD contains the tip (tip-merged?): :merge the branch when HEAD has
  neither, :record the finished merge on top of HEAD when HEAD contains the
  tip without the record, :keep HEAD when it has both, :unmerged when the
  branch moved after the recorded merge, else nil: the task is finished. No
  action rewrites a commit HEAD already has."
  [recorded? tip tip-merged?]
  (cond (nil? tip) nil
        recorded? (if tip-merged? :keep :unmerged)
        tip-merged? :record
        :else :merge))

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
