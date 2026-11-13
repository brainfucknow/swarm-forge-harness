(ns sfcs.sfcs-property
  "Properties of .NET coverage settings and file-level CRAP evidence."
  (:require [cheshire.core :as json]
            [clojure.data.xml :as xml]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [property-support :refer [defspec]]
            [sfcs.coverage :as coverage]
            [sfcs.measurement :as measurement]))

(def trials 200)

(def segment-char
  "Printable ASCII other than a path separator, so .NET regex metacharacters and XML specials appear."
  (gen/such-that #(not (#{\/ \\} %)) (gen/fmap char (gen/choose 32 126))))

(def segment (gen/fmap str/join (gen/vector segment-char 1 6)))
(def segments (gen/vector segment 1 3))
(def separator (gen/elements ["/" "\\"]))

(defn- absolute [parts] (str "/" (str/join "/" parts)))

(defn- join-with [sep parts] (str/join sep parts))

(defn- sources
  "The <Source> patterns under `kind` (Include or Exclude) of a parsed settings file."
  [settings kind]
  (->> (xml-seq settings)
       (filter #(= (keyword kind) (:tag %)))
       (mapcat :content)
       (filter #(= :Source (:tag %)))
       (mapv #(re-pattern (apply str (:content %))))))

(defn- matches-any?
  "Whether a pattern finds `path`, unanchored, as .NET's Regex.IsMatch does."
  [patterns path]
  (boolean (some #(re-find % path) patterns)))

(defspec settings-include-exactly-the-paths-under-root trials
  (prop/for-all [root-parts segments
                 below segments
                 suffix segment
                 sep separator]
    (let [root (absolute root-parts)
          [include] (sources (xml/parse-str (coverage/settings-xml root [])) "Include")
          under (str (join-with sep (cons "" root-parts)) sep (join-with sep below))
          sibling (str root suffix "/" (str/join "/" below))
          nested (str "/" suffix root "/" (str/join "/" below))]
      (and (matches-any? [include] under)
           (not (matches-any? [include] sibling))
           (not (matches-any? [include] nested))))))

(defspec settings-exclude-obj-sources-and-nothing-else trials
  (prop/for-all [root-parts segments
                 dir (gen/one-of [segment (gen/return "obj")])
                 below segments
                 assemblies (gen/vector segment 0 3)]
    (let [root (absolute root-parts)
          excludes (sources (xml/parse-str (coverage/settings-xml root assemblies)) "Exclude")
          path (str root "/" dir "/" (str/join "/" below))]
      (= (= "obj" dir) (matches-any? excludes path)))))

(defn- module-patterns [settings]
  (->> (xml-seq settings)
       (filter #(= :ModulePath (:tag %)))
       (mapv #(re-pattern (apply str (:content %))))))

(defspec settings-exclude-exactly-the-test-assemblies trials
  (prop/for-all [assemblies (gen/vector-distinct segment {:min-elements 1 :max-elements 3})
                 other segment
                 dirs segments
                 sep separator
                 extension (gen/elements [".dll" ".exe"])]
    (let [patterns (module-patterns (xml/parse-str (coverage/settings-xml "/r" assemblies)))
          module (fn [name] (str (join-with sep (cons "" dirs)) sep name extension))]
      (and (every? #(matches-any? patterns (module %)) assemblies)
           (= (boolean (some #{other} assemblies)) (matches-any? patterns (module other)))
           (not (matches-any? patterns (str (module (first assemblies)) ".config")))
           (not (matches-any? patterns (str (join-with sep (cons "" dirs)) sep "x" (first assemblies) extension)))))))

(def property-value
  (gen/one-of [(gen/elements ["true" "True" " TRUE " "false" "" "yes"]) gen/string-ascii]))

(defn- reads-true? [value]
  (= "true" (str/lower-case (str/trim value))))

(defspec a-test-project-is-one-msbuild-evaluates-true trials
  (prop/for-all [exit (gen/elements [0 0 1])
                 is-test property-value
                 is-mtp property-value]
    (let [out (json/generate-string {"Properties" {"IsTestProject" is-test "IsTestingPlatformApplication" is-mtp}})]
      (= (and (zero? exit) (or (reads-true? is-test) (reads-true? is-mtp)))
         (coverage/test-project? {:exit exit :out out})))))

(defspec unreadable-evaluations-are-not-test-projects trials
  (prop/for-all [out gen/string-ascii]
    (not (coverage/test-project? {:exit 0 :out (str/replace out "Properties" "")}))))

(defspec only-zero-and-zero-tests-ran-pass trials
  (prop/for-all [status (gen/choose -2 300)]
    (= (contains? #{0 8} status) (coverage/run-passed? status))))

(def source-id (gen/choose 0 40))
(def source-ids (gen/set source-id))

(defn- source-name [id] (str "File" id ".cs"))

(defn- measured-method [file]
  {:file file :first-line 1 :last-line 3 :complexity 2 :coverage 0.5})

(defspec missing-files-match-unmeasured-undeclared-scope trials
  (prop/for-all [scope-ids source-ids
                 measured-ids source-ids
                 exempt-ids source-ids
                 absolute? gen/boolean]
    (let [scope (into (sorted-set) (map source-name) scope-ids)
          exempt (set (map source-name exempt-ids))
          measured (set (map source-name measured-ids))
          methods (map #(measured-method (str (when absolute? "/repo/") %)) measured)
          expected (sort (set/difference scope measured exempt))]
      (= expected (measurement/missing-files "/repo" scope exempt methods)))))

(defspec duplicate-and-reordered-evidence-keeps-the-same-gaps trials
  (prop/for-all [scope-ids source-ids
                 exempt-ids source-ids
                 method-ids (gen/vector source-id)]
    (let [scope (into (sorted-set) (map source-name) scope-ids)
          exempt (set (map source-name exempt-ids))
          methods (map (comp measured-method source-name) method-ids)]
      (= (measurement/missing-files "/repo" scope exempt methods)
         (measurement/missing-files "/repo" scope exempt (concat (reverse methods) methods))))))

(defspec unusable-method-data-leaves-the-source-unmeasured trials
  (prop/for-all [id source-id
                 invalid (gen/elements [{:first-line 0} {:last-line nil}
                                        {:complexity -1} {:complexity Double/POSITIVE_INFINITY}
                                        {:complexity Double/NaN} {:coverage -0.1} {:coverage 1.1}])]
    (let [file (source-name id)]
      (= [file] (measurement/missing-files "/repo" (sorted-set file) #{}
                                          [(merge (measured-method file) invalid)])))))
