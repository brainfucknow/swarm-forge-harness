(ns sfcs.measurement
  "File-level evidence and explicit no-code declarations for .NET CRAP checks."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [sfcore.changes :as changes]
            [sfcore.git :as git]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]))

(defn candidates
  "Existing selected-language sources in the changed scope, or all Git-listed sources."
  [root changed extension]
  (let [paths (if changed
                (keys changed)
                (str/split (git/git ["ls-files" "-z" "--cached" "--others" "--exclude-standard"
                                    "--full-name" "--" (str "*" extension)] :dir root) #"\u0000"))]
    (into (sorted-set)
          (filter #(and (str/ends-with? % extension) (fs/regular-file? (fs/path root %))))
          paths)))

(defn- declaration-path [root scope declared]
  (let [file (fs/normalize (fs/absolutize (shell/in-dir declared)))
        relative (str/replace (str (fs/relativize root file)) "\\" "/")]
    (when-not (and (fs/regular-file? file)
                   (.startsWith (fs/real-path file) (fs/real-path root))
                   (contains? scope relative))
      (throw (outcome/usage-error
               (str "--no-executable-code " declared ": expected an existing in-scope source file inside " root))))
    relative))

(defn exemptions
  "Validated caller assertions, normalized and unique; they never remove scored methods."
  [root scope declarations]
  (into (sorted-set) (map (partial declaration-path root scope)) declarations))

(defn- usable? [{:keys [first-line last-line complexity coverage]}]
  (and (pos-int? first-line) (pos-int? last-line)
       (number? complexity) (<= 0 complexity Double/MAX_VALUE)
       (number? coverage) (<= 0 coverage 1)))

(defn missing-files
  "Candidates without usable method evidence in any report, excluding declared no-code files."
  [root scope exempt methods]
  (let [paths (zipmap scope (repeat true))
        measured (into #{} (keep #(changes/to-changed-path (:file %) paths root)) (filter usable? methods))]
    (remove #(or (contains? measured %) (contains? exempt %)) scope)))
