(ns sf.acceptance-identity-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [sf.repo-support :refer [*dir* with-repo]]
            [speclj.core :refer :all]))

(def checkout (str (fs/canonicalize (fs/path (fs/parent *file*) "../../../../.."))))

(describe "Acceptance Git identity isolation"
  (around [example] (with-repo example))

  (it "does not inherit system identity when a scenario has an empty Git home"
    (let [home (fs/create-dirs (fs/path *dir* "empty-home"))
          system (fs/path *dir* "system.gitconfig")
          _ (spit (str system) "[user]\n name = System Author\n email = system@example.test\n")
          {:keys [exit out err]}
          (process/shell {:dir *dir* :out :string :err :string :continue true}
            "bb" "--config" "/dev/null" "-cp" (str (fs/path checkout "test/acceptance/src"))
            "-e" (str "(require '[acceptance.support :as fixtures]) "
                      "(let [[repo home system] *command-line-args*] "
                      "(prn (select-keys (fixtures/run repo [\"git\" \"config\" \"--get\" \"user.name\"] "
                      "{\"HOME\" home \"GIT_CONFIG_SYSTEM\" system}) [:exit :out])))")
            *dir* (str home) (str system))]
      (should= 0 exit)
      (should= {:exit 1 :out ""} (edn/read-string out)))))
