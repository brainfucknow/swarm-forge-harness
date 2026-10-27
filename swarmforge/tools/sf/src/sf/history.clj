(ns sf.history
  "What sf start and sf show print about a task: its request, its records and
  the LORE: lines of its commits, open or finished."
  (:require [clojure.string :as str]
            [sf.flow :as flow]
            [sf.lore :as lore]
            [sf.record :as record]
            [sf.shell :as shell]
            [sf.store :as store]))

(defn- lore-lines
  "The LORE: lines of the first-parent commits of rev after the created record."
  [rev records]
  (when-let [base (flow/created-commit records)]
    (lore/lines (lore/read-log (shell/must {:dir (shell/repo-root)} "git" "log" "--reverse" "--first-parent"
                                           (str "--format=" lore/log-format) (str base ".." rev))))))

(defn- branch-history [rev id]
  (let [records (store/records-at rev id)]
    {:records (store/with-files records) :lore (lore-lines rev records)}))

(defn- finished-history
  "From the merge of sf finish when HEAD has it, else from the archive, with
  the LORE: lines of the branch while it exists."
  [id file]
  (if-let [finished (flow/finished-record (store/records-at "HEAD" id))]
    (update (branch-history (str (:sha finished) "^2") id) :records concat [finished])
    {:blocks (:blocks file)
     :lore (when (store/has-branch? id) (:lore (branch-history (store/ref-of id) id)))}))

(defn view
  "The task's :file, :status, :state, and :records or archived :blocks, with
  its :lore lines."
  [id]
  (let [file (store/task-file id)]
    (if (store/finished? id)
      (merge {:file file :status "finished" :state (record/archived-state file)}
             (finished-history id file))
      (merge {:file file :status "open" :state (store/state id)}
             (branch-history (store/ref-of id) id)))))

(defn print-body
  "Prints a --- line, the request, the record blocks and the LORE: lines."
  [{:keys [file records blocks lore]}]
  (println "---")
  (println (str/join "\n\n" (remove str/blank? [(:request file)
                                                (str/join "\n\n" (or blocks (map record/block records)))
                                                (str/join "\n" lore)]))))
