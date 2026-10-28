(ns sf.store-spec
  (:require [speclj.core :refer [around describe it should=]]
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
