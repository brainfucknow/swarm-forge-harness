(ns sf.record-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.property-support :refer [hex7 passes? role sentence word]]
            [sf.record :as record]))

(def ^:private title (gen/one-of [sentence gen/string (gen/fmap #(str/join "-" %) (gen/vector word 1 12))]))

(def ^:private slug-shape #"[a-z0-9]+(-[a-z0-9]+)*")

(describe "slug"
  (it "is lowercase ASCII words joined by single dashes, at most 30 characters"
    (should (passes? (prop/for-all [t title]
                       (let [s (record/slug t)]
                         (and (re-matches slug-shape s) (<= 1 (count s) 30)))))))

  (it "is idempotent"
    (should (passes? (prop/for-all [t title]
                       (= (record/slug t) (record/slug (record/slug t))))))))

(def ^:private ids
  "Task ids with distinct hex parts."
  (gen/let [hexes (gen/fmap distinct (gen/not-empty (gen/vector hex7 1 6)))
            titles (gen/vector sentence (count hexes))]
    (mapv #(str (record/slug %1) "-" %2) titles hexes)))

(describe "resolve"
  (it "finds each id by itself and by its hex part"
    (should (passes? (prop/for-all [ids ids]
                       (every? #(= [:ok %] (record/resolve % ids) (record/resolve (record/hex-part %) ids)) ids)))))

  (it "only ever finds one of the ids"
    (should (passes? (prop/for-all [ids ids arg (gen/one-of [hex7 word])]
                       (let [[status found] (record/resolve arg ids)]
                         (case status
                           :ok (contains? (set ids) found)
                           :ambiguous (and (< 1 (count found)) (every? (set ids) found))
                           :none true)))))))

(describe "new-id"
  (it "takes the slug and a hex part that no task has"
    (should (passes? (prop/for-all [t title taken (gen/vector hex7 0 5) fresh hex7]
                       (let [id (record/new-id t taken (concat taken [fresh]))]
                         (and (= id (str (record/slug t) "-" (record/hex-part id)))
                              (not (some #{(record/hex-part id)} taken)))))))))

(def ^:private value (gen/fmap #(str/join "," %) (gen/not-empty (gen/vector word 1 3))))

(def ^:private text
  (gen/fmap #(str/join "\n" %) (gen/vector (gen/one-of [sentence (gen/return "")]) 0 4)))

(def ^:private rec
  (gen/let [task value
            type (gen/elements ["created" "question" "answer" "review" "handoff" "route" "finished"])
            from role
            text text
            fields (gen/map (gen/elements [:stage :route :needs :merged-into]) value)]
    (merge fields {:task task :record type :from from} (when-not (str/blank? text) {:text text}))))

(defn- trailer-lines
  "The trailers git reads from a record message: the lines of its last paragraph."
  [message]
  (str/split-lines (last (str/split (str/trim message) #"\n\n"))))

(describe "message and parse"
  (it "read back the fields and the trimmed text of a record"
    (should (passes? (prop/for-all [r rec sha hex7 parent hex7]
                       (let [message (record/message r)
                             parsed (record/parse {:sha sha :parents [parent] :message message
                                                   :trailers (trailer-lines message)})]
                         (= (cond-> r (:text r) (update :text str/trim))
                            (dissoc parsed :sha :commit))))))))

(def ^:private chain
  (gen/let [created hex7
            steps (gen/vector (gen/tuple hex7 (gen/one-of [(gen/return nil) hex7])) 0 6)]
    ;; Each step is a record sha and the role commit it was made on, or nil when
    ;; it was made straight on the record before it.
    (reduce (fn [records [sha role-commit]]
              (conj records {:record "question" :sha sha
                             :commit (or role-commit (:sha (peek records)))}))
            [{:record "created" :sha created :commit created}]
            steps)))

(describe "role-commits"
  (it "points every record at the created commit or a role's commit, never at a later record"
    (should (passes? (prop/for-all [records chain]
                       (let [later (set (map :sha (rest records)))]
                         (not-any? #(later (:commit %)) (record/role-commits records)))))))

  (it "is idempotent"
    (should (passes? (prop/for-all [records chain]
                       (= (record/role-commits records)
                          (record/role-commits (record/role-commits records))))))))

(describe "files-value"
  (it "lists at most 40 paths and counts the rest"
    (should (passes? (prop/for-all [n (gen/choose 0 90)]
                       (let [paths (map #(str "f" %) (range n))
                             listed (str/split (record/files-value paths) #",")]
                         (cond (zero? n) (= ["-"] listed)
                               (<= n 40) (= paths listed)
                               :else (= (concat (take 40 paths) [(str "+" (- n 40) " more")]) listed))))))))

(def ^:private request
  "Paragraphs separated by one or more blank lines."
  (gen/let [paragraphs (gen/not-empty (gen/vector sentence 1 3))
            gaps (gen/vector (gen/elements ["\n\n" "\n\n\n"]) (count paragraphs))]
    (apply str (first paragraphs) (interleave (rest gaps) (rest paragraphs)))))

(describe "title and request"
  (it "read back from the parsed created message, blank lines in the request kept"
    (should (passes? (prop/for-all [title sentence req request]
                       (let [message (record/message {:task "t-1" :record "created" :from "operator"
                                                      :text (record/created-text title req)
                                                      :stage "coder" :route "coder" :needs "none"})
                             parsed (record/parse {:sha "s" :parents ["p"] :message message
                                                   :trailers (trailer-lines message)})]
                         (= {:title title :request req} (record/title-and-request (:text parsed)))))))))

(def ^:private kept-kinds #{"question" "answer" "changes-requested"})

(def ^:private history
  "Records of every kind, each with its position as :n so equal kinds differ."
  (gen/fmap #(vec (map-indexed (fn [n kind] {:record kind :n n}) %))
            (gen/vector (gen/elements ["created" "question" "answer" "changes-requested" "review"
                                       "approval" "handoff" "route" "start"])
                        0 30)))

(defn- other? [r] (not (kept-kinds (:record r))))

(describe "brief"
  (it "keeps the records in order and counts every record it leaves out"
    (should (passes? (prop/for-all [records history limit (gen/choose 0 12)]
                       (let [{shown :records omitted :omitted} (record/brief records limit)]
                         (and (apply < -1 (map :n shown))
                              (= (count records) (+ (count shown) omitted))))))))

  (it "keeps every question, answer and changes-requested record and the last limit others"
    (should (passes? (prop/for-all [records history limit (gen/choose 0 12)]
                       (let [shown (:records (record/brief records limit))
                             others (filter other? records)]
                         (and (= (remove other? records) (remove other? shown))
                              (= (vec (take-last limit others)) (filter other? shown))))))))

  (it "is idempotent: a brief leaves nothing more out of itself"
    (should (passes? (prop/for-all [records history limit (gen/choose 0 12)]
                       (let [shown (:records (record/brief records limit))]
                         (= {:records shown :omitted 0} (record/brief shown limit))))))))
