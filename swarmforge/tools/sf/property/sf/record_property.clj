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

(describe "role-commits"
  (let [chain (gen/let [created hex7
                        steps (gen/vector (gen/tuple hex7 (gen/one-of [(gen/return nil) hex7])) 0 6)]
                ;; Each step is a record sha and the role commit it was made on, or nil when
                ;; it was made straight on the record before it.
                (reduce (fn [records [sha role-commit]]
                          (conj records {:record "question" :sha sha
                                         :commit (or role-commit (:sha (peek records)))}))
                        [{:record "created" :sha created :commit created}]
                        steps))]
    (it "points every record at the created commit or a role's commit, never at a later record"
      (should (passes? (prop/for-all [records chain]
                         (let [later (set (map :sha (rest records)))]
                           (not-any? #(later (:commit %)) (record/role-commits records)))))))

    (it "is idempotent"
      (should (passes? (prop/for-all [records chain]
                         (= (record/role-commits records)
                            (record/role-commits (record/role-commits records)))))))))

(describe "files-value"
  (it "lists at most 40 paths and counts the rest"
    (should (passes? (prop/for-all [n (gen/choose 0 90)]
                       (let [paths (map #(str "f" %) (range n))
                             listed (str/split (record/files-value paths) #",")]
                         (cond (zero? n) (= ["-"] listed)
                               (<= n 40) (= paths listed)
                               :else (= (concat (take 40 paths) [(str "+" (- n 40) " more")]) listed))))))))

(def ^:private request
  (gen/fmap #(str/join "\n\n" %) (gen/not-empty (gen/vector sentence 1 3))))

(def ^:private block
  (gen/fmap #(str/join "\n" %) (gen/not-empty (gen/vector (gen/fmap (fn [[k v]] (str k ": " v)) (gen/tuple word sentence)) 1 4))))

(describe "task files"
  (it "read back the id, title and request they were written with"
    (should (passes? (prop/for-all [id value title sentence req request]
                       (let [file (record/read-task-file (record/task-file {:id id :date "2026-10-27" :title title :request req}))]
                         (= [id title req []]
                            [(get-in file [:front "id"]) (:title file) (:request file) (:blocks file)]))))))

  (it "keep the request and every record block once archived"
    (should (passes? (prop/for-all [id value title sentence req request route value blocks (gen/vector block 0 4)]
                       (let [text (record/task-file {:id id :date "2026-10-27" :title title :request req})
                             file (record/read-task-file (record/archive text route blocks))]
                         (= [id title req blocks {:stage "done" :route route :needs "none"}]
                            [(get-in file [:front "id"]) (:title file) (:request file) (:blocks file)
                             (record/archived-state file)])))))))
