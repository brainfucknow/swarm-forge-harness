(ns sf.unclosed-cli-spec
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [sf.cli :as cli]
            [sf.repo-support :as support :refer [*dir* git with-repo]]
            [speclj.core :refer :all]))

(defn- sf [& args]
  (support/captured #(cli/main (vec args))))

(defn- recover! []
  (let [id (:out (sf "new" "Hosted work" "--route" "coder"))]
    (should= 0 (:status (sf "handoff" id)))
    (git *dir* "merge" "-q" "--no-ff" "-m" "Host merge" (str "swarm/" id))
    (git *dir* "branch" "-D" (str "swarm/" id))
    id))

(describe "Public CLI for unclosed histories"
  (around [example]
    (with-repo {"roles.conf" "coder strong subagent\n" "routes.conf" "feature coder\n"} example))

  (it "does not discover task-only or unknown-type trailer commits as tasks"
    (doseq [[id type] [["phantom-1111111" nil] ["phantom-2222222" "unknown"]]]
      (git *dir* "commit" "-q" "--allow-empty" "-m"
           (str "Incidental\n\nSwarmForge-Task: " id "\n"
                (when type (str "SwarmForge-Record: " type "\n")))))
    (should= "" (:out (sf "harvest")))
    (should= 1 (:status (sf "show" "phantom-1111111"))))

  (it "prints the recovered last recorded stage and route"
    (let [id (recover!)]
      (should= {:out "done" :err "" :status 0} (dissoc (sf "stage" id) :result))
      (should= {:out "coder" :err "" :status 0} (dissoc (sf "route" id) :result))))

  (it "refuses run even when recovered history last reached done"
    (let [id (recover!)
          {:keys [status err]} (sf "run" id "claude")]
      (should= 1 status)
      (should-contain id err)
      (should-contain "unclosed" err)))

  (it "refuses dispatch before attempting to create a worktree"
    (let [id (recover!)
          {:keys [status err]} (sf "dispatch" "coder" id "claude")]
      (should= 1 status)
      (should-contain id err)
      (should-contain "unclosed" err))))
