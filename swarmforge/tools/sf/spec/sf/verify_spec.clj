(ns sf.verify-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [around describe it should should= should-contain]]
            [sf.repo-support :refer [captured]]
            [sf.verify :as verify]))

(def ^:dynamic *dir* nil)

(defn- with-checkout [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "sf-verify-"}))]
    (try
      (binding [*dir* dir] (run-spec))
      (finally (fs/delete-tree dir)))))

(defn- write-conf! [text]
  (fs/create-dirs (fs/path *dir* "swarmforge"))
  (spit (str (fs/path *dir* "swarmforge" "verify.conf")) text))

(describe "the verification commands"
  (it "are the lines in file order, without blank lines and comment lines"
    (should= ["true" "exit 3" "make test"]
             (verify/commands "# checks\n\ntrue\n   \n  # indented comment\nexit 3\n  make test  \n")))

  (it "are none without text"
    (should= [] (verify/commands nil))
    (should= [] (verify/commands "# no commands yet\n\n"))))

(describe "verifying a checkout"
  (around [it] (with-checkout it))

  (it "passes unverified when verify.conf is missing or has no command"
    (should= {:status 0 :result nil :out "VERIFY: none configured" :err ""} (captured #(verify/check! *dir*)))
    (write-conf! "# none\n")
    (should= "VERIFY: none configured" (:out (captured #(verify/check! *dir*)))))

  (it "runs each command from the checkout root and prints a line for each that passes"
    (write-conf! "touch made\ntest -e made\n")
    (should= {:status 0 :result nil :out "VERIFY: passed: touch made\nVERIFY: passed: test -e made" :err ""}
             (captured #(verify/check! *dir*)))
    (should (fs/exists? (fs/path *dir* "made"))))

  (it "stops at the first command that fails and returns it with its exit status"
    (write-conf! "true\nexit 3\ntouch never\n")
    (let [{:keys [result out]} (captured #(verify/check! *dir*))]
      (should= {:command "exit 3" :exit 3} result)
      (should= "VERIFY: passed: true" out)
      (should= false (fs/exists? (fs/path *dir* "never")))))

  (it "sends the output of the commands to stderr"
    (write-conf! "echo $((40 + 2)); echo $((40 + 3)) >&2\n")
    (let [{:keys [out err]} (captured #(verify/check! *dir*))]
      (should= "VERIFY: passed: echo $((40 + 2)); echo $((40 + 3)) >&2" out)
      (should-contain "42" err)
      (should-contain "43" err))))
