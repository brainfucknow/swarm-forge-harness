(ns sf.runtime-spec
  (:require [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [clojure.edn :as edn]
            [speclj.core :refer [describe it should should-not should= should-be-nil]]
            [sf.runtime :as runtime]))

(def ^:private sf-home
  (fs/parent (fs/absolutize (System/getProperty "babashka.config"))))

(defn- ci-bb-version
  "The BB_VERSION the CI job named job installs."
  [job]
  (let [workflow (yaml/parse-string (slurp (str (fs/path sf-home ".." ".." ".." ".github" "workflows" "test.yml"))))]
    (some #(get-in % [:env :BB_VERSION]) (get-in workflow [:jobs job :steps]))))

(describe "minimum"
  (it "is the Babashka the sf-min-bb CI job tests on"
    (should= "1.12.200" runtime/minimum)
    (should= runtime/minimum (str (ci-bb-version :sf-min-bb))))

  (it "is the :min-bb-version of the sf bb.edn"
    (should= runtime/minimum (:min-bb-version (edn/read-string (slurp (str (fs/path sf-home "bb.edn")))))))

  (it "is no newer than the Babashka of the main CI job"
    (should-not (runtime/too-old? (str (ci-bb-version :sf))))))

(describe "too-old?"
  (it "refuses a Babashka older than the minimum"
    (should (runtime/too-old? "1.12.199"))
    (should (runtime/too-old? "1.11.300"))
    (should (runtime/too-old? "1.3.191")))

  (it "accepts the minimum and every later version"
    (should-not (runtime/too-old? "1.12.200"))
    (should-not (runtime/too-old? "1.12.201-SNAPSHOT"))
    (should-not (runtime/too-old? "1.13.229"))
    (should-not (runtime/too-old? "2.0.0")))

  (it "compares the numbers, not the text"
    (should-not (runtime/too-old? "1.100.0"))
    (should (runtime/too-old? "0.999.999")))

  (it "reads a missing part as 0"
    (should (runtime/too-old? "1.12"))
    (should-not (runtime/too-old? "1.13"))))

(describe "refusal"
  (it "names the version and the minimum"
    (should= "sf: Babashka 1.12.199 is too old; sf needs 1.12.200 or newer: https://babashka.org"
             (runtime/refusal "1.12.199")))

  (it "is nil for a version sf runs on"
    (should-be-nil (runtime/refusal runtime/minimum))))
