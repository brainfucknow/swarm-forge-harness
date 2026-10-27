(ns sf.entry-point
  "Whether a tool source has the entry point swarmforge/tools.conf names for it."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [edamame.core :as edamame]))

(def ^:private bb-edn-options
  {:all true :auto-resolve name :read-cond :allow :features #{:bb :clj}
   :readers (fn [_tag] identity)})

(defn task-names
  "The task names (the symbol keys of :tasks) of a bb.edn's text."
  [text]
  (->> (:tasks (edamame/parse-string text bb-edn-options))
       keys
       (filter symbol?)
       (map str)
       set))

(defn namespace-files
  "The paths under src/ that can hold the namespace."
  [namespace]
  (let [path (-> namespace (str/replace "." "/") (str/replace "-" "_"))]
    (mapv #(str path "." %) ["clj" "cljc" "bb"])))

(defn- bb-problem [dir task]
  (let [config (fs/path dir "bb.edn")]
    (cond
      (not (fs/exists? config)) (str "no bb.edn in " dir)
      (not ((task-names (slurp (str config))) task)) (str config " has no task '" task "'"))))

(defn- bbmain-problem [dir namespace]
  (when-not (some #(fs/exists? (fs/path dir "src" %)) (namespace-files namespace))
    (str (fs/path dir "src") " has no namespace " namespace)))

(defn problem
  "Why the source in dir lacks the entry point of a tool of kind, or nil."
  [kind dir task]
  (case kind
    "bb" (bb-problem dir task)
    "bbmain" (bbmain-problem dir task)
    nil))
