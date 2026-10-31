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

(defn- record-history [rev records]
  {:records (store/with-files records) :commits (lore-commits rev records)})

(defn- branch-history [rev id]
  (record-history rev (store/records-at rev id)))

(defn- finished-history
  "From the merge of sf finish when HEAD has it, else from the archive, with
  the LORE: lines of the branch while it exists."
  [id file]
  (if-let [finished (flow/finished-record (store/records-at "HEAD" id))]
    (update (branch-history (str (:sha finished) "^2") id) :records concat [finished])
    {:blocks (:blocks file)
     :commits (when (store/has-branch? id) (:commits (branch-history (store/ref-of id) id)))}))

(defn view
  "The task's :file, :status, :state, and :records or archived :blocks, with
  the :commits whose Lore trailers make its LORE: lines."
  [id]
  (let [file (store/task-file id)]
    (if (store/finished? id)
      (merge {:file file :status "finished" :state (record/archived-state file)}
             (finished-history id file))
      (let [rev (store/ref-of id)
            records (store/records-at rev id)
            state (or (last records) (shell/die "swarm/" id " has no records"))]
        (merge {:file file :status "open" :state state}
               (record-history rev records))))))

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
  [{:keys [file records blocks commits]}]
  (print-sections (:request file)
                  (str/join "\n\n" (or blocks (map record/block records)))
                  (str/join "\n" (lore/lines commits))))

(defn print-brief
  "print-body for sf start of task id: only the records that record/brief
  keeps and the lines that lore/brief keeps, each section led by a line that
  counts what it left out."
  [id {:keys [file records commits]}]
  (let [{shown :records omitted :omitted} (record/brief records brief-records)
        {lore :lines lore-omitted :omitted} (lore/brief commits)]
    (print-sections (:request file)
                    (omitted-line "RECORDS" omitted "earlier records omitted" id)
                    (str/join "\n\n" (map record/block shown))
                    (str/join "\n" (remove nil? (cons (omitted-line "LORE-OMITTED" lore-omitted "superseded or evidence lines" id)
                                                         lore))))))
