(ns sf.git-spec
  (:require [speclj.core :refer [around describe it should= should-be-nil should-contain]]
            [sf.git :as git]
            [sf.repo-support :as repo :refer [*dir* with-repo]]))

(def ^:private spec-ref "refs/heads/spec")

(defn- commit
  "A new commit of the base tree with message, on parent when there is one."
  [message parent]
  (git/commit-tree (git/tree-with "HEAD" [] {}) (if parent [parent] []) message))

(defn- interloper
  "A make that moves spec-ref itself on its first n calls before answering, as a
  concurrent writer would; it counts its calls in calls."
  [n calls]
  (fn [tip]
    (let [call (swap! calls inc)]
      (when (<= call n)
        (repo/git *dir* "update-ref" spec-ref (commit (str "other " call) tip)))
      {:commit (commit (str "mine " call) tip) :call call})))

(describe "moving a ref"
  (around [it] (with-repo it))

  (it "names no commit for a missing ref"
    (should-be-nil (git/rev spec-ref)))

  (it "creates a missing ref"
    (let [step (git/advance! spec-ref (fn [tip] {:commit (commit "first" tip) :tip tip}))]
      (should-be-nil (:tip step))
      (should= (:commit step) (git/rev spec-ref))))

  (it "builds on the tip it hands to make"
    (let [first-commit (:commit (git/advance! spec-ref (fn [tip] {:commit (commit "first" tip)})))]
      (git/advance! spec-ref (fn [tip] {:commit (commit "second" tip)}))
      (should= first-commit (repo/git *dir* "rev-parse" (str spec-ref "^")))))

  (it "moves nothing when make returns nil"
    (should-be-nil (git/advance! spec-ref (constantly nil)))
    (should-be-nil (git/rev spec-ref)))

  (it "reads the ref again and retries once when another writer moves it first"
    (let [step (git/advance! spec-ref (interloper 1 (atom 0)))]
      (should= 2 (:call step))
      (should= nil (:error step))
      (should= "other 1" (repo/git *dir* "log" "-1" "--format=%s" (str spec-ref "^")))))

  (it "gives up with git's error when another writer moves it twice"
    (let [step (git/advance! spec-ref (interloper 2 (atom 0)))]
      (should= 2 (:call step))
      (should-contain "spec" (:error step))
      (should= "other 2" (repo/git *dir* "log" "-1" "--format=%s" spec-ref)))))
