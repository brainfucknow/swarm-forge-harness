(ns sf.config-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [around describe it should-contain should= should-not-contain]]
            [sf.config :as config]
            [sf.repo-support :as support :refer [*dir* with-repo]]
            [sf.shell :as shell]))

(defn- check-with
  "The stderr of sf check in a repository whose catalogue has conf over the defaults."
  [conf]
  (with-repo conf #(support/captured config/check)))

(describe "check"
  (it "accepts the catalogue"
    (should= {:out "CHECK: ok (3 roles, 2 routes)" :err "" :status 0} (dissoc (check-with {}) :result)))

  (it "names a missing catalogue file"
    (with-repo (fn []
                 (fs/delete (fs/path *dir* "swarmforge" "tiers.conf"))
                 (fs/delete (fs/path *dir* "swarmforge" "roles.conf"))
                 (let [{:keys [err status]} (support/captured config/check)]
                   (should= 1 status)
                   (should-contain "tiers.conf: missing" err)
                   (should-contain "roles.conf: missing" err)))))

  (it "names every problem of a tier"
    (let [{:keys [err status]} (check-with {"tiers.conf" "frontier inherit\nfast a b c\nfast a b c\n"})]
      (should= 1 status)
      (should-contain "'frontier' needs exactly" err)
      (should-contain "tier 'fast' listed twice" err)))

  (it "names every problem of a role"
    (let [{:keys [err]} (check-with {"roles.conf" (str "specifier frontier subagent\n"
                                                       "specifier frontier subagent\n"
                                                       "Bad_Name strong subagent\n"
                                                       "done strong subagent\n"
                                                       "ghost nothing sidecar flagged\n"
                                                       "coder strong subagent loud\n"
                                                       "short\n")})]
      (should-contain "role 'specifier' listed twice" err)
      (should-contain "role name 'Bad_Name' must be lowercase" err)
      (should-contain "'done' is reserved" err)
      (should-contain "'ghost' uses unknown tier 'nothing'" err)
      (should-contain "'ghost' runs-as must be subagent or session" err)
      (should-contain "roles/ghost.md: missing role prompt" err)
      (should-contain "'coder' has unknown flag 'loud'" err)
      (should-contain "'short' needs <role> <tier> <runs-as>" err)))

  (it "names an empty catalogue"
    (should-contain "roles.conf: no roles" (:err (check-with {"roles.conf" "# nothing\n"}))))

  (it "names every problem of a route"
    (let [{:keys [err]} (check-with {"routes.conf" (str "feature specifier,coder\n"
                                                        "feature coder\n"
                                                        "back coder,specifier\n"
                                                        "ghost ghost\n"
                                                        "lonely\n")})]
      (should-contain "route 'feature' listed twice" err)
      (should-contain "back: route 'coder,specifier' is out of order" err)
      (should-contain "ghost: unknown role 'ghost' in route 'ghost'" err)
      (should-contain "'lonely' needs exactly" err)))

  (it "names every problem of a tool"
    (let [{:keys [err]} (check-with {"tools.conf" (str "a bb src t - -\n"
                                                       "a bb src t - -\n"
                                                       "b zzz src t - -\n"
                                                       "c bb src t zzz -\n"
                                                       "d bb\n")})]
      (should-contain "tool 'a' listed twice" err)
      (should-contain "'b' has unknown kind 'zzz'" err)
      (should-contain "'c' has unknown rewrite 'zzz'" err)
      (should-contain "'d' needs exactly" err)))

  (it "counts the problems"
    (should-contain "2 configuration problem(s)"
                    (:err (check-with {"tiers.conf" "frontier inherit\nfast a b c\nfast a b c\nstrong a b c\n"})))))

(describe "resolve-route"
  (around [it] (with-repo it))

  (it "takes a route name or a list"
    (should= "specifier,coder,cleaner" (config/resolve-route "feature"))
    (should= "coder,cleaner" (config/resolve-route "coder,cleaner")))

  (it "refuses an empty route, an unknown role, and a route out of order"
    (should-contain "empty route" (:err (support/captured #(config/resolve-route ""))))
    (should-contain "unknown role 'nobody' in route 'nobody'" (:err (support/captured #(config/resolve-route "nobody"))))
    (should-contain "out of order; follow swarmforge/roles.conf: specifier,coder,cleaner"
                    (:err (support/captured #(config/resolve-route "coder,specifier"))))))

(describe "default-route"
  (around [it] (with-repo it))

  (it "is the SWARMFORGE_ROUTE route"
    (with-redefs [shell/env (fn [name default] (if (= "SWARMFORGE_ROUTE" name) "quick" default))]
      (should= "coder" (config/default-route))))

  (it "is the feature route"
    (should= "specifier,coder,cleaner" (config/default-route)))

  (it "is every role when there is no feature route"
    (support/write-conf! *dir* "routes.conf" "quick coder\n")
    (should= "specifier,coder,cleaner" (config/default-route))))

(describe "audit"
  (around [it] (with-repo it))

  (it "is a flag of the role"
    (should= true (config/role-audits? "coder"))
    (should= false (config/role-audits? "cleaner"))
    (should-not-contain "audit" (config/pipeline))))
