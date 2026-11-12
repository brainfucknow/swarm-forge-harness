(ns sf.rounds-spec
  (:require [speclj.core :refer [describe context it should= should should-not should-be-nil]]
            [sf.rounds :as rounds]))

(defn- rec [record from & {:as more}]
  (merge {:record record :from from :stage "specifier" :needs "none"} more))

(def ^:private created (rec "created" "operator" :sha "c0" :commit "c0"))
(defn- review [sha] (rec "review" "specifier" :sha (str "v" sha) :commit sha :reviewed (str sha "full") :needs "approval"))
(defn- continued [text] (rec "changes-requested" "judge" :verdict "continue" :text text))
(defn- verdict [v text] (rec "verdict" "judge" :verdict v :text text :needs "approval"))
(defn- rejected [text] (rec "changes-requested" "operator" :text text))

(describe "rounds"
  (it "is empty without a specifier review"
    (should= [] (rounds/rounds [created])))

  (it "numbers each specifier review from 1, with the verdict and changes-requested records after it"
    (let [rs (rounds/rounds [created (review "s1") (continued "r1") (review "s2")
                             (verdict "stuck" "cap") (rejected "no") (review "s3")])]
      (should= [1 2 3] (map :n rs))
      (should= ["s1full" "s2full" "s3full"] (map :reviewed rs))
      (should= [["r1"] ["cap" "no"] []] (map #(map :text (:records %)) rs))
      (should= ["continue" "stuck" nil] (map rounds/verdict-of rs))))

  (it "names the commit of a review made before sf named it"
    (should= ["s1"] (map :reviewed (rounds/rounds [created (dissoc (review "s1") :reviewed)]))))

  (it "is not opened by the review of another stage"
    (let [rs (rounds/rounds [created (review "s1") (assoc (review "s2") :stage "coder")])]
      (should= [1] (map :n rs))))

  (it "keeps question and answer records out of a round"
    (should= [[]] (map :records (rounds/rounds [created (review "s1") (rec "question" "specifier" :text "q")]))))

  (context "baseline"
    (it "is round 1 without an operator send-back"
      (should= [1 1 1] (map :baseline (rounds/rounds [created (review "a") (continued "x") (review "b") (continued "y") (review "c")]))))

    (it "is the first round after the newest operator send-back before the round"
      (should= [1 1 3 3 5]
               (map :baseline (rounds/rounds [created (review "a") (continued "x") (review "b") (rejected "no")
                                              (review "c") (continued "y") (review "d") (rejected "again") (review "e")]))))

    (it "is not reset by an automatic send-back"
      (should= [1 1] (map :baseline (rounds/rounds [created (review "a") (continued "x") (review "b")]))))))

(describe "automatic send-backs"
  (it "counts only the changes-requested records with verdict continue"
    (should= 2 (rounds/automatic-count [created (review "a") (continued "x") (review "b") (rejected "no")
                                        (review "c") (continued "y") (review "d") (verdict "stuck" "z")]))))

(defn- commit [sha message & {:keys [parents trailers numstat] :or {parents ["p"] trailers [] numstat []}}]
  {:sha sha :parents parents :message message :trailers trailers :numstat numstat})

(describe "size"
  (it "counts nonblank message lines without the trailer block"
    (should= 3 (rounds/size [(commit "a" "Spec\n\nline one\n\nBy specifier.\n\nLore-id: 1\nConstraint: x\n"
                                     :trailers ["Lore-id: 1" "Constraint: x"])])))

  (it "counts the whole message when git reads no trailer"
    (should= 3 (rounds/size [(commit "a" "Spec\n\nline one\nBy specifier.\n")])))

  (it "leaves out the message of a commit another counted commit supersedes, not its tracked lines"
    (should= (+ 2 3 4)
             (rounds/size [(commit "a" "Old\nlonger\nstill\n\nLore-id: aaaa\n" :trailers ["Lore-id: aaaa"] :numstat [["4" "0"]])
                           (commit "b" "New\nbody\n\nLore-id: bbbb\nSupersedes: aaaa\n"
                                   :trailers ["Lore-id: bbbb" "Supersedes: aaaa"])
                           (commit "c" "Third\nx\ny\n")])))

  (it "counts both messages when a rework does not supersede"
    (should= 4 (rounds/size [(commit "a" "One\ntwo\n") (commit "b" "Three\nfour\n")])))

  (it "adds lines added minus lines removed, - as 0, and never below 0"
    (should= (+ 1 7) (rounds/size [(commit "a" "Add" :numstat [["10" "2"] ["-" "-"] ["0" "1"]])]))
    (should= (+ 2 0) (rounds/size [(commit "a" "Add" :numstat [["3" "0"]]) (commit "b" "Drop" :numstat [["0" "5"]])])))

  (it "leaves out record commits and merges"
    (should= 1 (rounds/size [(commit "a" "Spec")
                             (commit "r" "sf review: t\n\nready\n\nSwarmForge-Task: t\nSwarmForge-Record: review\n"
                                     :trailers ["SwarmForge-Task: t" "SwarmForge-Record: review"])
                             (commit "m" "Merge main\nmore\n" :parents ["p" "q"] :numstat [["300" "0"]])]))))

(describe "read-log"
  (it "reads the commits and numstat lines of git log --numstat with log-format"
    (should= [{:sha "abc" :parents ["p1"] :message "Spec\n\nBody\n\nLore-id: 1\n" :trailers ["Lore-id: 1"]
               :numstat [["3" "1"] ["-" "-"]]}
              {:sha "def" :parents ["p1" "p2"] :message "Merge\n" :trailers [] :numstat []}]
             (rounds/read-log (str "\u001eabc\u001fp1\u001fSpec\n\nBody\n\nLore-id: 1\n\u001fLore-id: 1\n\u001f\n\n3\t1\ta.md\n-\t-\tbin.png\n"
                                   "\u001edef\u001fp1 p2\u001fMerge\n\u001f\u001f\n")))))

(describe "growth"
  (it "is the rounded-down percentage over the baseline, - when it is 0"
    (should= "50%" (rounds/growth 150 100))
    (should= "50%" (rounds/growth 301 200))
    (should= "-34%" (rounds/growth 66 100))
    (should= "0%" (rounds/growth 7 7))
    (should= "-" (rounds/growth 5 0))))

(describe "over-limit?"
  (it "holds when the size is more than half over a baseline that is not 0"
    (should (rounds/over-limit? 301 200))
    (should-not (rounds/over-limit? 300 200))
    (should (rounds/over-limit? 16 10))
    (should-not (rounds/over-limit? 15 10))
    (should-not (rounds/over-limit? 100 0))))

(describe "limits"
  (it "lists the cap, then the growth limit, for the newest round"
    (should= ["LIMIT: cap 2 of 2 automatic send-backs used" "LIMIT: growth size 16 is more than half over 10"]
             (rounds/limits 2 16 10))
    (should= ["LIMIT: cap 2 of 2 automatic send-backs used"] (rounds/limits 3 15 10))
    (should= ["LIMIT: growth size 16 is more than half over 10"] (rounds/limits 1 16 10))
    (should= [] (rounds/limits 1 15 10))))

(describe "report"
  (it "prints NO_ROUNDS without a round"
    (should= ["NO_ROUNDS"] (rounds/report [] [])))

  (it "prints a ROUND line per round, its records' texts, then the limit lines"
    (should= ["ROUND 1 abc1234 size:10 growth:0% verdict:continue"
              "  judge: r1 first"
              "    second"
              ""
              "    fourth"
              "ROUND 2 def5678 size:16 growth:60% verdict:-"
              "LIMIT: growth size 16 is more than half over 10"]
             (rounds/report [{:n 1 :short "abc1234" :size 10 :growth "0%"
                              :records [(continued "r1 first\nsecond\n\nfourth")]}
                             {:n 2 :short "def5678" :size 16 :growth "60%" :records []}]
                            ["LIMIT: growth size 16 is more than half over 10"])))

  (it "prints an undefined growth as - without %"
    (should= ["ROUND 1 abc1234 size:0 growth:- verdict:stuck" "  judge: x"]
             (rounds/report [{:n 1 :short "abc1234" :size 0 :growth "-" :records [(verdict "stuck" "x")]}] [])))

  (it "prints an operator's send-back under its round"
    (should= ["ROUND 1 abc1234 size:1 growth:0% verdict:-" "  operator: no"]
             (rounds/report [{:n 1 :short "abc1234" :size 1 :growth "0%" :records [(rejected "no")]}] []))))

(def ^:private ok
  {:state {:stage "specifier" :needs "approval"} :rounds (rounds/rounds [created (review "a")])
   :verdict "converged" :text "fine" :judge "judge"})

(describe "verdict-refusal"
  (it "allows a verdict on a round without one"
    (should-be-nil (rounds/verdict-refusal ok))
    (doseq [v ["converged" "continue" "needs-operator" "stuck"]]
      (should-be-nil (rounds/verdict-refusal (assoc ok :verdict v)))))

  (it "refuses a verdict that is not one of the four"
    (should= "verdict must be converged, continue, needs-operator or stuck, not 'approve'"
             (rounds/verdict-refusal (assoc ok :verdict "approve")))
    (should= "verdict must be converged, continue, needs-operator or stuck, not ''"
             (rounds/verdict-refusal (assoc ok :verdict nil))))

  (it "refuses a blank text"
    (should= "verdict needs a reason" (rounds/verdict-refusal (assoc ok :text " "))))

  (it "refuses without a judge in roles.conf"
    (should= "roles.conf has no judge; add `<role> <tier> subagent judge` to record verdicts"
             (rounds/verdict-refusal (assoc ok :judge nil))))

  (it "refuses at another stage"
    (should= "task is at stage:coder; verdicts are recorded at stage:specifier"
             (rounds/verdict-refusal (assoc-in ok [:state :stage] "coder"))))

  (it "refuses a task not waiting for approval"
    (should= "task is not waiting for approval" (rounds/verdict-refusal (assoc-in ok [:state :needs] "none"))))

  (it "refuses a second verdict in the round"
    (should= "round 1 already has the verdict stuck"
             (rounds/verdict-refusal (assoc ok :rounds (rounds/rounds [created (review "a") (verdict "stuck" "x")]))))))
