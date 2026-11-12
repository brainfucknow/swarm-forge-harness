(ns sf.audit-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.audit :as audit]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.shell :as shell]
            [sf.store :as store]))

(def ^:dynamic *root* nil)

(defn- task-path [] (str (fs/path *root* ".swarmforge" "audit" "t1")))

(defn- pending-file [role] (str (fs/path (task-path) role)))

(defn- roleless-file [text]
  (fs/create-dirs (fs/parent (task-path)))
  (spit (task-path) text))

(defn- attempt [role head]
  (let [out (java.io.StringWriter.)
        status (binding [*out* out]
                 (try (audit/gate "t1" role head)
                      (catch clojure.lang.ExceptionInfo e (:sf/exit (ex-data e)))))]
    {:result status :out (str out)}))

(describe "audit gate"
  (around [it]
    (let [root (str (fs/create-temp-dir {:prefix "audit-gate-"}))]
      (try
        (binding [*root* root]
          (with-redefs [shell/repo-root (constantly root)
                        config/role-audits? (fn [role] (contains? #{"coder" "hardener"} role))
                        store/ref-of (constantly "ref")
                        store/records-at (constantly [])
                        flow/last-milestone-commit (constantly "base123")]
            (it)))
        (finally (fs/delete-tree root)))))

  (it "does nothing for a role that does not audit"
    (should= {:result nil :out ""} (attempt "cleaner" "h1"))
    (should-not (fs/exists? (task-path))))

  (it "refuses the first attempt at a head and names the diff to review"
    (let [{:keys [result out]} (attempt "coder" "h1")]
      (should= 3 result)
      (should-contain "AUDIT_REQUIRED: t1 at h1" out)
      (should-contain "git diff base123..HEAD" out)
      (should= "h1\n1\n" (slurp (pending-file "coder")))))

  (it "counts every refusal, at the same head and at a new one"
    (attempt "coder" "h1")
    (attempt "coder" "h2")
    (should= "h2\n2\n" (slurp (pending-file "coder"))))

  (it "passes the retry at the refused head with the number of refusals"
    (attempt "coder" "h1")
    (attempt "coder" "h2")
    (should= {:result 2 :out ""} (attempt "coder" "h2"))
    (should= "h2\n2\n" (slurp (pending-file "coder"))))

  (it "refuses a role whose head another role was refused at"
    (attempt "coder" "h1")
    (should= 3 (:result (attempt "hardener" "h1")))
    (should= {:result 1 :out ""} (attempt "hardener" "h1")))

  (it "keeps each role's head and count apart"
    (attempt "coder" "h1")
    (attempt "coder" "h2")
    (attempt "hardener" "h2")
    (should= "h2\n2\n" (slurp (pending-file "coder")))
    (should= "h2\n1\n" (slurp (pending-file "hardener"))))

  (it "reads a role's pending file that holds only a head as one refusal"
    (fs/create-dirs (task-path))
    (spit (pending-file "coder") "h9\n")
    (should= {:result 1 :out ""} (attempt "coder" "h9")))

  (it "refuses every role's first attempt over a roleless file, counting 1"
    (roleless-file "h9\n2\n")
    (should= 3 (:result (attempt "coder" "h9")))
    (should= "h9\n1\n" (slurp (pending-file "coder")))
    (should= 3 (:result (attempt "hardener" "h9")))
    (should= {:result 1 :out ""} (attempt "coder" "h9")))

  (it "refuses a role's first attempt over a head-only roleless file"
    (roleless-file "h9\n")
    (should= 3 (:result (attempt "coder" "h9")))
    (should= {:result 1 :out ""} (attempt "coder" "h9")))

  (it "refuses a role's first attempt when no file is pending, even at an empty head"
    (should= 3 (:result (attempt "coder" "")))
    (should= "\n1\n" (slurp (pending-file "coder"))))

  (it "clear! removes only that role's pending state"
    (attempt "coder" "h1")
    (attempt "hardener" "h1")
    (audit/clear! "t1" "coder")
    (should-not (fs/exists? (pending-file "coder")))
    (should= "h1\n1\n" (slurp (pending-file "hardener"))))

  (it "clear! of the last pending role removes the task's audit path"
    (attempt "coder" "h1")
    (audit/clear! "t1" "coder")
    (should-not (fs/exists? (task-path)))
    (audit/clear! "t1" "coder")))
