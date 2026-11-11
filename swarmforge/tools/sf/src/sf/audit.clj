(ns sf.audit
  "The audit gate of the roles that roles.conf flags `audit`: the first attempt
  to record a commit is refused with a self-review; the next attempt at the
  same commit goes through."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.shell :as shell]
            [sf.store :as store]))

(defn- refusal [id head]
  (str "AUDIT_REQUIRED: " id " at " head "; nothing was recorded
Before running this command again:
- Re-read the task as sf start prints it: the request, every answer, and the record you received.
- Check every requirement and constraint against the committed work, including boundaries, failure cases, and what must not happen.
- For each requirement, name the test or check that covers it, or call it a gap. Passing tools alone do not make the task complete.
- Read the whole diff since the previous record (git diff " (flow/last-milestone-commit (store/records-at (store/ref-of id) id)) "..HEAD), the tests, and anything generated or left in the working tree.
- Fix every finding, commit, rerun the checks, then run this command again. It records the handoff when HEAD is still " head "; a new commit starts a new audit."))

(defn- path-of [id]
  (fs/path (shell/repo-root) ".swarmforge" "audit" id))

(defn- pending
  "The :head the gate refused last and the number of :refusals since the
  task's last audited record, from the pending audit file. A file an earlier sf
  wrote holds only the head; it stands for one refusal."
  [path]
  (if (fs/exists? path)
    (let [[head refusals] (str/split-lines (slurp (str path)))]
      {:head (str/trim (or head "")) :refusals (or (some-> refusals str/trim parse-long) 1)})
    {:head nil :refusals 0}))

(defn gate
  "nil when role does not audit. Otherwise stops with exit 3 when it refuses
  role's attempt to record head, or returns the number of refusals the record
  carries. The count stays pending until clear! runs after the record is
  written."
  [id role head]
  (when (config/role-audits? role)
    (let [path (path-of id)
          {:keys [refusals] refused :head} (pending path)]
      (if (= head refused)
        refusals
        (do (fs/create-dirs (fs/parent path))
            (spit (str path) (str head "\n" (inc refusals) "\n"))
            (println (refusal id head))
            (shell/fail 3 nil))))))

(defn clear!
  "Removes the task's pending audit state once its record is written."
  [id]
  (fs/delete-if-exists (path-of id)))
