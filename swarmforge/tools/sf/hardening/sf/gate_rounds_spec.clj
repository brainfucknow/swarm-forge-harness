(ns sf.gate-rounds-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should= around]]
            [sf.history :as history]
            [sf.rounds :as rounds]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)

(defn- git [& args]
  (shell/chomp (apply shell/must {:dir *dir*} "git" "-c" "user.name=t" "-c" "user.email=t@x" args)))

(defn- commit-lines! [file n message]
  (spit (str (fs/path *dir* file)) (apply str (repeat n "x\n")))
  (git "add" file)
  (git "commit" "-q" "-m" message)
  (git "rev-parse" "HEAD"))

(def review {:record "review" :stage "specifier"})

(describe "gate-rounds"
  (around [it]
    (let [dir (str (fs/create-temp-dir {:prefix "gate-rounds-spec-"}))]
      (try
        (binding [*dir* dir]
          (git "init" "-q" "-b" "main")
          (git "commit" "-q" "--allow-empty" "-m" "Base")
          (with-redefs [shell/repo-root (constantly dir)] (it)))
        (finally (fs/delete-tree dir)))))

  (it "sizes each round at its reviewed commit over the commits after created, and limits the newest"
    (let [base (git "rev-parse" "HEAD")
          a (commit-lines! "a.txt" 4 "Spec A")
          b (commit-lines! "b.txt" 2 "Spec B")
          records [{:record "created" :commit base}
                   (assoc review :reviewed a)
                   {:record "changes-requested" :verdict "continue"}
                   (assoc review :reviewed b)]
          {found :rounds limits :limits} (history/gate-rounds records)]
      (should= [5 8] (map :size found))
      (should= [5 5] (map :base found))
      (should= [(rounds/growth 5 5) (rounds/growth 8 5)] (map :growth found))
      (should= [(git "rev-parse" "--short" a) (git "rev-parse" "--short" b)] (map :short found))
      (should= (rounds/limits 1 8 5) limits)))

  (it "has no rounds and no limits before the first review"
    (should= {:rounds [] :limits []} (history/gate-rounds [{:record "created" :commit (git "rev-parse" "HEAD")}])))

  (it "counts every commit up to reviewed when no created record is readable"
    (let [a (commit-lines! "a.txt" 4 "Spec A")]
      (should= [6] (map :size (:rounds (history/gate-rounds [(assoc review :reviewed a)])))))))
