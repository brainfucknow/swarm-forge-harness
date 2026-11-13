(ns sf.spec-revisions-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.property-support :refer [hex7 passes? sentence]]
            [sf.spec-revisions :as spec-revisions]))

(def ^:private size (gen/choose 0 5000))

(describe "growth limit"
  (it "holds exactly when size is more than half over a base that is not 0"
    (should (passes? (prop/for-all [s size b size]
                       (= (spec-revisions/over-limit? s b)
                          (and (pos? b) (> (/ (- s b) b) 1/2)))))))

  (it "never disagrees with the displayed growth: over the limit shows at least 50%, under it at most 50%"
    (should (passes? (prop/for-all [s size b (gen/choose 1 5000)]
                       (let [shown (parse-long (str/replace (spec-revisions/growth s b) "%" ""))]
                         (if (spec-revisions/over-limit? s b) (>= shown 50) (<= shown 50)))))))

  (it "prints the cap line exactly when the cap is used and the growth line exactly when over the limit, cap first"
    (should (passes? (prop/for-all [automatic (gen/choose 0 4) s size b size]
                       (let [lines (spec-revisions/limits automatic s b)]
                         (= lines (cond-> []
                                    (>= automatic spec-revisions/cap) (conj (first (spec-revisions/limits spec-revisions/cap 0 0)))
                                    (spec-revisions/over-limit? s b) (conj (str "LIMIT: growth size " s " is more than half over " b))))))))))

(def ^:private numstat-row
  (gen/one-of [(gen/tuple (gen/fmap str (gen/choose 0 40)) (gen/fmap str (gen/choose 0 40)))
               (gen/return ["-" "-"])]))

(def ^:private spec-commit
  (gen/let [sha hex7 subject sentence body (gen/vector sentence 0 4) numstat (gen/vector numstat-row 0 3)]
    {:sha sha :parents ["p"] :message (str (str/join "\n" (cons subject body)) "\n")
     :trailers [] :numstat numstat}))

(def ^:private noise-commit
  "A commit size leaves out: a record commit or a merge."
  (gen/let [c spec-commit merge? gen/boolean]
    (if merge?
      (assoc c :parents ["p" "q"])
      (assoc c :trailers ["SwarmForge-Task: t" "SwarmForge-Record: review"]))))

(defn- interleave-at [commits extra positions]
  (reduce (fn [cs [c i]] (let [i (mod i (inc (count cs)))] (into (conj (subvec cs 0 i) c) (subvec cs i))))
          (vec commits) (map vector extra positions)))

(describe "size"
  (it "is never below 0"
    (should (passes? (prop/for-all [cs (gen/vector spec-commit 0 5)]
                       (<= 0 (spec-revisions/size cs))))))

  (it "is unchanged by record commits and merges anywhere in the history"
    (should (passes? (prop/for-all [cs (gen/vector spec-commit 0 4)
                                    noise (gen/vector noise-commit 1 3)
                                    at (gen/vector gen/nat 3)]
                       (= (spec-revisions/size cs) (spec-revisions/size (interleave-at cs noise at)))))))

  (it "drops exactly the message lines of a superseded commit, keeping its tracked lines"
    (should (passes? (prop/for-all [old spec-commit new spec-commit]
                       (let [old (update old :trailers conj "Lore-id: aaaa")
                             plain [old new]
                             superseding [old (update new :trailers conj "Supersedes: aaaa")]
                             old-lines (count (remove str/blank? (str/split-lines (:message old))))]
                         (= old-lines (- (spec-revisions/size plain) (spec-revisions/size superseding)))))))))

(def ^:private gate-record
  (gen/elements [{:record "review" :from "specifier" :stage "specifier"}
                 {:record "review" :from "coder" :stage "coder"}
                 {:record "spec-verdict" :from "spec-judge" :stage "specifier" :spec-verdict "stuck"}
                 {:record "changes-requested" :from "spec-judge" :stage "specifier" :spec-verdict "continue"}
                 {:record "changes-requested" :from "operator" :stage "specifier"}
                 {:record "question" :from "specifier" :stage "specifier"}
                 {:record "answer" :from "operator" :stage "specifier"}]))

(def ^:private records
  (gen/fmap (fn [rs] (vec (map-indexed #(assoc %2 :commit (str "c" %1) :text (str "t" %1)) rs)))
            (gen/vector gate-record 0 14)))

(defn- specifier-review? [{:keys [record stage]}] (and (= "review" record) (= "specifier" stage)))

(describe "spec revisions"
  (it "opens one spec revision per specifier review, numbered from 1"
    (should (passes? (prop/for-all [rs records]
                       (= (range 1 (inc (count (filter specifier-review? rs))))
                          (map :n (spec-revisions/spec-revisions rs)))))))

  (it "puts every spec verdict and changes-requested record after the first spec revision's review in exactly one spec revision, in order"
    (should (passes? (prop/for-all [rs records]
                       (= (->> rs (drop-while (complement specifier-review?))
                               (filter #(#{"spec-verdict" "changes-requested"} (:record %))))
                          (mapcat :records (spec-revisions/spec-revisions rs)))))))

  (it "gives each spec revision a baseline no later than itself that never moves back"
    (should (passes? (prop/for-all [rs records]
                       (let [bs (map :baseline (spec-revisions/spec-revisions rs))]
                         (and (every? true? (map <= bs (map :n (spec-revisions/spec-revisions rs))))
                              (= bs (sort bs))))))))

  (it "resets the baseline exactly at the first spec revision after an operator send-back"
    (should (passes? (prop/for-all [rs records]
                       (let [found (spec-revisions/spec-revisions rs)]
                         (every? (fn [[before after]]
                                   (= (= (:n after) (:baseline after))
                                      (boolean (some #(and (= "operator" (:from %)) (= "changes-requested" (:record %)))
                                                     (:records before)))))
                                 (partition 2 1 found)))))))

  (it "counts as automatic only the send-backs a continue spec verdict wrote"
    (should (passes? (prop/for-all [rs records]
                       (= (spec-revisions/automatic-count rs)
                          (count (filter #(= "continue" (:spec-verdict %)) rs))))))))
