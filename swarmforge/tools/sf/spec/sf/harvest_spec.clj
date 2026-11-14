(ns sf.harvest-spec
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [speclj.core :refer [describe it should= should-not-contain]]
            [sf.harvest :as harvest]))

(def ^:private records
  [{:record "created" :from "operator" :sha "c0" :commit "c0" :stage "specifier" :route "specifier,coder"
    :needs "none" :text "Add login\n\nUsers log in."}
   {:record "question" :from "specifier" :sha "q1" :commit "c0" :stage "specifier" :route "specifier,coder"
    :needs "operator" :text "Which?"}
   {:record "review" :from "specifier" :sha "v1" :commit "s1" :stage "specifier" :route "specifier,coder"
    :needs "approval" :text "ready" :audit-refusals "2"}
   {:record "approval" :from "operator" :sha "a1" :commit "s1" :stage "specifier" :route "specifier,coder"
    :needs "none" :rejected ["DEFECT, LOW: a | b | reason" "C | d"]}
   {:record "handoff" :from "specifier" :sha "h1" :commit "s1" :stage "coder" :route "specifier,coder"
    :needs "none" :text "specifier work complete"}
   {:record "route" :from "operator" :sha "r1" :commit "s1" :stage "coder" :route "coder"
    :needs "none"}
   {:record "moved" :from "operator" :sha "m1" :commit "s1" :stage "coder" :route "coder"
    :needs "none" :text "old/path.md"}
   {:record "finished" :from "operator" :sha "f1" :commit "b1" :stage "done" :route "coder"
    :needs "none" :merged-into "main"}])

(def ^:private task
  {:id "add-login-1234567" :status "finished" :truncated false :title "Add login" :request "Users log in."
   :records records
   :commits [{:sha "s1" :message "Spec\n\nBy specifier.\n" :trailers ["Lore-id: 0badc0de" "Constraint: keep it"]}
             {:sha "a1" :message "sf approval: t\n" :trailers ["Lore-id: 1234abcd" "Rejected: C | d"]}]})

(defn- parsed [task]
  (json/parse-string (harvest/line task)))

(def ^:private null-record
  {"type" nil "from" nil "stage" nil "needs" nil "route" nil "commit" nil "text" nil
   "merged_into" nil "rejected" nil "audit_refusals" nil "spec_verdict" nil "time" nil "recorded_by" nil})

(describe "line"
  (it "is one line of JSON"
    (let [line (harvest/line task)]
      (should-not-contain "\n" line)
      (should= "add-login-1234567" (get (json/parse-string line) "id"))))

  (it "holds the task and the state of its newest record"
    (should= {"id" "add-login-1234567" "title" "Add login" "request" "Users log in." "status" "finished"
              "truncated" false "stage" "done" "needs" "none" "route" ["coder"]}
             (select-keys (parsed task) ["id" "title" "request" "status" "truncated" "stage" "needs" "route"])))

  (it "gives each record every key, null where it does not apply"
    (should= [(merge null-record {"type" "created" "from" "operator" "stage" "specifier" "needs" "none"
                                  "route" ["specifier" "coder"] "commit" "c0"})
              (merge null-record {"type" "question" "from" "specifier" "stage" "specifier" "needs" "operator"
                                  "route" ["specifier" "coder"] "text" "Which?"})
              (merge null-record {"type" "review" "from" "specifier" "stage" "specifier" "needs" "approval"
                                  "route" ["specifier" "coder"] "commit" "s1" "text" "ready" "audit_refusals" 2})
              (merge null-record {"type" "approval" "from" "operator" "stage" "specifier" "needs" "none"
                                  "route" ["specifier" "coder"]
                                  "rejected" [{"finding" "DEFECT, LOW: a | b" "reason" "reason" "kind" "DEFECT" "severity" "LOW"}
                                              {"finding" "C" "reason" "d" "kind" nil "severity" nil}]})
              (merge null-record {"type" "handoff" "from" "specifier" "stage" "coder" "needs" "none"
                                  "route" ["specifier" "coder"] "commit" "s1" "text" "specifier work complete"})
              (merge null-record {"type" "route" "from" "operator" "stage" "coder" "needs" "none" "route" ["coder"]})
              (merge null-record {"type" "moved" "from" "operator" "stage" "coder" "needs" "none" "route" ["coder"]
                                  "text" "old/path.md"})
              (merge null-record {"type" "finished" "from" "operator" "stage" "done" "needs" "none" "route" ["coder"]
                                  "merged_into" "main"})]
             (get (parsed task) "records")))

  (it "gives a spec-verdict record and an automatic send-back their spec verdict, an operator's send-back null"
    (should= ["stuck" "continue" nil]
             (map #(get % "spec_verdict")
                  (get (parsed (assoc task :records
                                      [{:record "spec-verdict" :from "spec-judge" :spec-verdict "stuck" :text "cap"}
                                       {:record "changes-requested" :from "spec-judge" :spec-verdict "continue" :text "r1"}
                                       {:record "changes-requested" :from "operator" :text "no"}]))
                       "records"))))

  (it "ends each record with its time and the name that recorded it"
    (let [line (harvest/line (assoc task :records [(assoc (records 4) :time "2026-03-01T08:00:00Z" :recorded-by "Codex")]))
          entry (get-in (json/parse-string line) ["records" 0])
          raw (second (re-find #"\"records\":\[\{([^}]*)\}" line))]
      (should= ["type" "from" "stage" "needs" "route" "commit" "text" "merged_into" "rejected" "audit_refusals"
                "spec_verdict" "time" "recorded_by"]
               (map second (re-seq #"\"(\w+)\":" raw)))
      (should= ["2026-03-01T08:00:00Z" "Codex"] [(get entry "time") (get entry "recorded_by")])))

  (it "gives an approval without findings an empty rejected list"
    (should= [] (get-in (parsed (assoc task :records [(assoc (records 3) :rejected [])])) ["records" 0 "rejected"])))

  (it "gives a commit outside the readable history as null, never blank"
    (should= [nil nil]
             (map #(get % "commit")
                  (get (parsed (assoc task :records [(assoc (records 4) :commit "") (dissoc (records 2) :commit)]))
                       "records"))))

  (it "gives audit refusals that are not a count as null"
    (should= nil (get-in (parsed (assoc task :records [(assoc (records 2) :audit-refusals "x")])) ["records" 0 "audit_refusals"])))

  (it "gives each Lore trailer but Lore-id with its commit's Lore-id, role null where there is none"
    (should= [{"commit" "s1" "role" "specifier" "key" "Constraint" "value" "keep it" "lore_id" "0badc0de"}
              {"commit" "a1" "role" nil "key" "Rejected" "value" "C | d" "lore_id" "1234abcd"}]
             (get (parsed task) "lore"))))

(describe "line keys fixes and found_by"
  (it "follow route and precede records"
    (should= ["id" "title" "request" "status" "truncated" "stage" "needs" "route" "fixes" "found_by" "records"]
             (map second (re-seq #"\"(\w+)\":" (re-find #"^.*?\"records\":" (harvest/line task)))))))

  (it "hold the target and the detector of the created record"
    (let [created (assoc (records 0) :fixes "#115" :found-by "ci")]
      (should= {"fixes" "#115" "found_by" "ci"}
               (select-keys (parsed (assoc task :records (assoc records 0 created))) ["fixes" "found_by"]))))

  (it "are null for a task without a link and for a truncated task"
    (should= {"fixes" nil "found_by" nil} (select-keys (parsed task) ["fixes" "found_by"]))
    (should= {"fixes" nil "found_by" nil}
             (select-keys (parsed (assoc task :truncated true :records (vec (rest records)))) ["fixes" "found_by"])))

(describe "line of a truncated task"
  (it "has a blank title and request"
    (should= {"truncated" true "title" "" "request" ""}
             (select-keys (parsed (assoc task :truncated true :title nil :request nil))
                          ["truncated" "title" "request"]))))

(describe "line text"
  (it "keeps non-ASCII text and escapes line breaks"
    (let [line (harvest/line (assoc task :request "é\nü"))]
      (should= true (str/includes? line "\"request\":\"é\\nü\"")))))
