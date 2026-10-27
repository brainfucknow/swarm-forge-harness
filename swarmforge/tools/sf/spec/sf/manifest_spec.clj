(ns sf.manifest-spec
  (:require [speclj.core :refer [describe it should= should-be-nil]]
            [sf.manifest :as manifest]))

(describe "saved-block-path"
  (it "keeps the block of a feature under .metrics/gherkin/"
    (should= ".metrics/gherkin/test/features/x.feature" (manifest/saved-block-path "test/features/x.feature"))))

(describe "newest-copies"
  (it "takes each path from the first note that holds it"
    (should= {"a" "new" "b" "old-b" "c" "c1"}
             (manifest/newest-copies [{"a" "new"} {"a" "old" "b" "old-b"} {"c" "c1" "b" "older-b"}])))

  (it "is empty without notes"
    (should= {} (manifest/newest-copies []))))

(describe "to-load"
  (it "leaves out the paths that the worktree already has"
    (should= {"b" "b1"} (manifest/to-load {"a" "a1" "b" "b1"} #{"a" "x"}))))

(describe "to-save"
  (it "keeps files that differ from their copy or have none"
    (should= {"b" "b2" "c" "c1"}
             (manifest/to-save {"a" "a1" "b" "b2" "c" "c1"} {"a" "a1" "b" "b1" "d" "d1"})))

  (it "is empty when every file equals its copy"
    (should= {} (manifest/to-save {"a" "a1"} {"a" "a1"}))))

(describe "notes"
  (it "decode what encode wrote"
    (let [files {".metrics/mutate/a.edn" "{:x 1}\n" ".metrics/gherkin/f.feature" "# a\n# \"b\"\n"}]
      (should= files (manifest/decode-note (manifest/encode-note files)))))

  (it "encode paths in sorted order"
    (should= "{\"a\" \"1\", \"b\" \"2\"}\n" (manifest/encode-note {"b" "2" "a" "1"})))

  (it "decode a note that is not a map of strings as empty"
    (should= {} (manifest/decode-note "not a note"))
    (should= {} (manifest/decode-note "{:a 1}"))
    (should= {} (manifest/decode-note "{\"a\" \"1\" \"b\" 2}"))
    (should= {} (manifest/decode-note "{\"a\" \"1\" :b \"2\"}"))
    (should= {} (manifest/decode-note "{\"a\""))))

(defn- clj-manifest [source & forms]
  (pr-str {:version 2 :source source
           :forms (mapv (fn [[k s u]] {:id "f" :killed k :survived s :uncovered u}) forms)}))

(defn- gherkin-manifest [& results]
  (str "# acceptance-mutation-manifest-begin\n# "
       "{\"feature_path\":\"f\",\"scenarios\":["
       (apply str (interpose "," (map (fn [[k s]] (str "{\"result\":{\"Total\":" (+ k s) ",\"Killed\":" k ",\"Survived\":" s ",\"Errors\":0}}")) results)))
       "]}\n# acceptance-mutation-manifest-end\n"))

(describe "tested-line"
  (it "sums the forms of a clj-mutate manifest"
    (should= "Tested: clj-mutate src/sf/task.clj | 41 killed, 0 survived"
             (manifest/tested-line ".metrics/mutate/sf/task.edn" (clj-manifest "src/sf/task.clj" [40 0 0] [1 0 0]))))

  (it "names uncovered sites when there are some"
    (should= "Tested: clj-mutate src/b.clj | 3 killed, 3 survived, 2 uncovered"
             (manifest/tested-line ".metrics/mutate/b.edn" (clj-manifest "src/b.clj" [3 1 0] [0 2 2]))))

  (it "counts a missing form count as zero"
    (should= "Tested: clj-mutate src/c.clj | 0 killed, 0 survived"
             (manifest/tested-line ".metrics/mutate/c.edn" (pr-str {:source "src/c.clj" :forms [{:id "f"}]}))))

  (it "sums the scenarios of a gherkin-mutator manifest"
    (should= "Tested: gherkin-mutator test/features/x.feature | 12 killed, 1 survived"
             (manifest/tested-line ".metrics/gherkin/test/features/x.feature" (gherkin-manifest [9 0] [3 1]))))

  (it "gives no line for a file that is not a manifest"
    (should-be-nil (manifest/tested-line ".metrics/other.txt" "x"))
    (should-be-nil (manifest/tested-line ".metrics/mutate/bad.edn" "{:source"))
    (should-be-nil (manifest/tested-line ".metrics/mutate/list.edn" "[1 2]"))
    (should-be-nil (manifest/tested-line ".metrics/mutate/nosource.edn" (pr-str {:forms []})))
    (should-be-nil (manifest/tested-line ".metrics/mutate/noforms.edn" (pr-str {:source "s"})))
    (should-be-nil (manifest/tested-line ".metrics/gherkin/f.feature" "Feature: f\n"))
    (should-be-nil (manifest/tested-line ".metrics/gherkin/f.feature" "# acceptance-mutation-manifest-begin\n# {bad\n# acceptance-mutation-manifest-end\n"))))

(describe "tested-lines"
  (it "gives the lines in path order and skips files without one"
    (should= ["Tested: gherkin-mutator a.feature | 4 killed, 0 survived"
              "Tested: clj-mutate src/c.clj | 2 killed, 0 survived"]
             (manifest/tested-lines {".metrics/other.txt" "x"
                                     ".metrics/mutate/c.edn" (clj-manifest "src/c.clj" [2 0 0])
                                     ".metrics/gherkin/a.feature" (gherkin-manifest [4 0])}))))
