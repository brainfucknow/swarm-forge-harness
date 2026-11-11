(ns sf.review-audit-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [around describe it should should-not]]
            [sf.repo-support :refer [*dir* with-repo write-conf!]]
            [sf.task :as task]))

(defn- quietly [f]
  (binding [*out* (java.io.StringWriter.)] (try (f) (catch clojure.lang.ExceptionInfo _ nil))))

(describe "review by an audited role"
  (around [it] (with-repo it))

  (it "keeps the audit state until the record is written, then clears it"
    (write-conf! *dir* "roles.conf" "specifier frontier subagent audit\ncoder strong subagent audit\ncleaner fast subagent\n")
    (let [id (clojure.string/trim (with-out-str (task/new-task ["Add login"])))
          pending (fs/path *dir* ".swarmforge" "audit" id)]
      (quietly #(task/review [id]))
      (should (fs/exists? pending))
      (quietly #(task/review [id]))
      (should-not (fs/exists? pending)))))
