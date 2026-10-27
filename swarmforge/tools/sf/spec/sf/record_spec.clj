(ns sf.record-spec
  (:require [speclj.core :refer [describe it should= context]]
            [sf.record :as record]))

(describe "slug"
  (it "lowercases the title and joins its words with -"
    (should= "add-login" (record/slug "Add login")))

  (it "makes each run of other characters one - and trims - at both ends"
    (should= "fix-the-api-s-2-bugs" (record/slug "Fix: the API's 2 bugs!")))

  (it "drops the combining marks of decomposed letters"
    (should= "andra-inloggningen-for" (record/slug "Ändra inloggningen för användare")))

  (it "keeps a slug of 30 characters"
    (should= "twelve-chars-one-two-three-fou" (record/slug "twelve chars one two three fou")))

  (it "cuts a longer slug just before a -"
    (should= "twelve-chars-one-two-three" (record/slug "twelve chars one two three four")))

  (it "cuts a first word longer than 30 characters at 30"
    (should= "supercalifragilisticexpialidoc" (record/slug "Supercalifragilisticexpialidocious word")))

  (it "is task when nothing is left"
    (should= "task" (record/slug "!!!"))))

(describe "resolve"
  (let [ids ["add-login-1111111" "add-logout-2222222"]]
    (it "takes the full id"
      (should= [:ok "add-login-1111111"] (record/resolve "add-login-1111111" ids)))

    (it "takes the hex part"
      (should= [:ok "add-logout-2222222"] (record/resolve "2222222" ids)))

    (it "takes a unique prefix"
      (should= [:ok "add-login-1111111"] (record/resolve "add-logi" ids)))

    (it "names every match of an ambiguous prefix, sorted"
      (should= [:ambiguous ["add-login-1111111" "add-logout-2222222"]]
               (record/resolve "add-log" (reverse ids))))

    (it "finds nothing"
      (should= [:none] (record/resolve "nothing" ids)))

    (it "prefers the full id over a longer id it prefixes"
      (should= [:ok "a-1111111"] (record/resolve "a-1111111" ["a-1111111" "a-1111111-2222222"])))))

(describe "free-text"
  (it "joins arguments with spaces and trims both ends"
    (should= "Which hash?" (record/free-text [" Which" "hash? "])))

  (it "keeps the newlines of one argument"
    (should= "a\n\nb" (record/free-text ["a\n\nb\n"]))))

(def ^:private question
  {:task "t-1" :record "question" :from "coder" :text "Which?\n1) a"
   :stage "coder" :route "coder,qa" :needs "operator"})

(describe "message"
  (it "is the subject, the free text and the snapshot trailers"
    (should= (str "sf question: t-1\n\nWhich?\n1) a\n\n"
                  "SwarmForge-Task: t-1\nSwarmForge-Record: question\nSwarmForge-From: coder\n"
                  "SwarmForge-Stage: coder\nSwarmForge-Route: coder,qa\nSwarmForge-Needs: operator\n")
             (record/message question)))

  (it "has no text paragraph when the record has none"
    (should= (str "sf approval: t-1\n\n"
                  "SwarmForge-Task: t-1\nSwarmForge-Record: approval\nSwarmForge-From: operator\n"
                  "SwarmForge-Stage: coder\nSwarmForge-Route: coder\nSwarmForge-Needs: none\n")
             (record/message {:task "t-1" :record "approval" :from "operator"
                              :stage "coder" :route "coder" :needs "none"})))

  (it "ends with Merged-Into for the finished record"
    (should= (str "sf finished: t-1\n\n"
                  "SwarmForge-Task: t-1\nSwarmForge-Record: finished\nSwarmForge-From: operator\n"
                  "SwarmForge-Stage: done\nSwarmForge-Route: coder\nSwarmForge-Needs: none\n"
                  "SwarmForge-Merged-Into: main\n")
             (record/message {:task "t-1" :record "finished" :from "operator" :stage "done"
                              :route "coder" :needs "none" :merged-into "main"}))))

(describe "parse"
  (it "reads a record commit back"
    (should= (assoc question :sha "abc1234" :commit "par1234")
             (record/parse {:sha "abc1234" :parents ["par1234"] :message (record/message question)
                            :trailers (drop 4 (clojure.string/split-lines (record/message question)))})))

  (it "takes the commit of a created record as its own"
    (should= "abc1234" (:commit (record/parse {:sha "abc1234" :parents ["p"] :message "sf created: t\n\nT\n\nSwarmForge-Record: created\n"
                                               :trailers ["SwarmForge-Record: created"]}))))

  (it "keeps a value with a colon"
    (should= "a: b" (:route (record/parse {:sha "s" :parents [] :message "" :trailers ["SwarmForge-Route: a: b"]})))))

(describe "role-commits"
  (it "takes the commit of a record made on another record from that record"
    (should= ["c0" "c0" "r1" "r1" "r1"]
             (map :commit (record/role-commits [{:record "created" :sha "c0" :commit "c0"}
                                                {:record "question" :sha "q1" :commit "c0"}
                                                {:record "review" :sha "v1" :commit "r1"}
                                                {:record "approval" :sha "a1" :commit "v1"}
                                                {:record "handoff" :sha "h1" :commit "a1"}])))))

(describe "with-files"
  (it "lists the paths since the commit of the previous created, review or handoff record"
    (let [diffs {["c0" "c1"] ["a.clj"] ["c1" "c2"] []}]
      (should= [nil nil "a.clj" "-"]
               (map :files (record/with-files [{:record "created" :commit "c0"}
                                               {:record "question"}
                                               {:record "review" :commit "c1"}
                                               {:record "handoff" :commit "c2"}]
                                              (fn [from to] (diffs [from to])))))))

  (it "lists at most 40 paths"
    (should= (str (clojure.string/join "," (map str (range 40))) ",+2 more")
             (record/files-value (map str (range 42))))))

(describe "block"
  (it "renders the keys of the record type, later lines of a value indented"
    (should= "type: question\nfrom: coder\nquestion: Which?\n  1) a" (record/block question)))

  (it "renders a handoff"
    (should= "type: handoff\nfrom: coder\nto: qa\nbranch: swarm/t-1\ncommit: c1\nfiles: -\nsummary: done"
             (record/block {:task "t-1" :record "handoff" :from "coder" :stage "qa" :commit "c1"
                            :files "-" :text "done"})))

  (it "renders a created record"
    (should= "type: created\nfrom: operator\nbranch: swarm/t-1\ncommit: c0\nroute: coder"
             (record/block {:task "t-1" :record "created" :from "operator" :commit "c0" :route "coder"})))

  (it "renders a finished record"
    (should= "type: finished\nfrom: operator\nmerged-into: main"
             (record/block {:record "finished" :from "operator" :merged-into "main"})))

  (it "renders an answer, a change request and a route change"
    (should= "type: answer\nfrom: operator\nanswer: yes"
             (record/block {:record "answer" :from "operator" :text "yes"}))
    (should= "type: changes-requested\nfrom: operator\nreason: vague"
             (record/block {:record "changes-requested" :from "operator" :text "vague"}))
    (should= "type: route\nfrom: operator\nroute: coder,qa"
             (record/block {:record "route" :from "operator" :route "coder,qa"})))

  (it "renders only the type and the sender of a record without fields"
    (should= "type: approval\nfrom: operator"
             (record/block {:record "approval" :from "operator"}))))

(describe "task file"
  (let [text (record/task-file {:id "t-1" :date "2026-10-27" :title "Add login" :request "Users log in.\n"})]
    (it "is the front matter, the title heading and the request"
      (should= "---\nid: t-1\ncreated: 2026-10-27\n---\n\n# Add login\n\nUsers log in.\n" text))

    (it "reads back"
      (should= {:front {"id" "t-1" "created" "2026-10-27"} :title "Add login" :request "Users log in." :blocks []}
               (record/read-task-file text)))

    (context "archive"
      (let [archived (record/archive text "coder" ["type: created\nfrom: operator" "type: handoff\nfrom: coder"])]
        (it "adds the stage, the route and the fenced records"
          (should= (str "---\nid: t-1\ncreated: 2026-10-27\nstage: done\nroute: coder\n---\n\n# Add login\n\nUsers log in.\n\n"
                        "## Records\n\n```text\ntype: created\nfrom: operator\n```\n\n```text\ntype: handoff\nfrom: coder\n```\n")
                   archived))

        (it "reads back with the records unfenced"
          (should= {:front {"id" "t-1" "created" "2026-10-27" "stage" "done" "route" "coder"}
                    :title "Add login" :request "Users log in."
                    :blocks ["type: created\nfrom: operator" "type: handoff\nfrom: coder"]}
                   (record/read-task-file archived)))))))

(describe "record-trailer"
  (it "is the first trailer key that starts with SwarmForge- in any case"
    (should= "swarmforge-needs" (record/record-trailer ["Tested" "swarmforge-needs" "SwarmForge-Task"])))

  (it "is nil when there is none"
    (should= nil (record/record-trailer ["Tested" "SwarmForgeX"]))))
