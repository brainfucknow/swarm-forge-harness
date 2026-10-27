(ns sfjs.acceptance.world
  "The scratch environment one scenario execution runs in: a git repository,
  fake JavaScript tools, and the sf-js run."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def javascript-dir
  (str (nth (iterate fs/parent (fs/canonicalize *file*)) 5)))
(def swarmforge-dir (str (fs/canonicalize (fs/path javascript-dir ".." ".."))))
(def sf-js (str (fs/path swarmforge-dir "bin" "sf-js")))
(def fake-tool (str (fs/path javascript-dir "acceptance" "fake-tool.sh")))

(def fake-names ["node" "npm" "npx" "pnpm" "yarn" "bun" "bunx"])
(def system-names
  "The real commands sf-js and the fakes need; nothing else is on PATH."
  ["bash" "bb" "git" "mkdir" "dirname"])

(defn- scratch-root []
  (or (System/getenv "SFJS_SCRATCH")
      (str (fs/normalize (fs/path swarmforge-dir ".." "tmp" "sf-js" "scratch")))))

(defn fresh []
  (let [root (scratch-root)]
    (fs/create-dirs root)
    (let [dir (str (fs/canonicalize (fs/create-temp-dir {:dir root :prefix "world"})))]
      {:dir dir
       :repo (str (fs/path dir "repo"))
       :bin (str (fs/path dir "fakebin"))
       :system-bin (str (fs/path dir "systembin"))
       :state (str (fs/path dir "state"))
       :main-files {}
       :package nil
       :env {}})))

(defn cleanup [{:keys [dir]}]
  (when dir (fs/delete-tree dir)))

(defn path [{:keys [repo]} file]
  (str (fs/path repo file)))

(defn git [{:keys [repo]} & args]
  (let [result (apply p/shell {:dir repo :out :string :err :string :continue true} "git" args)]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "git " (str/join " " args) ": " (:err result)) {})))
    (:out result)))

(defn numbered-lines [file n]
  (str/join (for [i (range 1 (inc n))] (str "// " file " line " i "\n"))))

(defn write-file [world file content]
  (let [target (path world file)]
    (fs/create-dirs (fs/parent target))
    (spit target content)))

(defn create-repo
  "A repository whose `base` branch holds the main files, checked out on a
  task branch made from it."
  [{:keys [repo main-files] :as world} base]
  (fs/create-dirs repo)
  (git world "init" "--quiet" (str "--initial-branch=" base))
  (git world "config" "user.email" "test@example.com")
  (git world "config" "user.name" "Test")
  (git world "config" "commit.gpgsign" "false")
  (write-file world "base.txt" "base\n")
  (doseq [[file n] main-files] (write-file world file (numbered-lines file n)))
  (git world "add" ".")
  (git world "commit" "--quiet" "-m" "base")
  (git world "switch" "--quiet" "-c" "swarm/task")
  (assoc world :base base))

(defn commit-all [world message]
  (git world "add" "-A" ".")
  (git world "commit" "--quiet" "-m" message))

(defn rewrite-line [world file n]
  (let [lines (str/split-lines (slurp (path world file)))]
    (write-file world file (str (str/join "\n" (assoc lines (dec n) (str "// " file " rewritten line " n))) "\n"))))

(defn delete-lines [world file first-line last-line]
  (let [lines (str/split-lines (slurp (path world file)))
        kept (concat (take (dec first-line) lines) (drop last-line lines))]
    (write-file world file (str/join (map #(str % "\n") kept)))))

(defn install-fakes [{:keys [bin system-bin state] :as world}]
  (fs/create-dirs bin)
  (fs/create-dirs system-bin)
  (fs/create-dirs state)
  (doseq [name fake-names]
    (fs/copy fake-tool (fs/path bin name) {:replace-existing true})
    (fs/set-posix-file-permissions (fs/path bin name) "rwxr-xr-x"))
  (doseq [name system-names]
    (fs/create-sym-link (fs/path system-bin name) (fs/which name)))
  (assoc world :fakes true))

(defn state-file [{:keys [state]} name content]
  (spit (str (fs/path state name)) content))

(defn write-json [world file data]
  (write-file world file (json/generate-string data {:pretty true})))

(defn- run-env [{:keys [bin system-bin state env]}]
  (-> (into {} (System/getenv))
      (dissoc "SWARMFORGE_BASE")
      (assoc "PATH" (str bin fs/path-separator system-bin) "FAKE_JS_STATE" state)
      (merge env)))

(defn- outcome [result]
  {:status (:exit result)
   :out (:out result)
   :err (:err result)
   :lines (if (str/blank? (:out result)) [] (vec (str/split-lines (str/trimr (:out result)))))})

(defn run-sf-js [world args]
  (outcome (apply p/shell {:dir (:repo world) :out :string :err :string :continue true :env (run-env world)}
                  (str (fs/which "bash")) sf-js args)))

(defn run-sf-tool
  "Installs and requires sf-js through `sf tool` in a scratch project with
  swarmforge/ copied in; the outcome is the require's."
  [{:keys [dir] :as world}]
  (let [project (str (fs/path dir "project"))
        sf (str (fs/path project "swarmforge" "bin" "sf"))
        run #(p/shell {:dir project :out :string :err :string :continue true} "bb" sf "tool" %1 %2)]
    (fs/create-dirs project)
    (p/shell {:dir project :out :string :err :string} "git" "init" "--quiet")
    (fs/copy-tree swarmforge-dir (fs/path project "swarmforge"))
    (let [ensure (run "ensure" "sf-js")]
      (if (zero? (:exit ensure))
        (assoc world :result (outcome (run "require" "sf-js")))
        (assoc world :result (outcome ensure))))))
