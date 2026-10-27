(ns sf.bug
  "Tasks as git-bug issues: fields, labels, and comment records."
  (:require [clojure.string :as str]
            [sf.config :as config]
            [sf.shell :as shell]))

(defn git-bug []
  (shell/env "SWARMFORGE_GIT_BUG" "git-bug"))

(defn run [& args]
  (apply shell/run {} (git-bug) "bug" args))

(defn out [& args]
  (shell/chomp (apply shell/must {} (git-bug) "bug" args)))

(defn bug-id
  "The full id of a task given any id prefix."
  [id]
  (when (str/blank? id) (shell/die "task id required"))
  (let [result (run "show" id "--field" "id")
        full (when (shell/ok? result) (shell/chomp (:out result)))]
    (when (str/blank? full) (shell/die "no task '" id "'"))
    full))

(defn short-id [id] (out "show" id "--field" "humanId"))
(defn title [id] (out "show" id "--field" "title"))
(defn status [id] (out "show" id "--field" "status"))
(defn show [id] (shell/must {} (git-bug) "bug" "show" id))

(defn labels [id]
  (shell/lines (out "show" id "--field" "labels")))

(defn- label-values [id prefix]
  (keep #(when (str/starts-with? % prefix) (subs % (count prefix))) (labels id)))

(defn stage [id] (first (label-values id "stage:")))
(defn needs [id] (label-values id "needs:"))
(defn route-label [id] (first (label-values id "route:")))

(defn route [id]
  (or (route-label id) (config/catalogue-route)))

(defn open-ids [& filters]
  (shell/lines (apply out (concat ["-s" "open"] filters ["--format" "id"]))))

(defn has-label? [id label]
  (boolean (some #{label} (labels id))))

(defn- pause [] (Thread/sleep 1000))

(def ^:private tries 5)

(defn require-label
  "True when the label is present within 5 reads, one second apart. A late label
  is logged, so a stale read shows in the log, not as a refusal."
  [id label]
  (loop [attempt 1]
    (cond
      (has-label? id label)
      (do (when (> attempt 1)
            (shell/warn label " on " (short-id id) " appeared after " (dec attempt) " retries"))
          true)
      (< attempt tries) (do (pause) (recur (inc attempt)))
      :else false)))

;; git-bug can give a process a label list that is older than the last write.
;; Thus each write is read back and repeated until it shows.

(defn- label-write
  "op is new or rm. A write that changes nothing is not an error."
  [op id label]
  (let [result (run "label" op id label)
        output (str (:out result) (:err result))]
    (when-not (or (shell/ok? result) (str/includes? output "no label added or removed"))
      (shell/die "git-bug label " op " " label " failed: " (shell/chomp output)))))

(defn- write-until [op id label visible? failure]
  (loop [attempt 1]
    (label-write op id label)
    (cond (= visible? (has-label? id label)) nil
          (< attempt tries) (do (pause) (recur (inc attempt)))
          :else (shell/die "label " label " " failure " " (short-id id)))))

(defn add-label [id label]
  (write-until "new" id label true "did not stick on"))

(defn rm-label [id label]
  (write-until "rm" id label false "did not come off"))

(defn- replace-label [id prefix old new]
  (when (not= old new)
    (when old (rm-label id (str prefix old)))
    (add-label id (str prefix new))))

(defn set-stage [id new] (replace-label id "stage:" (stage id) new))
(defn set-route [id new] (replace-label id "route:" (route-label id) new))

(defn record
  "Adds a comment of header lines, given as [key value] pairs."
  [id & pairs]
  (let [text (apply str (for [[k v] (partition 2 pairs)] (str k ": " v "\n")))]
    (shell/must {:in text} (git-bug) "bug" "comment" "new" id "-F" "-")
    nil))

(defn one-line
  "Free text in a record header stays on one line, so it cannot look like a header."
  [text]
  (-> text
      (str/replace #"[\n\r]" " ")
      (str/replace #" +" " ")
      (str/replace #"^ | $" "")))

(defn record-commit
  "The commit in the last record of one of types in shown, the text of git-bug
  show. Only the header lines of records count. The request body and free text
  do not."
  [shown types]
  (:commit (reduce (fn [{:keys [record?] :as state} line]
                     (cond
                       (re-find #"^ *[0-9a-f]+ #[0-9]+ " line) (assoc state :record? false)
                       (some->> (re-find #"^ *type: (\S+)$" line) second types) (assoc state :record? true)
                       (and record? (re-find #"^ *commit: " line))
                       (assoc state :commit (second (str/split (str/trim line) #"\s+")))
                       :else state))
                   {:record? false :commit nil}
                   (str/split-lines shown))))

(defn last-recorded-commit
  "The commit in the last created, review, or handoff record."
  [id]
  (record-commit (show id) #{"created" "review" "handoff"}))
