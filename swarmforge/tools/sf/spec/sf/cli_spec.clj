(ns sf.cli-spec
  (:require [clojure.string :as str]
            [speclj.core :refer [around describe it should-contain should=]]
            [sf.cli :as cli]
            [sf.repo-support :as support :refer [with-repo]]
            [sf.shell :as shell]))

(defn- sf [& args]
  (support/captured #(cli/main (vec args))))

(describe "main"
  (around [it] (with-repo it))

  (it "prints the usage without a command and for help"
    (doseq [args [[] [""] ["-h"] ["--help"] ["help"]]]
      (should-contain "usage: sf <command>" (:out (apply sf args)))))

  (it "stops at an unknown command"
    (should= {:out "" :err "unknown command 'nope' (try sf --help)" :status 1}
             (dissoc (sf "nope") :result)))

  (it "prints the roles and the routes"
    (should= "specifier\ncoder\ncleaner" (:out (sf "roles")))
    (should= (:out (sf "roles")) (:out (sf "pipeline")))
    (should= "feature    specifier,coder,cleaner\nquick      coder" (:out (sf "routes"))))

  (it "checks the catalogue"
    (should= "CHECK: ok (3 roles, 2 routes)" (:out (sf "check"))))

  (it "runs a task command with the arguments after it"
    (let [id (:out (sf "new" "Add login"))]
      (should= "specifier" (:out (sf "stage" id)))
      (should-contain (str "HANDOFF: " id " specifier -> coder") (:out (sf "handoff" id "-m" "spec")))))

  (it "hands the rest to sf tool and fails with its status"
    (should-contain "usage: sf tool" (:out (sf "tool" "--help")))
    (should= 1 (:status (sf "tool" "nope")))))

(describe "main-with"
  (it "is 0 and prints nothing after a command that succeeds"
    (should= {:out "" :err "" :status 0 :result 0} (support/captured #(shell/main-with "p: " (constantly nil)))))

  (it "prints the message after the prefix and gives the exit status of the failure"
    (let [{:keys [err result]} (support/captured #(shell/main-with "p: " (fn [] (shell/fail 3 "broke"))))]
      (should= ["p: broke" 3] [err result])))

  (it "is 1 for an exception without a status and stays quiet for a blank message"
    (let [{:keys [err result]} (support/captured #(shell/main-with "p: " (fn [] (throw (Exception. "boom")))))]
      (should= ["p: boom" 1] [err result]))
    (should= {:out "" :err "" :status 0 :result 3} (support/captured #(shell/main-with "p: " (fn [] (shell/fail 3 nil)))))))
