(ns sf.coverage-args-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.coverage-args :as coverage-args]
            [sf.property-support :refer [passes?]]))

(def ^:private part (gen/elements ["a" "b" "ab" "src" "x.y"]))

(def ^:private directory
  "An absolute path as coverage-args writes it: the root is empty."
  (gen/fmap #(apply str (map (fn [p] (str "/" p)) %)) (gen/vector part 0 4)))

(def ^:private written
  "A path as a caller may write it, with ., .. and empty parts."
  (gen/let [parts (gen/vector (gen/one-of [part (gen/elements ["." ".." ""])]) 0 6)
            absolute? gen/boolean]
    (str (when absolute? "/") (str/join "/" parts))))

(describe "coverage-args paths"
  (it "lead back to the path from the directory they are relative to"
    (should (passes? (prop/for-all [path directory base directory]
                       (= path (coverage-args/absolute base (coverage-args/relative path base)))))))

  (it "do not depend on the current directory when they start with /"
    (should (passes? (prop/for-all [here directory path written]
                       (let [path (str "/" path)]
                         (= (coverage-args/absolute here path) (coverage-args/absolute "/elsewhere" path)))))))

  (it "keep the source roots pointing at the same directories after the rewrite"
    (should (passes? (prop/for-all [here directory roots (gen/vector (gen/such-that seq written) 1 3)]
                       (let [args (vec (mapcat #(vector "-s" %) roots))
                             targets (map #(coverage-args/absolute here %) roots)
                             {:keys [dir args]} (coverage-args/plan {:here here :args args :directory identity
                                                                     :file? (constantly false) :config "c"})
                             dir (if (= "/" dir) "" dir)]
                         (= targets (map #(coverage-args/absolute dir %) (take (count roots) (take-nth 2 (rest args)))))))))))
