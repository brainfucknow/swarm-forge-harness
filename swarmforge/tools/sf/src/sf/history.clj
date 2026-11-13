(ns sf.history
  "What sf start and sf show print about a task: its request, its records and
  the LORE: lines of its commits, open or finished."
  (:require [clojure.string :as str]
            [sf.flow :as flow]
            [sf.lore :as lore]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.spec-revisions :as spec-revisions]
            [sf.store :as store]))

(defn- commits-after
  "The first-parent commits of rev after base, as lore/read-log reads them."
  [base rev]
  (lore/read-log (shell/must {:dir (shell/repo-root)} "git" "log" "--reverse" "--first-parent"
                             (str "--format=" lore/log-format) (str base ".." rev))))

(defn- lore-commits
  "The first-parent commits of rev after the created record, as lore/read-log
  reads them."
  [rev records]
  (some-> (flow/created-commit records) (commits-after rev)))

(defn- record-history
  "The task's :title and :request, its :records with their files and the
  :commits after the created record, on the first-parent history of rev."
  [rev records]
  (merge (store/title-and-request records)
         {:records (store/with-files records) :commits (lore-commits rev records)}))

(defn- finished-history
  "From the merge of sf finish: the records of its second parent, then the
  finished record."
  [finished]
  (let [rev (str (:sha finished) "^2")]
    (update (record-history rev (store/records-at rev (:task finished))) :records concat [finished])))

(defn view
  "The task's :title, :request, :status, :state, :records and the :commits
  whose Lore trailers make its LORE: lines."
  [id]
  (if-let [finished (store/finished-record id)]
    (merge {:status "finished" :state finished} (finished-history finished))
    (let [records (store/open-records id)]
      (merge {:status "open" :state (last records)}
             (record-history (store/ref-of id) records)))))

(def ^:private brief-records
  "How many records that are not question, answer or changes-requested sf start prints."
  10)

(defn- print-sections
  "Prints a --- line, then the non-blank sections separated by a blank line."
  [& sections]
  (println "---")
  (println (str/join "\n\n" (remove str/blank? sections))))

(defn- omitted-line
  "`<label>: <n> <what>; sf show <id> prints them`, or nil when n is 0."
  [label n what id]
  (when (pos? n)
    (str label ": " n " " what "; sf show " id " prints them")))

(defn print-body
  "Prints a --- line, the request, the record blocks and the LORE: lines."
  [{:keys [request records commits]}]
  (print-sections request
                  (str/join "\n\n" (map record/block records))
                  (str/join "\n" (lore/lines commits))))

(defn print-brief
  "print-body for sf start of task id: only the records that record/brief
  keeps and the lines that lore/brief keeps, each section led by a line that
  counts what it left out."
  [id {:keys [request records commits]}]
  (let [{shown :records omitted :omitted} (record/brief records brief-records)
        {lore :lines lore-omitted :omitted} (lore/brief commits)]
    (print-sections request
                    (omitted-line "RECORDS" omitted "earlier records omitted" id)
                    (str/join "\n\n" (map record/block shown))
                    (str/join "\n" (remove nil? (cons (omitted-line "LORE-OMITTED" lore-omitted "superseded or evidence lines" id)
                                                         lore))))))

(defn- spec-commits
  "The first-parent commits after created up to reviewed, as
  spec-revisions/read-log reads them; every commit up to reviewed when created
  is nil."
  [created reviewed]
  (spec-revisions/read-log (shell/must {:dir (shell/repo-root)} "git" "log" "--first-parent" "--numstat"
                                       (str "--format=" spec-revisions/log-format)
                                       (if created (str created ".." reviewed) reviewed) "--")))

(defn- short-sha [rev]
  (shell/chomp (shell/must {:dir (shell/repo-root)} "git" "rev-parse" "--short" rev)))

(defn spec-revisions
  "The spec revisions of the task whose records these are, oldest first, each
  with its :size, :base (the size of its baseline spec revision), :growth,
  :total (its growth over spec revision 1) and :short reviewed sha, and the
  :limits that hold for the newest spec revision."
  [records]
  (let [created (flow/created-commit records)
        found (spec-revisions/spec-revisions records)
        sizes (mapv #(spec-revisions/size (spec-commits created (:reviewed %))) found)
        sized (vec (for [{:keys [n baseline reviewed] :as revision} found
                         :let [size (sizes (dec n)) base (sizes (dec baseline))]]
                     (assoc revision :size size :base base :growth (spec-revisions/growth size base)
                            :total (spec-revisions/growth size (first sizes))
                            :short (short-sha reviewed))))
        newest (peek sized)]
    {:spec-revisions sized
     :limits (if newest
               (spec-revisions/limits (spec-revisions/automatic-count records) (:size newest) (:base newest))
               [])}))

(defn- harvested
  "The task as sf harvest prints it, from its records on the first-parent
  history of rev. A history without the created record (cut off by a shallow
  clone) makes it truncated: no title or request, and the commits after its
  oldest readable record."
  [id status rev records]
  (let [created (flow/created-commit records)]
    (merge (if created (store/title-and-request records) {:title "" :request ""})
           {:id id :status status :truncated (nil? created) :records records
            :commits (commits-after (or created (:sha (first records))) rev)})))

(defn- finished-task [{:keys [task sha] :as finished}]
  (let [rev (str sha "^2")
        records (store/records-at rev task)]
    (assoc (harvested task "finished" rev (or (not-empty records) [finished]))
           :records (concat records [finished]))))

(defn- open-task
  "The open task id, or nil, after a warning, when its branch has no record."
  [id]
  (let [records (store/records-at (store/ref-of id) id)]
    (if (seq records)
      (harvested id "open" (store/ref-of id) records)
      (shell/warn "swarm/" id " has no records; skipped"))))

(defn harvest
  "The tasks sf harvest prints, as harvest/line takes them: the finished ones,
  the oldest finish first, then the open ones by id."
  []
  (concat (map finished-task (store/finished-tasks))
          (keep open-task (store/open-ids))))
