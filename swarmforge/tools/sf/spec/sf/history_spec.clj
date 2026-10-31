(ns sf.history-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [describe it should= should-be-nil around]]
            [sf.shell :as shell]
            [sf.history :as history]
            [sf.lore]
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

(def ^:private finished-history @#'sf.history/finished-history)

(describe "finished-history"
  (around [it] (with-repo it))

  (it "takes the archive's blocks and no commits when the branch is gone"
    (with-redefs [store/records-at (constantly [])
                  store/has-branch? (constantly false)
                  store/ref-of (fn [_] (throw (ex-info "no branch" {})))]
      (should= {:blocks ["b"] :commits nil} (finished-history "t-1" {:blocks ["b"]}))))

  (it "adds the commits of the branch while it exists"
    (let [base (git *dir* "rev-parse" "--short" "HEAD")
          _ (git *dir* "commit" "-q" "--allow-empty" "-m" "Code\n\nBy coder.\n\nConstraint: keep it\n")
          head (git *dir* "rev-parse" "--short" "HEAD")]
      (with-redefs [store/records-at (constantly [{:record "created" :commit base}])
                    store/with-files identity
                    store/has-branch? (constantly true)
                    store/ref-of (constantly "HEAD")]
        (should= ["b"] (:blocks (finished-history "t-1" {:blocks ["b"]})))
        (should= [(str "LORE: " head " coder Constraint: keep it")]
                 (sf.lore/lines (:commits (finished-history "t-1" {:blocks ["b"]}))))))))

(defn- view [records & commits]
  {:file {:request "Users log in."} :records records :commits commits})

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
