(ns sf.dispatch-exit-spec
  (:require [babashka.process :as p]
            [speclj.core :refer [around describe it should=]]
            [sf.dispatch :as dispatch]
            [sf.repo-support :refer [with-repo]]
            [sf.shell :as shell]
            [sf.task :as task]))

(describe "dispatch"
  (around [it] (with-repo it))

  (it "asks the backend run to return its exit status instead of throwing"
    (let [id (shell/chomp (with-out-str (task/new-task ["Add login"])))
          real p/shell
          options (atom nil)]
      (with-redefs [p/shell (fn [opts]
                              (if (= "git" (first (:cmd opts)))
                                (real opts)
                                (do (reset! options opts) {:exit 0})))]
        (dispatch/dispatch ["coder" id "claude"]))
      (should= true (:continue @options)))))
