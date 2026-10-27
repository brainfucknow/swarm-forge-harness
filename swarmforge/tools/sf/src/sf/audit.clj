(ns sf.audit
  "The audit gate of the roles that roles.conf flags `audit`: the first attempt
  to record a commit is refused with a self-review; the next attempt at the
  same commit goes through."
  (:require [babashka.fs :as fs]
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

(defn gate
  "Stops with exit 3 when it refuses role's attempt to record head."
  [id role head]
  (when (config/role-audits? role)
    (let [file (fs/path (shell/repo-root) ".swarmforge" "audit" id)]
      (if (and (fs/exists? file) (= head (shell/chomp (slurp (str file)))))
        (fs/delete file)
        (do (fs/create-dirs (fs/parent file))
            (spit (str file) (str head "\n"))
            (println (refusal id head))
            (shell/fail 3 nil))))))
