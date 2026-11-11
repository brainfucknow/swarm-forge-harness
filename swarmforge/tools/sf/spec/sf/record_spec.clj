(ns sf.record-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [sf.record :as record]))

(describe "slug"
  (it "lowercases the title and joins its words with -"
    (should= "add-login" (record/slug "Add login")))

  (it "makes each run of other characters one - and trims - at both ends"
    (should= "fix-the-api-s-2-bugs" (record/slug "Fix: the API's 2 bugs!")))

  (it "writes å as aa, ä and æ as ae, ö and ø as oe"
    (should= "aendra-inloggningen-foer" (record/slug "Ändra inloggningen för användare"))
    (should= "blaabaersyltetoey" (record/slug "Blåbærsyltetøy"))
    (should= "oel-og-aeble-paa-oen" (record/slug "ØL OG ÆBLE PÅ ÖN")))

  (it "drops the marks of other accented letters"
    (should= "creme-brulee" (record/slug "Crème brûlée"))
    (should= "tieng-viet" (record/slug "Tiếng Việt"))
    (should= "pinyin-a-u" (record/slug "Pīnyīn ǎ ǜ")))

  (it "makes letters with no decomposition one -"
    (should= "odz-stra-e" (record/slug "Łódź Straße")))

  (it "drops a combining mark after a plain letter"
    (should= "andra" (record/slug "A\u0308ndra")))

  (it "keeps a slug of 30 characters"
    (should= "twelve-chars-one-two-three-fou" (record/slug "twelve chars one two three fou")))

  (it "cuts a longer slug just before a -"
    (should= "twelve-chars-one-two-three" (record/slug "twelve chars one two three four")))

  (it "cuts a first word longer than 30 characters at 30"
    (should= "supercalifragilisticexpialidoc" (record/slug "Supercalifragilisticexpialidocious word")))

  (it "is task when nothing is left"
    (should= "task" (record/slug "!!!"))))

(def ^:private ids ["add-login-1111111" "add-logout-2222222"])

(describe "resolve"
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
    (should= [:ok "a-1111111"] (record/resolve "a-1111111" ["a-1111111" "a-1111111-2222222"]))))

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

(describe "a review record"
  (it "names the commit it reviewed in a last trailer and reads it back"
    (let [review {:task "t-1" :record "review" :from "specifier" :stage "specifier" :route "specifier,coder"
                  :needs "approval" :reviewed "0123456789abcdef0123456789abcdef01234567"}
          message (record/message review)]
      (should= "SwarmForge-Reviewed: 0123456789abcdef0123456789abcdef01234567"
               (last (clojure.string/split-lines message)))
      (should= (assoc review :sha "abc1234" :commit "par1234")
               (record/parse {:sha "abc1234" :parents ["par1234"] :message message
                              :trailers (drop 2 (clojure.string/split-lines message))})))))

(describe "same-commit?"
  (it "matches a full sha and its abbreviation either way round"
    (should (record/same-commit? "0123456789abcdef" "0123456"))
    (should (record/same-commit? "0123456" "0123456789abcdef"))
    (should (record/same-commit? "0123456" "0123456")))

  (it "tells different commits and missing ones apart"
    (should-not (record/same-commit? "0123456" "0123457"))
    (should-not (record/same-commit? nil "0123456"))
    (should-not (record/same-commit? "0123456" ""))))

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
             (record/files-value (map str (range 42)))))

  (it "lists exactly 40 paths in full"
    (should= (clojure.string/join "," (map str (range 40)))
             (record/files-value (map str (range 40))))))

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

(defn- created-message [title request]
  (record/message {:task "t-1" :record "created" :from "operator" :text (record/created-text title request)
                   :stage "coder" :route "coder" :needs "none"}))

(describe "title and request"
  (it "go in the created message as the title, a blank line and the request"
    (should= (str "sf created: t-1\n\nAdd login\n\nUsers log in.\n\nWith email.\n\n"
                  "SwarmForge-Task: t-1\nSwarmForge-Record: created\nSwarmForge-From: operator\n"
                  "SwarmForge-Stage: coder\nSwarmForge-Route: coder\nSwarmForge-Needs: none\n")
             (created-message "Add login" "Users log in.\n\nWith email.\n")))

  (it "read back from the parsed created record, blank lines in the request kept"
    (let [message (created-message "Add login" "Users log in.\n\n\nWith email.")]
      (should= {:title "Add login" :request "Users log in.\n\n\nWith email."}
               (record/title-and-request
                (:text (record/parse {:sha "s" :parents ["p"] :message message
                                      :trailers (take-last 6 (clojure.string/split-lines message))}))))))

  (it "read back from the task file of a task made before sf kept them in the created record"
    (should= {:title "Add login" :request "Users log in.\n\nWith email."}
             (record/read-task-file "---\nid: t-1\ncreated: 2026-10-27\n---\n\n# Add login\n\nUsers log in.\n\nWith email.\n")))

  (it "puts a title on one line"
    (should= "Add login now" (record/one-line " Add\n login \t now\n"))))

(describe "record-trailer"
  (it "is the first trailer key that starts with SwarmForge- in any case"
    (should= "swarmforge-needs" (record/record-trailer ["Tested" "swarmforge-needs" "SwarmForge-Task"])))

  (it "is nil when there is none"
    (should= nil (record/record-trailer ["Tested" "SwarmForgeX"]))))

(describe "new-id"
  (it "is the slug and the first hex that no task has"
    (should= "add-login-3333333"
             (record/new-id "Add login" ["1111111" "2222222"] ["1111111" "2222222" "3333333"]))))

(describe "milestone?"
  (it "is true for created, review and handoff records"
    (should= [true true true false false]
             (map #(record/milestone? {:record %}) ["created" "review" "handoff" "question" "approval"]))))

(defn- records [& kinds]
  (map-indexed (fn [i kind] {:record kind :text (str i)}) kinds))

(describe "brief"
  (it "keeps every record when there are at most limit other records"
    (let [all (records "created" "question" "answer" "route")]
      (should= {:records all :omitted 0} (record/brief all 2))))

  (it "leaves out the earliest other records beyond limit, in record order"
    (let [all (records "created" "route" "review" "handoff")]
      (should= {:records (drop 2 all) :omitted 2} (record/brief all 2))))

  (it "keeps every question, answer and changes-requested record"
    (let [all (vec (records "created" "question" "answer" "review" "changes-requested" "route" "handoff"))]
      (should= {:records [(all 1) (all 2) (all 4) (all 5) (all 6)] :omitted 2}
               (record/brief all 2))))

  (it "tells equal records apart by position"
    (let [route {:record "route" :route "a,b"}]
      (should= {:records [route] :omitted 2} (record/brief [route route route] 1)))))
