(ns sf.role-kind-spec
  (:require [speclj.core :refer [describe it should should-not around]]
            [sf.config :as config]
            [sf.repo-support :refer [with-repo]]))

(describe "gate? and judge?"
  (around [it] (with-repo {"roles.conf" "specifier frontier subagent\njudge strong subagent judge\nfalsifier strong subagent gate\n"} it))

  (it "are false for a role with no flags"
    (should-not (config/gate? "specifier"))
    (should-not (config/judge? "specifier")))

  (it "name only the flagged role"
    (should (config/gate? "falsifier"))
    (should (config/judge? "judge"))
    (should-not (config/gate? "judge"))
    (should-not (config/judge? "falsifier"))))
