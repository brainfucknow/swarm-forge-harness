(ns sf.config-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [speclj.core :refer [describe context it should= should should-not should-throw around]]
            [sf.config :as config]
            [sf.shell :as shell]))

(def ^:dynamic *dir* nil)
(def ^:dynamic *env* {})

(defn- conf [name text]
  (spit (str (fs/path *dir* "swarmforge" name)) text))

(defn- with-catalogue [run-spec]
  (let [dir (str (fs/create-temp-dir {:prefix "config-spec-"}))]
    (try
      (fs/create-dirs (fs/path dir "swarmforge" "roles"))
      (doseq [role ["specifier" "coder" "qa"]] (spit (str (fs/path dir "swarmforge" "roles" (str role ".md"))) role))
      (with-redefs [shell/repo-root (constantly dir)
                    shell/env (fn [name default] (get *env* name default))]
        (binding [*dir* dir]
          (conf "tiers.conf" "strong opus high gpt\nfast sonnet low mini\n")
          (conf "roles.conf" "specifier strong session\ncoder strong subagent audit\nqa fast subagent\n")
          (conf "routes.conf" "feature specifier,coder,qa\nquick coder,qa\n")
          (conf "tools.conf" (str "dry4clj bbmain github.com/x/dry4clj 0123456789abcdef0123456789abcdef01234567 dry4clj.core - -\n"
                                  "crap4clj bb github.com/x/crap4clj 0123456789abcdef0123456789abcdef01234567 crap coverage -\n"
                                  "speclj clj speclj/speclj 3.13.0 speclj.main - -\n"
                                  "sf-cs local swarmforge/bin/sf-cs - - - -\n"))
          (run-spec)))
      (finally (fs/delete-tree dir)))))

(defn- check
  "Runs sf check; returns [status stdout stderr lines]."
  []
  (let [err (java.io.StringWriter.)
        status (atom nil)
        out (with-out-str (binding [*err* err] (reset! status (shell/main-with "sf: " config/check))))]
    [@status out (str/split-lines (str err))]))

(describe "catalogue"
  (around [it] (with-catalogue it))

  (it "reads roles, tiers and audit flags"
    (should= ["specifier" "coder" "qa"] (config/pipeline))
    (should= "sonnet" (config/tier-field "fast" 2))
    (should (config/role-audits? "coder"))
    (should= nil (config/require-role "qa"))
    (should-throw clojure.lang.ExceptionInfo "unknown role 'nope' (see swarmforge/roles.conf)" (config/require-role "nope")))

  (context "routes"
    (it "resolves a route name or a role list"
      (should= "coder,qa" (config/resolve-route "quick"))
      (should= "specifier,qa" (config/resolve-route "specifier,qa")))

    (it "refuses an empty, unknown or out-of-order route"
      (should-throw clojure.lang.ExceptionInfo "empty route" (config/resolve-route ""))
      (should-throw clojure.lang.ExceptionInfo "unknown role 'x' in route 'coder,x' (see swarmforge/roles.conf)" (config/resolve-route "coder,x"))
      (should-throw clojure.lang.ExceptionInfo "route 'qa,coder' is out of order; follow swarmforge/roles.conf: specifier,coder,qa"
                    (config/resolve-route "qa,coder"))
      (should-throw clojure.lang.ExceptionInfo "route 'coder,coder' is out of order; follow swarmforge/roles.conf: specifier,coder,qa"
                    (config/resolve-route "coder,coder")))

    (it "defaults to SWARMFORGE_ROUTE, then the feature route, then the catalogue"
      (binding [*env* {"SWARMFORGE_ROUTE" "quick"}] (should= "coder,qa" (config/default-route)))
      (should= "specifier,coder,qa" (config/default-route))
      (conf "routes.conf" "")
      (should= "specifier,coder,qa" (config/default-route))))

  (context "optional roles"
    (it "requires explicit selection while preserving named and fallback routes"
      (conf "roles.conf" "specifier strong session\ncoder strong subagent audit\narchitecture-design-reviewer strong subagent optional\nhardener fast subagent\nsecurity-reviewer strong subagent optional audit\nqa fast subagent\n")
      (should= ["specifier" "coder" "architecture-design-reviewer" "hardener" "security-reviewer" "qa"] (config/pipeline))
      (should= "specifier,coder,qa" (config/default-route))
      (conf "routes.conf" "review coder,architecture-design-reviewer,hardener,security-reviewer,qa\n")
      (should= "coder,architecture-design-reviewer,hardener,security-reviewer,qa" (config/resolve-route "review"))
      (conf "routes.conf" "")
      (should= "specifier,coder,hardener,qa" (config/default-route))
      (should (config/role-audits? "security-reviewer"))
      (should= "coder,architecture-design-reviewer,hardener,security-reviewer,qa"
               (config/resolve-route "coder,architecture-design-reviewer,hardener,security-reviewer,qa"))
      (binding [*env* {"SWARMFORGE_ROUTE" "coder,security-reviewer,qa"}]
        (should= "coder,security-reviewer,qa" (config/default-route)))
      (should-throw clojure.lang.ExceptionInfo (config/resolve-route "security-reviewer,hardener,qa")))

    (it "does not expand a fallback that collides with a named route"
      (conf "roles.conf" "coder strong subagent\nsecurity-reviewer strong subagent optional\n")
      (conf "routes.conf" "coder coder,security-reviewer\n")
      (should= "coder" (config/default-route))
      (should= "coder,security-reviewer" (config/resolve-route "coder")))

    (it "refuses an empty fallback rather than enabling optional reviewers"
      (conf "roles.conf" "security-reviewer strong subagent optional\n")
      (conf "routes.conf" "")
      (should-throw clojure.lang.ExceptionInfo "empty route" (config/default-route)))

    (it "accepts optional and audit flags in either order and rejects duplicate or unknown flags"
      (doseq [role ["architecture-design-reviewer" "security-reviewer"]]
        (spit (str (fs/path *dir* "swarmforge/roles" (str role ".md"))) "fixture"))
      (doseq [flags ["optional" "optional audit" "audit optional"]]
        (conf "roles.conf" (str "coder strong subagent\nsecurity-reviewer strong subagent " flags "\nqa fast subagent\n"))
        (conf "routes.conf" "feature coder,qa\n")
        (should= 0 (first (check)))
        (should= (not= flags "optional") (config/role-audits? "security-reviewer")))
      (conf "routes.conf" "feature coder\n")
      (doseq [[flags problem] [["optional optional" "repeats flag 'optional'"]
                                ["audit audit" "repeats flag 'audit'"]
                                ["optional loud" "unknown flag 'loud'"]]]
        (conf "roles.conf" (str "coder strong subagent " flags "\n"))
        (let [[status _ errors] (check)]
          (should= 1 status)
          (should (some #(str/includes? % problem) errors))))))

  (context "gate roles"
    (it "keeps a gate off the pipeline and the routes and lists it with the gates"
      (spit (str (fs/path *dir* "swarmforge/roles/falsifier.md")) "fixture")
      (conf "roles.conf" "specifier strong session\nfalsifier strong subagent gate\ncoder strong subagent audit\nqa fast subagent\n")
      (should= ["specifier" "coder" "qa"] (config/pipeline))
      (should= ["falsifier"] (config/gates))
      (should (config/gate? "falsifier"))
      (should-not (config/gate? "coder"))
      (should= "specifier,coder,qa" (config/default-route))
      (should= nil (config/require-agent "falsifier"))
      (should= nil (config/require-agent "coder"))
      (should-throw clojure.lang.ExceptionInfo "unknown role 'nope' (see swarmforge/roles.conf)" (config/require-agent "nope"))
      (should-throw clojure.lang.ExceptionInfo "unknown role 'falsifier' (see swarmforge/roles.conf)" (config/require-role "falsifier"))
      (should-throw clojure.lang.ExceptionInfo "'falsifier' in route 'specifier,falsifier,coder' is a gate, not a stage; it runs at the specification gate"
                    (config/resolve-route "specifier,falsifier,coder"))
      (should= [0 "CHECK: ok (3 roles, 2 routes)\n" [""]] (check)))

    (it "has no gates when no role is flagged gate"
      (should= [] (config/gates)))

    (it "refuses a gate that audits, is optional, runs in session, or sits on a route"
      (spit (str (fs/path *dir* "swarmforge/roles/falsifier.md")) "fixture")
      (doseq [[row problem] [["falsifier strong subagent gate audit" "'falsifier' is a gate and cannot also be audit or optional"]
                             ["falsifier strong subagent optional gate" "'falsifier' is a gate and cannot also be audit or optional"]
                             ["falsifier strong session gate" "'falsifier' is a gate and must run as subagent"]]]
        (conf "roles.conf" (str "specifier strong session\n" row "\ncoder strong subagent\nqa fast subagent\n"))
        (let [[status _ errors] (check)]
          (should= 1 status)
          (should (some #(str/includes? % problem) errors))))
      (conf "roles.conf" "specifier strong session\nfalsifier strong subagent gate\ncoder strong subagent\nqa fast subagent\n")
      (conf "routes.conf" "feature specifier,falsifier,coder,qa\n")
      (let [[status _ errors] (check)]
        (should= 1 status)
        (should (some #(str/includes? % "routes.conf: feature: 'falsifier' in route 'specifier,falsifier,coder,qa' is a gate, not a stage") errors)))))

  (context "spec-judge role"
    (it "keeps the spec-judge off the pipeline, the gates and the routes, and names it"
      (doseq [role ["falsifier" "spec-judge"]] (spit (str (fs/path *dir* "swarmforge/roles" (str role ".md"))) "fixture"))
      (conf "roles.conf" "specifier strong session\nfalsifier strong subagent gate\nspec-judge strong subagent spec-judge\ncoder strong subagent\nqa fast subagent\n")
      (should= ["specifier" "coder" "qa"] (config/pipeline))
      (should= ["falsifier"] (config/gates))
      (should= "spec-judge" (config/spec-judge))
      (should (config/spec-judge? "spec-judge"))
      (should-not (config/spec-judge? "falsifier"))
      (should-not (config/gate? "spec-judge"))
      (should= nil (config/require-agent "spec-judge"))
      (should-throw clojure.lang.ExceptionInfo "'spec-judge' in route 'specifier,spec-judge,coder' is a spec-judge, not a stage; it runs at the specification gate"
                    (config/resolve-route "specifier,spec-judge,coder"))
      (should= [0 "CHECK: ok (3 roles, 2 routes)\n" [""]] (check)))

    (it "has no spec-judge when no role is flagged spec-judge"
      (should= nil (config/spec-judge)))

    (it "refuses a spec-judge that is also audit, optional or gate, runs in session, or comes twice"
      (doseq [role ["spec-judge" "referee"]] (spit (str (fs/path *dir* "swarmforge/roles" (str role ".md"))) "fixture"))
      (doseq [[rows problem] [["spec-judge strong subagent spec-judge audit" "'spec-judge' is a spec-judge and cannot also be audit, optional or gate"]
                              ["spec-judge strong subagent optional spec-judge" "'spec-judge' is a spec-judge and cannot also be audit, optional or gate"]
                              ["spec-judge strong subagent gate spec-judge" "'spec-judge' is a spec-judge and cannot also be audit, optional or gate"]
                              ["spec-judge strong session spec-judge" "'spec-judge' is a spec-judge and must run as subagent"]
                              ["spec-judge strong subagent spec-judge\nreferee strong subagent spec-judge" "'referee' is a second spec-judge; roles.conf has one"]]]
        (conf "roles.conf" (str "specifier strong session\n" rows "\ncoder strong subagent\nqa fast subagent\n"))
        (let [[status _ errors] (check)]
          (should= 1 status)
          (should (some #(str/includes? % problem) errors)))))

    (it "refuses the old flag judge as an unknown flag that names spec-judge"
      (spit (str (fs/path *dir* "swarmforge/roles/referee.md")) "fixture")
      (conf "roles.conf" "specifier strong session\nreferee strong subagent judge\ncoder strong subagent\nqa fast subagent\n")
      (let [[status _ errors] (check)]
        (should= 1 status)
        (should (some #(str/includes? % "roles.conf: 'referee' has unknown flag 'judge' (only audit, optional, gate, spec-judge)") errors))))

    (it "refuses a route that names the spec-judge"
      (spit (str (fs/path *dir* "swarmforge/roles/spec-judge.md")) "fixture")
      (conf "roles.conf" "specifier strong session\nspec-judge strong subagent spec-judge\ncoder strong subagent\nqa fast subagent\n")
      (conf "routes.conf" "feature specifier,spec-judge,coder,qa\n")
      (let [[status _ errors] (check)]
        (should= 1 status)
        (should (some #(str/includes? % "routes.conf: feature: 'spec-judge' in route 'specifier,spec-judge,coder,qa' is a spec-judge, not a stage") errors)))))

  (context "check"
    (it "accepts a valid catalogue, the bbmain kind and the coverage rewrite"
      (should= [0 "CHECK: ok (3 roles, 2 routes)\n" [""]] (check)))

    (it "reports missing tiers and roles files"
      (fs/delete (fs/path *dir* "swarmforge" "tiers.conf"))
      (fs/delete (fs/path *dir* "swarmforge" "roles.conf"))
      (let [[status _ lines] (check)]
        (should= 1 status)
        (should (some #{"CHECK: tiers.conf: missing"} lines))
        (should (some #{"CHECK: roles.conf: missing"} lines))))

    (it "reports every problem of a row"
      (conf "tiers.conf" "strong opus high gpt\nstrong opus\n")
      (conf "roles.conf" (str "specifier strong session\nBad strong subagent\ndone strong subagent\nspecifier strong subagent\n"
                              "coder weak robot\nqa strong subagent flag\nx\n"))
      (conf "routes.conf" "feature specifier,qa\nfeature qa,specifier\nlonely\n")
      (conf "tools.conf" "a java s - t rot13 -\na bb github.com/x/a 0123abc t - -\nshort bb\nc bb github.com/x/a aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa t - -\n")
      (should= ["CHECK: tiers.conf: 'strong' needs exactly <tier> <claude-model> <codex-effort> <copilot-model> [<kilo-model>]"
                "CHECK: tiers.conf: tier 'strong' listed twice"
                "CHECK: roles.conf: role name 'Bad' must be lowercase letters, digits, and hyphens"
                "CHECK: roles/Bad.md: missing role prompt for 'Bad'"
                "CHECK: roles.conf: 'done' is reserved"
                "CHECK: roles/done.md: missing role prompt for 'done'"
                "CHECK: roles.conf: role 'specifier' listed twice"
                "CHECK: roles.conf: 'coder' uses unknown tier 'weak' (see tiers.conf)"
                "CHECK: roles.conf: 'coder' runs-as must be subagent or session, not 'robot'"
                "CHECK: roles.conf: 'qa' has unknown flag 'flag' (only audit, optional, gate, spec-judge)"
                "CHECK: roles.conf: 'x' needs <role> <tier> <runs-as> [audit] [optional]"
                "CHECK: routes.conf: route 'feature' listed twice"
                "CHECK: routes.conf: feature: route 'qa,specifier' is out of order; follow swarmforge/roles.conf: specifier,Bad,done,specifier,coder,qa,x"
                "CHECK: routes.conf: 'lonely' needs exactly <name> <role,role,...>"
                "CHECK: tools.conf: 'a' has unknown kind 'java'"
                "CHECK: tools.conf: 'a' has unknown rewrite 'rot13'"
                "CHECK: tools.conf: tool 'a' listed twice"
                "CHECK: tools.conf: 'a' needs a 40-digit lowercase hex commit SHA as its ref, not '0123abc'"
                "CHECK: tools.conf: 'short' needs exactly <name> <kind> <source> <ref> <task> <rewrite> <needs>"
                "CHECK: tools.conf: 'c' pins github.com/x/a at a different ref than 'a'"
                (str "sf: 20 configuration problem(s) in " *dir* "/swarmforge")]
               (nth (check) 2)))

    (it "reports an empty roles file"
      (conf "roles.conf" "")
      (should (some #{"CHECK: roles.conf: no roles"} (nth (check) 2))))))
