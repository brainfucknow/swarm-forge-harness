(ns sf.history
  "What sf start and sf show print about a task: its request, its records and
  the LORE: lines of its commits, open or finished."
  (:require [clojure.string :as str]
            [sf.flow :as flow]
            [sf.lore :as lore]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]))

(defn- lore-commits
  "The first-parent commits of rev after the created record, as lore/read-log
  reads them."
  [rev records]
  (when-let [base (flow/created-commit records)]
    (lore/read-log (shell/must {:dir (shell/repo-root)} "git" "log" "--reverse" "--first-parent"
                               (str "--format=" lore/log-format) (str base ".." rev)))))

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
