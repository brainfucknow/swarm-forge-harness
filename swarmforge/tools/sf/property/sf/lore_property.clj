(ns sf.lore-property
  (:require [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.lore :as lore]))

(def ^:private runs 200)

(defn- passes? [property]
  (let [result (tc/quick-check runs property)]
    (when-not (:pass? result) (prn (:shrunk result)))
    (:pass? result)))

(def ^:private word (gen/not-empty gen/string-alphanumeric))

(def ^:private sentence (gen/fmap #(str/join " " %) (gen/not-empty (gen/vector word 1 4))))

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
  (gen/let [sha (gen/fmap #(apply str %) (gen/vector (gen/elements (seq "0123456789abcdef")) 7))
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
