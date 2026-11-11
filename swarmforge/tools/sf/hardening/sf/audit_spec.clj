(ns sf.audit-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer :all]
            [sf.audit :as audit]
            [sf.config :as config]
            [sf.flow :as flow]
            [sf.shell :as shell]
            [sf.store :as store]))

(def ^:dynamic *root* nil)

(defn- pending-file [] (str (fs/path *root* ".swarmforge" "audit" "t1")))

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
                        config/role-audits? (fn [role] (= "coder" role))
                        store/ref-of (constantly "ref")
                        store/records-at (constantly [])
                        flow/last-milestone-commit (constantly "base123")]
            (it)))
        (finally (fs/delete-tree root)))))

  (it "does nothing for a role that does not audit"
    (should= {:result nil :out ""} (attempt "cleaner" "h1"))
    (should-not (fs/exists? (pending-file))))

  (it "refuses the first attempt at a head and names the diff to review"
    (let [{:keys [result out]} (attempt "coder" "h1")]
      (should= 3 result)
      (should-contain "AUDIT_REQUIRED: t1 at h1" out)
      (should-contain "git diff base123..HEAD" out)
      (should= "h1\n1\n" (slurp (pending-file)))))

  (it "counts every refusal, at the same head and at a new one"
    (attempt "coder" "h1")
    (attempt "coder" "h2")
    (should= "h2\n2\n" (slurp (pending-file))))

  (it "passes the retry at the refused head with the number of refusals"
    (attempt "coder" "h1")
    (attempt "coder" "h2")
    (should= {:result 2 :out ""} (attempt "coder" "h2"))
    (should= "h2\n2\n" (slurp (pending-file))))

  (it "treats a pending file that holds only a head as one refusal"
    (fs/create-dirs (fs/parent (pending-file)))
    (spit (pending-file) "h9\n")
    (should= {:result 1 :out ""} (attempt "coder" "h9")))

  (it "counts the next refusal after a head-only file as the second"
    (fs/create-dirs (fs/parent (pending-file)))
    (spit (pending-file) "h9\n")
    (attempt "coder" "h10")
    (should= "h10\n2\n" (slurp (pending-file))))

  (it "refuses a role's first attempt when no file is pending, even at an empty head"
    (should= 3 (:result (attempt "coder" "")))
    (should= "\n1\n" (slurp (pending-file))))

  (it "clear! removes the pending state and tolerates none"
    (attempt "coder" "h1")
    (audit/clear! "t1")
    (should-not (fs/exists? (pending-file)))
    (audit/clear! "t1")))
