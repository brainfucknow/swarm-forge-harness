(ns sf.store-spec
  (:require [speclj.core :refer [around describe it should should-not should=]]
            [sf.flow :as flow]
            [sf.record :as record]
            [sf.repo-support :as repo :refer [*dir* with-repo]]
            [sf.store :as store]))

(describe "with-files"
  (around [it] (with-repo it))

  (it "names the files changed since the previous created, review or handoff commit"
    (let [base (repo/git *dir* "rev-parse" "HEAD")]
      (spit (str *dir* "/a.txt") "a")
      (repo/git *dir* "add" "a.txt")
      (repo/git *dir* "commit" "-q" "-m" "Add a")
      (let [tip (repo/git *dir* "rev-parse" "HEAD")]
        (should= ["-" "a.txt"]
                 (map :files (rest (store/with-files [{:record "created" :commit base}
                                                      {:record "review" :commit base}
                                                      {:record "handoff" :commit tip}]))))))))

(defn- create! [id]
  (store/create! id (record/message (assoc (flow/created "Add login" "Users log in." "coder") :task id))))

(defn- finished-message [id into]
  (record/message {:task id :record "finished" :from "operator" :stage "done"
                   :route "coder" :needs "none" :merged-into into}))

(describe "finished tasks"
  (around [it] (with-repo it))

  (it "are those whose finished record HEAD reaches through any parent"
    (create! "t-1")
    (create! "t-2")
    (repo/git *dir* "checkout" "-q" "-b" "pr")
    (repo/git *dir* "merge" "-q" "--no-ff" "-m" (finished-message "t-1" "pr") "swarm/t-1")
    (repo/git *dir* "checkout" "-q" "main")
    (should-not (store/finished? "t-1"))
    (repo/git *dir* "merge" "-q" "--no-ff" "--no-edit" "pr")
    (should (store/finished? "t-1"))
    (should= "pr" (:merged-into (store/finished-record "t-1")))
    (should= ["t-1"] (store/finished-ids))
    (should-not (store/finished? "t-2")))

  (it "do not include a squash commit, even one whose message repeats the finished record"
    (create! "t-1")
    (repo/git *dir* "merge" "-q" "--squash" "swarm/t-1")
    (repo/git *dir* "commit" "-q" "--allow-empty" "-m" (str "Squash\n\n" (finished-message "t-1" "main")))
    (should= [] (store/finished-ids)))

  (it "do not include a merge whose finished lines are not in its trailer block"
    (create! "t-1")
    (repo/git *dir* "merge" "-q" "--no-ff" "-m"
              (str "Merge\n\nSwarmForge-Record: finished\nSwarmForge-Task: t-1\n\nnot trailers: the end")
              "swarm/t-1")
    (should= [] (store/finished-ids))))

(defn- tree-files [id]
  (set (clojure.string/split-lines (repo/git *dir* "ls-tree" "-r" "--name-only" (str "swarm/" id)))))

(describe "write! on a task made before records changed no file"
  (around [it] (with-repo it))

  (it "removes the task file of that task only when the record is at done"
    (.mkdirs (java.io.File. (str *dir* "/tasks")))
    (doseq [f ["t-1.md" "tasks/t-1.md" "tasks/xt-1.md" "tasks/t-2.md"]]
      (repo/git *dir* "add" "--" (do (spit (str *dir* "/" f) "x") f)))
    (repo/git *dir* "commit" "-q" "-m" "Add task files")
    (create! "t-1")
    (let [rec {:record "handoff" :from "coder" :route "coder" :needs "none"}
          files (tree-files "t-1")]
      (store/write! "t-1" (fn [_] (assoc rec :stage "qa")))
      (should= files (tree-files "t-1"))
      (store/write! "t-1" (fn [_] (assoc rec :stage "done")))
      (should= (disj files "t-1.md" "tasks/t-1.md") (tree-files "t-1")))))

(describe "title-and-request"
  (around [it] (with-repo it))

  (it "reads them from the created record"
    (create! "t-1")
    (should= {:title "Add login" :request "Users log in."}
             (store/title-and-request (store/records-at (store/ref-of "t-1") "t-1"))))

  (it "reads them from the file that the created commit of an older task adds"
    (spit (str *dir* "/t-1.md") "---\nid: t-1\ncreated: 2026-10-27\n---\n\n# Old title\n\nOld request.\n")
    (repo/git *dir* "add" "t-1.md")
    (repo/git *dir* "commit" "-q" "-m" "sf created: t-1\n\nOld title\n\nSwarmForge-Task: t-1\nSwarmForge-Record: created\n")
    (should= {:title "Old title" :request "Old request."}
             (store/title-and-request (store/records-at "HEAD" "t-1")))))
