(ns sf.install-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [sf.repo-support :refer [*dir* with-repo]]
            [speclj.core :refer :all]))

(def checkout (str (fs/canonicalize (fs/path (fs/parent *file*) "../../../../.."))))
(def protected ["swarmforge/constitution/articles/project.md" "swarmforge/roles.conf"
                "swarmforge/routes.conf" "swarmforge/tiers.conf" "swarmforge/tools.conf"])

(defn- write-file! [root relative text]
  (let [path (fs/path root relative)]
    (fs/create-dirs (fs/parent path))
    (spit (str path) text)))

(defn- install! []
  (process/shell {:dir *dir* :out :string :err :string :continue true}
                 "bash" (str (fs/path checkout "install.sh")) *dir*))

(describe "Installer updates"
  (around [example] (with-repo example))

  (it "preserves existing project configuration byte-for-byte on reinstall"
    (should= 0 (:exit (install!)))
    (doseq [path (rest protected)]
      (write-file! *dir* path (str (slurp (str (fs/path *dir* path))) "# consumer setting\n")))
    (write-file! *dir* "swarmforge/constitution/articles/project.md" "project language and policy chosen here\n")
    (let [before (into {} (map (fn [path] [path (slurp (str (fs/path *dir* path)))]) protected))
          {:keys [exit err]} (install!)]
      (should= 0 exit)
      (doseq [[path content] before]
        (should= content (slurp (str (fs/path *dir* path))))))))
