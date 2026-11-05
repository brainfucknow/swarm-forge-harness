(ns sf.lore-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.lore :as lore]
            [sf.property-support :refer [hex7 passes? sentence]]))

(def ^:private trailer-key
  (gen/one-of [(gen/elements ["Constraint" "Rejected" "Tested" "Not-tested" "Directive" "Confidence"])
               (gen/elements ["Co-Authored-By" "Signed-off-by" "Session" "Tested-by"])]))

(def ^:private trailer (gen/tuple trailer-key sentence))

(def ^:private message
  "A commit message and the keys of the trailers git reads from it."
  (gen/let [subject sentence
            body (gen/vector sentence 0 3)
            trailers (gen/vector trailer 0 4)
            blank-tail (gen/elements ["" "\n" "\n\n"])]
    {:keys (mapv first trailers)
     :text (str (str/join "\n\n" (cond-> (into [subject] body)
                                   (seq trailers) (conj (str/join "\n" (map (fn [[k v]] (str k ": " v)) trailers)))))
                "\n" blank-tail)}))

(def ^:private role (gen/elements ["coder" "cleaner" "architect" "qa"]))

(defn- sign [text role keys]
  (lore/sign text role keys (constantly "0badc0de")))

(defn- last-paragraph [text]
  (last (str/split (str/trim-newline text) #"\n\n")))

(defn- signed-keys
  "The trailer keys of a signed message: the original ones, and a Lore-id the
  signature put first."
  [signed keys]
  (cond->> keys
    (str/starts-with? (last-paragraph signed) "Lore-id: ") (cons "Lore-id")))

(describe "sign"
  (it "is idempotent: a signed message signs to itself"
    (should (passes? (prop/for-all [{:keys [text keys]} message r role]
                       (let [signed (sign text r keys)]
                         (= signed (sign signed r (signed-keys signed keys))))))))

  (it "leaves exactly one byline for the role"
    (should (passes? (prop/for-all [{:keys [text keys]} message r role]
                       (= 1 (count (filter #{(str "By " r ".")} (str/split-lines (sign text r keys)))))))))

  (it "keeps the trailer block last, so git still reads it"
    (should (passes? (prop/for-all [{:keys [text keys]} message r role]
                       (let [block (when (seq keys) (last-paragraph text))]
                         (or (nil? block)
                             (str/ends-with? (sign text r keys) (str block "\n"))))))))

  (it "adds a Lore-id exactly when the block has a Lore trailer and no Lore-id"
    (should (passes? (prop/for-all [{:keys [text keys]} message r role]
                       (= (boolean (some lore/lore-key? keys))
                          (str/includes? (sign text r keys) "Lore-id: 0badc0de"))))))

  (it "keeps the text before the trailer block as a prefix"
    (should (passes? (prop/for-all [{:keys [text keys]} message r role]
                       (let [head (str/trim-newline (if (seq keys)
                                                      (subs text 0 (str/last-index-of (str/trim-newline text) "\n\n"))
                                                      text))]
                         (str/starts-with? (sign text r keys) head)))))))

(def ^:private commit
  (gen/let [sha hex7
            message (gen/fmap #(str (str/join "\n" %) "\n") (gen/not-empty (gen/vector gen/string-alphanumeric)))
            trailers (gen/vector (gen/fmap (fn [[k v]] (str k ": " v)) trailer) 0 4)]
    {:sha sha :message message :trailers trailers}))

(defn- git-log
  "The output git log --format=lore/log-format prints for commits."
  [commits]
  (apply str (for [{:keys [sha message trailers]} commits]
               (str sha "\u001f" message "\u001f" (apply str (map #(str % "\n") trailers)) "\u001e\n"))))

(describe "read-log"
  (it "reads back the commits git log prints"
    (should (passes? (prop/for-all [commits (gen/vector commit 0 5)]
                       (= commits (lore/read-log (git-log commits)))))))

  (it "gives one LORE line per Lore trailer, in order"
    (should (passes? (prop/for-all [commits (gen/vector commit 0 5)]
                       (= (for [{:keys [sha trailers]} commits
                                t trailers
                                :when (lore/lore-key? (lore/trailer-key t))]
                            [sha t])
                          (for [line (lore/lines (lore/read-log (git-log commits)))
                                :let [[_ sha _ t] (re-matches #"LORE: (\S+) (\S+) (.*)" line)]]
                            [sha t])))))))

(def ^:private lore-id (gen/elements ["a1" "b2" "c3" "d4"]))

(def ^:private brief-trailer
  "A trailer of any Lore key, a Supersedes naming one of a few Lore-ids, or a
  trailer that is not Lore."
  (gen/one-of [(gen/fmap (fn [[k v]] (str k ": " v))
                         (gen/tuple (gen/elements ["Constraint" "Rejected" "Directive" "Scope-risk" "Reversibility"
                                                   "Depends-on" "Related" "Tested" "Not-tested" "Confidence"
                                                   "Co-Authored-By"])
                                    sentence))
               (gen/fmap #(str "Supersedes: " %) lore-id)]))

(def ^:private brief-commit
  "A commit with an optional Lore-id first, as the hook writes it."
  (gen/let [sha hex7
            r role
            id (gen/one-of [(gen/return nil) lore-id])
            trailers (gen/vector brief-trailer 0 4)]
    {:sha sha :message (str "M\n\nBy " r ".\n")
     :trailers (vec (cond->> trailers id (cons (str "Lore-id: " id))))}))

(def ^:private evidence #{"Tested" "Not-tested" "Confidence" "Lore-id"})

(defn- line-key [line]
  (second (re-matches #"LORE: \S+ \S+ ([^:]+): .*" line)))

(defn- line-sha [line]
  (second (re-matches #"LORE: (\S+) .*" line)))

(defn- subsequence? [xs ys]
  (cond (empty? xs) true
        (empty? ys) false
        (= (first xs) (first ys)) (recur (rest xs) (rest ys))
        :else (recur xs (rest ys))))

(describe "brief"
  (it "keeps LORE lines in sf show order and counts every line it leaves out"
    (should (passes? (prop/for-all [commits (gen/vector brief-commit 0 6)]
                       (let [{:keys [lines omitted]} (lore/brief commits)
                             all (lore/lines commits)]
                         (and (subsequence? lines all)
                              (= (count all) (+ (count lines) omitted))))))))

  (it "keeps no evidence line and no line of a superseded commit"
    (should (passes? (prop/for-all [commits (gen/vector brief-commit 0 6)]
                       (let [superseded (set (for [{:keys [trailers]} commits
                                                   t trailers
                                                   :when (str/starts-with? t "Supersedes: ")]
                                               (subs t (count "Supersedes: "))))
                             gone (set (for [{:keys [sha trailers]} commits
                                             t trailers
                                             :when (superseded (second (re-matches #"Lore-id: (.*)" t)))]
                                         sha))]
                         (not-any? #(or (evidence (line-key %)) (gone (line-sha %)))
                                   (:lines (lore/brief commits))))))))

  (it "leaves out only the evidence lines when nothing is superseded"
    (should (passes? (prop/for-all [commits (gen/vector brief-commit 0 6)]
                       (let [commits (map #(update % :trailers (partial remove (fn [t] (str/starts-with? t "Supersedes: "))))
                                          commits)]
                         (= (remove #(evidence (line-key %)) (lore/lines commits))
                            (:lines (lore/brief commits)))))))))
