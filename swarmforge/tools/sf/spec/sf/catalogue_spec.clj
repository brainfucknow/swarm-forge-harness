(ns sf.catalogue-spec
  (:require [clojure.string :as str]
            [speclj.core :refer [describe context it should= should should-not should-be-nil]]
            [sf.catalogue :as catalogue]))

(def ^:private sha "0123456789abcdef0123456789abcdef01234567")

(defn- tool [line]
  (catalogue/tool (str/split line #" ")))

(defn- problems [line & others]
  (let [it (tool line)]
    (catalogue/ref-problems it (cons it (map tool others)))))

(describe "tool"
  (it "names the columns of a row and splits the needs"
    (should= {:name "c" :kind "bb" :source "github.com/x/c" :ref sha :task "crap" :rewrite "coverage"
              :needs ["cloverage" "speclj"] :columns 7}
             (tool (str "c bb github.com/x/c " sha " crap coverage cloverage,speclj"))))

  (it "has no needs for - or a short row"
    (should= [] (:needs (tool "c bb github.com/x/c - - - -")))
    (should= [] (:needs (tool "c bb")))))

(describe "github-repo"
  (it "is the repository of github.com/<owner>/<repo> in safe characters"
    (should= "Acceptance-Pipeline-Specification" (catalogue/github-repo "github.com/unclebob/Acceptance-Pipeline-Specification"))
    (should= "a.b_c-1" (catalogue/github-repo "github.com/O.w_n-1/a.b_c-1")))

  (it "is nil for any other source"
    (doseq [source ["example.com/a/x" "github.com/a" "github.com/a/x/y" "github.com/a/.." "github.com/./x"
                    "github.com/a/x;y" "github.com//x" "github.com/a/" "https://github.com/a/x" "" nil]]
      (should-be-nil (catalogue/github-repo source)))))

(describe "refs"
  (it "are full lowercase commit SHAs for the kinds that clone"
    (should (catalogue/commit-sha? sha))
    (doseq [ref ["0123abc" (str/upper-case sha) (str sha "0") (str "-" (subs sha 1)) "main" nil]]
      (should-not (catalogue/commit-sha? ref))))

  (it "are pinned Maven versions for clj"
    (doseq [ref ["1.2.4" "3.13.0" "1.0.0-RC1" "2.0_beta+3"]]
      (should (catalogue/maven-version? ref)))
    (doseq [ref ["RELEASE" "LATEST" "1.0-SNAPSHOT" "1.0\"}" "v1.0" "-1" "" nil]]
      (should-not (catalogue/maven-version? ref)))))

(describe "ref-problems"
  (it "accepts rows that keep the rules"
    (should= [] (problems (str "x bb github.com/a/x " sha " t - -")))
    (should= [] (problems (str "x bbmain github.com/a/x " sha " t - -")))
    (should= [] (problems "x clj g/x 1.2.4 t - -"))
    (should= [] (problems "x local swarmforge/bin/x - - - -"))
    (should= [] (problems "x java anything anything t - -")))

  (it "names a ref of the wrong form for the kind"
    (should= ["'x' needs a 40-digit lowercase hex commit SHA as its ref, not '0123abc'"]
             (problems "x bb github.com/a/x 0123abc t - -"))
    (should= ["'x' needs a 40-digit lowercase hex commit SHA as its ref, not '--upload-pack=x'"]
             (problems "x bbmain github.com/a/x --upload-pack=x t - -"))
    (should= ["'x' needs a pinned Maven version as its ref, not 'RELEASE'"]
             (problems "x clj g/x RELEASE t - -"))
    (should= ["'x' is local and needs '-' as its ref, not '1.0'"]
             (problems "x local swarmforge/bin/x 1.0 - - -")))

  (it "names a GitHub source that is not github.com/<owner>/<repo>"
    (should= ["'x' needs a source github.com/<owner>/<repo>, not 'github.com/a/..'"]
             (problems (str "x bb github.com/a/.. " sha " t - -")))
    (should= [] (problems (str "x clj g/x/y 1.0 t - -"))))

  (it "names a row whose source the first row of that source pins at another ref"
    (let [first-row (tool (str "y bb github.com/a/x " (apply str (repeat 40 "a")) " t - -"))
          second-row (tool (str "x bb github.com/a/x " sha " t - -"))
          rows [first-row second-row]]
      (should= ["'x' pins github.com/a/x at a different ref than 'y'"]
               (catalogue/ref-problems second-row rows))
      (should= [] (catalogue/ref-problems first-row rows))))

  (context "on a row with the wrong number of columns"
    (it "names only the columns"
      (should= ["'x' needs exactly <name> <kind> <source> <ref> <task> <rewrite> <needs>"]
               (problems "x bb github.com/a/x t - -"))
      (should= ["'x' needs exactly <name> <kind> <source> <ref> <task> <rewrite> <needs>"]
               (problems (str "x bb github.com/a/x " sha " t - - extra"))))))

(describe "problems"
  (it "is empty for a catalogue whose rows keep the rules"
    (should= [] (catalogue/problems (map tool [(str "a bb github.com/x/a " sha " t - -")
                                               (str "b bbmain github.com/x/a " sha " t - a")
                                               "c local swarmforge/bin/c - - - -"]))))

  (it "names, row by row, a name listed twice, an unknown kind or rewrite, then the ref problems"
    (should= ["'a' has unknown kind 'java'"
              "'a' has unknown rewrite 'rot13'"
              "tool 'a' listed twice"
              "'a' needs a 40-digit lowercase hex commit SHA as its ref, not '0123abc'"
              "'short' needs exactly <name> <kind> <source> <ref> <task> <rewrite> <needs>"]
             (catalogue/problems (map tool ["a java s - t rot13 -" "a bb github.com/x/a 0123abc t - -" "short bb"])))))

(describe "pin-sources"
  (it "is speclj for cloverage, whose wrapper takes speclj's version"
    (should= ["speclj"] (catalogue/pin-sources "cloverage")))

  (it "is empty for the other tools"
    (should= [] (catalogue/pin-sources "speclj"))
    (should= [] (catalogue/pin-sources "crap4clj"))))
