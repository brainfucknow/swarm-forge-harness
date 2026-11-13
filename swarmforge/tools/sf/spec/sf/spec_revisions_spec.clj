(ns sf.spec-revisions-spec
  (:require [speclj.core :refer [describe context it should= should should-not should-be-nil]]
            [sf.spec-revisions :as spec-revisions]))

(defn- rec [record from & {:as more}]
  (merge {:record record :from from :stage "specifier" :needs "none"} more))

(def ^:private created (rec "created" "operator" :sha "c0" :commit "c0"))
(defn- review [sha] (rec "review" "specifier" :sha (str "v" sha) :commit sha :reviewed (str sha "full") :needs "approval"))
(defn- continued [text] (rec "changes-requested" "spec-judge" :spec-verdict "continue" :text text))
(defn- spec-verdict [v text] (rec "spec-verdict" "spec-judge" :spec-verdict v :text text :needs "approval"))
(defn- rejected [text] (rec "changes-requested" "operator" :text text))

(describe "spec revisions"
  (it "is empty without a specifier review"
    (should= [] (spec-revisions/spec-revisions [created])))

  (it "numbers each specifier review from 1, with the spec verdict and changes-requested records after it"
    (let [rs (spec-revisions/spec-revisions [created (review "s1") (continued "r1") (review "s2")
                             (spec-verdict "stuck" "cap") (rejected "no") (review "s3")])]
      (should= [1 2 3] (map :n rs))
      (should= ["s1full" "s2full" "s3full"] (map :reviewed rs))
      (should= [["r1"] ["cap" "no"] []] (map #(map :text (:records %)) rs))
      (should= ["continue" "stuck" nil] (map spec-revisions/spec-verdict-of rs))))

  (it "names the commit of a review made before sf named it"
    (should= ["s1"] (map :reviewed (spec-revisions/spec-revisions [created (dissoc (review "s1") :reviewed)]))))

  (it "is not opened by the review of another stage"
    (let [rs (spec-revisions/spec-revisions [created (review "s1") (assoc (review "s2") :stage "coder")])]
      (should= [1] (map :n rs))))

  (it "keeps question and answer records out of a spec revision"
    (should= [[]] (map :records (spec-revisions/spec-revisions [created (review "s1") (rec "question" "specifier" :text "q")]))))

  (context "baseline"
    (it "is spec revision 1 without an operator send-back"
      (should= [1 1 1] (map :baseline (spec-revisions/spec-revisions [created (review "a") (continued "x") (review "b") (continued "y") (review "c")]))))

    (it "is the first spec revision after the newest operator send-back before the spec revision"
      (should= [1 1 3 3 5]
               (map :baseline (spec-revisions/spec-revisions [created (review "a") (continued "x") (review "b") (rejected "no")
                                              (review "c") (continued "y") (review "d") (rejected "again") (review "e")]))))

    (it "is not reset by an automatic send-back"
      (should= [1 1] (map :baseline (spec-revisions/spec-revisions [created (review "a") (continued "x") (review "b")]))))))

(describe "automatic send-backs"
  (it "counts only the changes-requested records with spec verdict continue"
    (should= 2 (spec-revisions/automatic-count [created (review "a") (continued "x") (review "b") (rejected "no")
                                        (review "c") (continued "y") (review "d") (spec-verdict "stuck" "z")]))))

(defn- commit [sha message & {:keys [parents trailers numstat] :or {parents ["p"] trailers [] numstat []}}]
  {:sha sha :parents parents :message message :trailers trailers :numstat numstat})

(describe "size"
  (it "counts nonblank message lines without the trailer block"
    (should= 3 (spec-revisions/size [(commit "a" "Spec\n\nline one\n\nBy specifier.\n\nLore-id: 1\nConstraint: x\n"
                                     :trailers ["Lore-id: 1" "Constraint: x"])])))

  (it "counts the whole message when git reads no trailer"
    (should= 3 (spec-revisions/size [(commit "a" "Spec\n\nline one\nBy specifier.\n")])))

  (it "leaves out the message of a commit another counted commit supersedes, not its tracked lines"
    (should= (+ 2 3 4)
             (spec-revisions/size [(commit "a" "Old\nlonger\nstill\n\nLore-id: aaaa\n" :trailers ["Lore-id: aaaa"] :numstat [["4" "0"]])
                           (commit "b" "New\nbody\n\nLore-id: bbbb\nSupersedes: aaaa\n"
                                   :trailers ["Lore-id: bbbb" "Supersedes: aaaa"])
                           (commit "c" "Third\nx\ny\n")])))

  (it "counts both messages when a rework does not supersede"
    (should= 4 (spec-revisions/size [(commit "a" "One\ntwo\n") (commit "b" "Three\nfour\n")])))

  (it "adds lines added minus lines removed, - as 0, and never below 0"
    (should= (+ 1 7) (spec-revisions/size [(commit "a" "Add" :numstat [["10" "2"] ["-" "-"] ["0" "1"]])]))
    (should= (+ 2 0) (spec-revisions/size [(commit "a" "Add" :numstat [["3" "0"]]) (commit "b" "Drop" :numstat [["0" "5"]])])))

  (it "leaves out record commits and merges"
    (should= 1 (spec-revisions/size [(commit "a" "Spec")
                             (commit "r" "sf review: t\n\nready\n\nSwarmForge-Task: t\nSwarmForge-Record: review\n"
                                     :trailers ["SwarmForge-Task: t" "SwarmForge-Record: review"])
                             (commit "m" "Merge main\nmore\n" :parents ["p" "q"] :numstat [["300" "0"]])]))))

(describe "read-log"
  (it "reads the commits and numstat lines of git log --numstat with log-format"
    (should= [{:sha "abc" :parents ["p1"] :message "Spec\n\nBody\n\nLore-id: 1\n" :trailers ["Lore-id: 1"]
               :numstat [["3" "1"] ["-" "-"]]}
              {:sha "def" :parents ["p1" "p2"] :message "Merge\n" :trailers [] :numstat []}]
             (spec-revisions/read-log (str "\u001eabc\u001fp1\u001fSpec\n\nBody\n\nLore-id: 1\n\u001fLore-id: 1\n\u001f\n\n3\t1\ta.md\n-\t-\tbin.png\n"
                                   "\u001edef\u001fp1 p2\u001fMerge\n\u001f\u001f\n")))))

(describe "growth"
  (it "is the rounded-down percentage over the baseline, - when it is 0"
    (should= "50%" (spec-revisions/growth 150 100))
    (should= "50%" (spec-revisions/growth 301 200))
    (should= "-34%" (spec-revisions/growth 66 100))
    (should= "0%" (spec-revisions/growth 7 7))
    (should= "-" (spec-revisions/growth 5 0))))

(describe "over-limit?"
  (it "holds when the size is more than half over a baseline that is not 0"
    (should (spec-revisions/over-limit? 301 200))
    (should-not (spec-revisions/over-limit? 300 200))
    (should (spec-revisions/over-limit? 16 10))
    (should-not (spec-revisions/over-limit? 15 10))
    (should-not (spec-revisions/over-limit? 100 0))))

(describe "limits"
  (it "lists the cap, then the growth limit, for the newest spec revision"
    (should= ["LIMIT: cap 2 of 2 automatic send-backs used" "LIMIT: growth size 16 is more than half over 10"]
             (spec-revisions/limits 2 16 10))
    (should= ["LIMIT: cap 2 of 2 automatic send-backs used"] (spec-revisions/limits 3 15 10))
    (should= ["LIMIT: growth size 16 is more than half over 10"] (spec-revisions/limits 1 16 10))
    (should= [] (spec-revisions/limits 1 15 10))))

(defn- sized
  "Spec revisions with the size and baseline of each pair, numbered from 1."
  [& pairs]
  (vec (map-indexed (fn [i [size baseline]] {:n (inc i) :size size :baseline baseline}) pairs)))

(describe "notes"
  (it "prints the total growth over spec revision 1 when the newest spec revision is more than half over it after a reset"
    (should= ["NOTE: total growth 109% over spec revision 1 (11); not a limit"]
             (spec-revisions/notes (sized [11 1] [16 2] [21 3] [23 3])))
    (should= ["NOTE: total growth 128% over spec revision 1 (38); not a limit"]
             (spec-revisions/notes (sized [38 1] [63 2] [87 3]))))

  (it "prints nothing at half over spec revision 1 or less"
    (should= [] (spec-revisions/notes (sized [10 1] [15 2])))
    (should= [] (spec-revisions/notes (sized [11 1] [16 2]))))

  (it "prints nothing when the newest spec revision's baseline is spec revision 1, where LIMIT: growth says it"
    (should= [] (spec-revisions/notes (sized [10 1] [16 1] [30 1]))))

  (it "prints nothing when spec revision 1 has size 0"
    (should= [] (spec-revisions/notes (sized [0 1] [16 2]))))

  (it "prints nothing without a spec revision"
    (should= [] (spec-revisions/notes []))))

(def ^:private found
  [{:n 1 :short "abc1234" :size 10 :growth "0%" :total "0%"
    :records [(continued "r1 first\nsecond\n\nfourth")]}
   {:n 2 :short "def5678" :size 16 :growth "60%" :total "60%" :records []}])

(def ^:private tail ["LIMIT: growth size 16 is more than half over 10" "NOTE: x"])

(describe "report"
  (it "prints NO_SPEC_REVISIONS without a spec revision"
    (should= ["NO_SPEC_REVISIONS"] (spec-revisions/report [] [] false))
    (should= ["NO_SPEC_REVISIONS"] (spec-revisions/report [] [] true)))

  (it "prints a SPEC-REVISION line per spec revision with its total, then the tail lines, and no record text"
    (should= ["SPEC-REVISION 1 abc1234 size:10 growth:0% total:0% spec-verdict:continue"
              "SPEC-REVISION 2 def5678 size:16 growth:60% total:60% spec-verdict:-"
              "LIMIT: growth size 16 is more than half over 10"
              "NOTE: x"]
             (spec-revisions/report found tail false)))

  (it "prints each spec revision's records' texts under its line when full"
    (should= ["SPEC-REVISION 1 abc1234 size:10 growth:0% total:0% spec-verdict:continue"
              "  spec-judge: r1 first"
              "    second"
              ""
              "    fourth"
              "SPEC-REVISION 2 def5678 size:16 growth:60% total:60% spec-verdict:-"
              "LIMIT: growth size 16 is more than half over 10"
              "NOTE: x"]
             (spec-revisions/report found tail true)))

  (it "prints an undefined growth and total as - without %"
    (should= ["SPEC-REVISION 1 abc1234 size:0 growth:- total:- spec-verdict:stuck" "  spec-judge: x"]
             (spec-revisions/report [{:n 1 :short "abc1234" :size 0 :growth "-" :total "-" :records [(spec-verdict "stuck" "x")]}] [] true)))

  (it "prints an operator's send-back under its spec revision when full"
    (should= ["SPEC-REVISION 1 abc1234 size:1 growth:0% total:0% spec-verdict:-" "  operator: no"]
             (spec-revisions/report [{:n 1 :short "abc1234" :size 1 :growth "0%" :total "0%" :records [(rejected "no")]}] [] true))))

(def ^:private ok
  {:state {:stage "specifier" :needs "approval"} :revisions (spec-revisions/spec-revisions [created (review "a")])
   :spec-verdict "converged" :text "fine" :spec-judge "spec-judge"})

(describe "spec-verdict-refusal"
  (it "allows a spec verdict on a spec revision without one"
    (should-be-nil (spec-revisions/spec-verdict-refusal ok))
    (doseq [v ["converged" "continue" "needs-operator" "stuck"]]
      (should-be-nil (spec-revisions/spec-verdict-refusal (assoc ok :spec-verdict v)))))

  (it "refuses a spec verdict that is not one of the four"
    (should= "spec verdict must be converged, continue, needs-operator or stuck, not 'approve'"
             (spec-revisions/spec-verdict-refusal (assoc ok :spec-verdict "approve")))
    (should= "spec verdict must be converged, continue, needs-operator or stuck, not ''"
             (spec-revisions/spec-verdict-refusal (assoc ok :spec-verdict nil))))

  (it "refuses a blank text"
    (should= "spec verdict needs a reason" (spec-revisions/spec-verdict-refusal (assoc ok :text " "))))

  (it "refuses without a spec-judge in roles.conf"
    (should= "roles.conf has no spec-judge; add `<role> <tier> subagent spec-judge` to record spec verdicts"
             (spec-revisions/spec-verdict-refusal (assoc ok :spec-judge nil))))

  (it "refuses at another stage"
    (should= "task is at stage:coder; spec verdicts are recorded at stage:specifier"
             (spec-revisions/spec-verdict-refusal (assoc-in ok [:state :stage] "coder"))))

  (it "refuses a task not waiting for approval"
    (should= "task is not waiting for approval" (spec-revisions/spec-verdict-refusal (assoc-in ok [:state :needs] "none"))))

  (it "refuses a second spec verdict in the spec revision"
    (should= "spec revision 1 already has the spec verdict stuck"
             (spec-revisions/spec-verdict-refusal (assoc ok :revisions (spec-revisions/spec-revisions [created (review "a") (spec-verdict "stuck" "x")]))))))
