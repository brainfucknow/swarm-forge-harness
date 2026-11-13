(ns sf.install-spec
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [sf.repo-support :refer [*dir* with-repo]]
            [speclj.core :refer :all]))

(def checkout (str (fs/canonicalize (fs/path (fs/parent *file*) "../../../../.."))))
(def protected ["swarmforge/constitution/articles/project.md" "swarmforge/roles.conf"
                "swarmforge/routes.conf" "swarmforge/tiers.conf" "swarmforge/tools.conf"])
(def ^:dynamic *source* checkout)

(defn- write-file! [root relative text]
  (let [path (fs/path root relative)]
    (fs/create-dirs (fs/parent path))
    (spit (str path) text)))

(defn- install! []
  (process/shell {:dir *dir* :out :string :err :string :continue true}
                 "bash" (str (fs/path *source* "install.sh")) *dir*))

(defn- installed-sf [& args]
  (apply process/shell {:dir *dir* :out :string :err :string :continue true}
         (str (fs/path *dir* "swarmforge/bin/sf")) args))

(defn- with-source [example]
  (let [source (fs/path *dir* "source checkout")]
    (fs/create-dirs source)
    (fs/copy (fs/path checkout "install.sh") (fs/path source "install.sh"))
    (doseq [relative ["swarmforge" ".agents/skills" ".claude/skills" ".kilo/command"]]
      (fs/copy-tree (fs/path checkout relative) (fs/path source relative)))
    (binding [*source* (str source)] (example))))

(defn- contents [paths]
  (into {} (map (fn [path] [path (slurp (str (fs/path *dir* path)))]) paths)))

(defn- review-lines [result]
  (filterv #(str/starts-with? % "UNSHIPPED ") (str/split-lines (:out result))))

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
        (should= content (slurp (str (fs/path *dir* path)))))))

  (it "seeds only missing defaults and never creates or overwrites verify.conf"
    (doseq [path protected] (fs/delete-if-exists (fs/path *dir* path)))
    (should= 0 (:exit (install!)))
    (doseq [path protected]
      (should= (slurp (str (fs/path checkout path))) (slurp (str (fs/path *dir* path)))))
    (should-not (fs/exists? (fs/path *dir* "swarmforge/verify.conf")))
    (write-file! *dir* "swarmforge/verify.conf" "project verification\n")
    (write-file! *dir* "swarmforge/constitution/articles/project.md" "project owns this\n")
    (fs/delete (fs/path *dir* "swarmforge/routes.conf"))
    (let [before (contents (remove #{"swarmforge/routes.conf"} protected))]
      (should= 0 (:exit (install!)))
      (should= before (contents (keys before)))
      (should= (slurp (str (fs/path checkout "swarmforge/routes.conf")))
               (slurp (str (fs/path *dir* "swarmforge/routes.conf")))))
    (should= "project verification\n" (slurp (str (fs/path *dir* "swarmforge/verify.conf")))))

  (it "regenerates adapters from the consumer catalogue and keeps repeat installs idempotent"
    (should= 0 (:exit (install!)))
    (write-file! *dir* "swarmforge/roles.conf"
                 "specifier frontier subagent\ncoder fast subagent\ncleaner fast subagent\n")
    (write-file! *dir* "swarmforge/routes.conf" "feature coder\n")
    (should= 0 (:exit (installed-sf "adapters")))
    (fs/delete (fs/path *dir* ".swarmforge/adapters.edn"))
    (let [paths (concat protected [".codex/agents/coder.toml" ".github/agents/coder.agent.md"
                                  "AGENTS.md" "CLAUDE.md" ".github/copilot-instructions.md"
                                  ".gitignore" "swarmforge/.gitignore"])
          before (contents paths)]
      (should= 0 (:exit (install!)))
      (should= before (contents paths))
      (should= 0 (:exit (install!)))
      (should= before (contents paths))
      (should= 0 (:exit (installed-sf "check")))
      (should-contain "coder" (:out (installed-sf "roles")))
      (should-contain "feature" (:out (installed-sf "routes")))))

  (it "keeps invalid consumer configuration intact and permits retry after correction"
    (should= 0 (:exit (install!)))
    (let [roles (slurp (str (fs/path *dir* "swarmforge/roles.conf")))
          invalid "not a valid catalogue\n"]
      (write-file! *dir* "swarmforge/roles.conf" invalid)
      (let [{:keys [exit err]} (install!)]
        (should-not= 0 exit)
        (should-contain "roles.conf" err)
        (should= invalid (slurp (str (fs/path *dir* "swarmforge/roles.conf")))))
      (write-file! *dir* "swarmforge/roles.conf" roles)
      (should= 0 (:exit (install!)))))

  (it "refuses symlink and nonregular configuration destinations without modifying their targets"
    (should= 0 (:exit (install!)))
    (let [before (contents protected)
          outside (fs/path *dir* "external.txt")]
      (spit (str outside) "external contents\n")
      (doseq [path protected]
        (let [destination (fs/path *dir* path)]
          (fs/delete destination)
          (fs/create-sym-link destination outside)
          (let [{:keys [exit err]} (install!)]
            (should-not= 0 exit)
            (should-contain "configuration conflict" err)
            (should (fs/sym-link? destination))
            (should= "external contents\n" (slurp (str outside))))
          (fs/delete destination)
          (fs/create-dirs destination)
          (should-not= 0 (:exit (install!)))
          (should (fs/directory? destination))
          (fs/delete-tree destination)
          (write-file! *dir* path (before path))))
      (should= 0 (:exit (install!)))))

  (it "refreshes shipped runtime and lists unshipped payload files without deleting them"
    (with-source
      #(let [retired "swarmforge/roles/retired.md"
             old-tool "swarmforge/tools/retired/src/Old.clj"
             user-guide "swarmforge/languages/My Guide.md"
             user-skill ".agents/skills/custom/SKILL.md"
             runtime "swarmforge/handoff-protocol.md"]
         (write-file! *source* retired "retired role\n")
         (write-file! *source* old-tool "retired source\n")
         (should= 0 (:exit (install!)))
         (fs/delete (fs/path *source* retired))
         (fs/delete (fs/path *source* old-tool))
         (write-file! *dir* user-guide "user guide\n")
         (write-file! *dir* user-skill "user skill\n")
         (write-file! *dir* "swarmforge/tools/sf/.cpcache/old" "cache\n")
         (write-file! *dir* ".codex/agents/user.toml" "user agent\n")
         (write-file! *source* runtime "updated runtime payload\n")
         (let [unknown [retired old-tool user-guide user-skill]
               before (contents unknown)
               first-result (install!)
               second-result (install!)]
           (should= 0 (:exit first-result))
           (should= 0 (:exit second-result))
           (should= before (contents unknown))
           (should= "updated runtime payload\n" (slurp (str (fs/path *dir* runtime))))
           (should= (review-lines first-result) (review-lines second-result))
           (should= (mapv (fn [path] (str "UNSHIPPED (manual review; may be a user file): " path)) (sort unknown))
                    (review-lines first-result)))))))
