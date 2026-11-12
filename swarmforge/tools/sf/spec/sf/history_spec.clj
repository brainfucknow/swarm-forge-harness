(ns sf.history-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [describe it should= should-be-nil around]]
            [sf.shell :as shell]
            [sf.history :as history]
            [sf.flow :as flow]
            [sf.lore]
            [sf.record :as record]
            [sf.store :as store]))

(def ^:dynamic *dir* nil)

(defn- git [dir & args]
  (shell/chomp (apply shell/must {:dir dir} "git" "-c" "user.name=t" "-c" "user.email=t@x" args)))

(defn- with-repo [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "history-spec-"}))]
    (try
      (git dir "init" "-q" "-b" "main")
      (git dir "commit" "-q" "--allow-empty" "-m" "Base\n\nTested: base")
      (with-redefs [shell/repo-root (constantly dir)]
        (binding [*dir* dir] (run-spec)))
      (finally (fs/delete-tree dir)))))

(def ^:private lore-commits @#'sf.history/lore-commits)

(defn- lore-lines [rev records]
  (some-> (lore-commits rev records) sf.lore/lines))

(describe "lore-commits"
  (around [it] (with-repo it))

  (it "prints the LORE: lines of the first-parent commits after the created record"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")
          _ (git *dir* "commit" "-q" "--allow-empty" "-m" "Code\n\nBy coder.\n\nConstraint: keep it\n")
          head (git *dir* "rev-parse" "--short" "HEAD")]
      (should= [(str "LORE: " head " coder Constraint: keep it")]
               (lore-lines "HEAD" [{:record "created" :commit base}]))))

  (it "leaves out the commits that a merge brings in"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")]
      (git *dir* "checkout" "-q" "-b" "other")
      (git *dir* "commit" "-q" "--allow-empty" "-m" "Other\n\nTested: other\n")
      (git *dir* "checkout" "-q" "main")
      (git *dir* "merge" "-q" "--no-ff" "--no-edit" "other")
      (should= [] (lore-lines "HEAD" [{:record "created" :commit base}]))))

  (it "is nil when there is no created record"
    (should-be-nil (lore-lines "HEAD" [{:record "question"}]))))

(defn- finish-through-second-parent!
  "Task t-1 in dir, finished into the branch pr, which main then merges: its
  finished record is reached only through HEAD's second parent."
  [dir]
  (store/create! "t-1" (record/message (assoc (flow/created "Add login" "Users log in." "coder") :task "t-1")))
  (git dir "checkout" "-q" "-b" "pr")
  (git dir "commit" "-q" "--allow-empty" "-m" "Code\n\nBy coder.\n\nTested: it\n")
  (git dir "merge" "-q" "--no-ff" "-m" (record/message {:task "t-1" :record "finished" :from "operator" :stage "done"
                                                         :route "coder" :needs "none" :merged-into "pr"})
       "swarm/t-1")
  (git dir "branch" "-q" "-D" "swarm/t-1")
  (git dir "checkout" "-q" "main")
  (git dir "merge" "-q" "--no-ff" "--no-edit" "pr"))

(describe "view of a finished task"
  (around [it] (with-repo it))

  (it "reads the finished record through a second parent and the records of the merged branch"
    (finish-through-second-parent! *dir*)
    (let [{:keys [status state title request records]} (history/view "t-1")]
      (should= ["finished" "done" "Add login" "Users log in."] [status (:stage state) title request])
      (should= [["created" nil] ["finished" "pr"]] (map (juxt :record :merged-into) records)))))

(defn- view [records & commits]
  {:request "Users log in." :records records :commits commits})

(def ^:private routes (repeat 11 {:record "route" :from "operator" :route "a,b"}))

(def ^:private superseding
  [{:sha "c1" :message "S\n\nBy specifier.\n" :trailers ["Lore-id: 1" "Directive: one" "Tested: t"]}
   {:sha "c2" :message "C\n\nBy coder.\n" :trailers ["Lore-id: 2" "Directive: two" "Supersedes: 1"]}])

(describe "print-brief"
  (it "leads the records with a RECORDS line that counts the records left out"
    (should= (str "---\nUsers log in.\n\nRECORDS: 1 earlier records omitted; sf show t-1 prints them\n\n"
                  (str/join "\n\n" (repeat 10 "type: route\nfrom: operator\nroute: a,b")) "\n")
             (with-out-str (history/print-brief "t-1" (view routes)))))

  (it "leads the LORE: lines with a LORE-OMITTED line that counts the lines left out"
    (should= (str "---\nUsers log in.\n\nLORE-OMITTED: 4 superseded or evidence lines; sf show t-1 prints them\n"
                  "LORE: c2 coder Directive: two\nLORE: c2 coder Supersedes: 1\n")
             (with-out-str (history/print-brief "t-1" (apply view [] superseding)))))

  (it "prints no omission line when it leaves nothing out"
    (should= "---\nUsers log in.\n\ntype: question\nfrom: qa\nquestion: q\n\nLORE: c1 qa Directive: d\n"
             (with-out-str (history/print-brief "t-1" (view [{:record "question" :from "qa" :text "q"}]
                                                            {:sha "c1" :message "M\n\nBy qa.\n" :trailers ["Directive: d"]}))))))

(describe "print-body"
  (it "prints every record and every LORE: line"
    (let [out (with-out-str (history/print-body (apply view routes superseding)))]
      (should= 11 (count (re-seq #"type: route" out)))
      (should= 6 (count (re-seq #"(?m)^LORE: " out)))
      (should-be-nil (re-find #"RECORDS|LORE-OMITTED" out)))))

(defn- harvested []
  (let [err (java.io.StringWriter.)
        tasks (binding [*err* err] (doall (history/harvest)))]
    {:tasks tasks :err (str err)}))

(describe "harvest"
  (around [it] (with-repo it))

  (it "reads the finished tasks first, then the open ones by id"
    (store/create! "t-9" (record/message (assoc (flow/created "Later" "Open request." "coder") :task "t-9")))
    (finish-through-second-parent! *dir*)
    (store/create! "t-0" (record/message (assoc (flow/created "Open" "Another." "coder") :task "t-0")))
    (let [{:keys [tasks err]} (harvested)]
      (should= [["t-1" "finished" false "Add login" "Users log in."]
                ["t-0" "open" false "Open" "Another."]
                ["t-9" "open" false "Later" "Open request."]]
               (map (juxt :id :status :truncated :title :request) tasks))
      (should= [["created" "finished"] ["created"] ["created"]]
               (map #(map :record (:records %)) tasks))
      (should= [] (:commits (first tasks)))
      (should= "" err)))

  (it "skips a swarm branch without records and names it on stderr"
    (store/create! "t-0" (record/message (assoc (flow/created "Open" "Another." "coder") :task "t-0")))
    (git *dir* "branch" "swarm/experiment")
    (let [{:keys [tasks err]} (harvested)]
      (should= ["t-0"] (map :id tasks))
      (should= "sf: swarm/experiment has no records; skipped\n" err)))

  (it "reads a branch whose history has no created record as truncated, Lore after its oldest record"
    (git *dir* "checkout" "-q" "-b" "swarm/t-2")
    (git *dir* "commit" "-q" "--allow-empty" "-m"
         (record/message {:task "t-2" :record "handoff" :from "coder" :stage "qa" :route "coder,qa" :needs "none"
                          :text "done"}))
    (git *dir* "commit" "-q" "--allow-empty" "-m" "QA\n\nBy qa.\n\nTested: it\n")
    (git *dir* "checkout" "-q" "main")
    (let [[task] (:tasks (harvested))]
      (should= ["t-2" "open" true "" ""] ((juxt :id :status :truncated :title :request) task))
      (should= ["handoff"] (map :record (:records task)))
      (should= [["Tested: it"]] (map :trailers (:commits task))))))
