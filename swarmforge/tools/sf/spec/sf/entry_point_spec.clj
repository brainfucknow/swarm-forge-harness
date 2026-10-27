(ns sf.entry-point-spec
  (:require [babashka.fs :as fs]
            [speclj.core :refer [describe it should= should-be-nil with around]]
            [sf.entry-point :as entry-point]))

(describe "task-names"
  (it "names the symbol keys of the :tasks map"
    (should= #{"mutate" "bb-spec:core"}
             (entry-point/task-names "{:paths [\"src\"] :tasks {:requires ([x]) mutate {:task (f)} bb-spec:core {}}}")))

  (it "reads task bodies with reader macros, tags and namespaced keywords"
    (should= #{"crap" "int"}
             (entry-point/task-names "{:tasks {:init (def x #\"re\") crap {:task #(inc %) :k ::local} int #my/tag {:x @y}}}")))

  (it "is empty without a :tasks map"
    (should= #{} (entry-point/task-names "{:paths [\"src\"]}"))))

(describe "namespace-files"
  (it "turns dots into slashes and dashes into underscores, for each extension"
    (should= ["clj_mutate/core.clj" "clj_mutate/core.cljc" "clj_mutate/core.bb"]
             (entry-point/namespace-files "clj-mutate.core"))))

(describe "problem"
  (with dir (str (fs/create-temp-dir {:prefix "entry-point-"})))
  (around [it] (try (it) (finally (fs/delete-tree @dir))))

  (it "reports a bb source without bb.edn"
    (should= (str "no bb.edn in " @dir) (entry-point/problem "bb" @dir "crap")))

  (it "reports a bb source whose bb.edn lacks the task"
    (spit (str (fs/path @dir "bb.edn")) "{:tasks {crap4clj {} int {}}}")
    (should= (str @dir "/bb.edn has no task 'crap'") (entry-point/problem "bb" @dir "crap")))

  (it "accepts a bb source whose bb.edn has the task"
    (spit (str (fs/path @dir "bb.edn")) "{:tasks {crap {}}}")
    (should-be-nil (entry-point/problem "bb" @dir "crap")))

  (it "reports a bbmain source without the namespace"
    (spit (str (fs/path @dir "bb.edn")) "{:tasks {dry4clj.core {}}}")
    (should= (str @dir "/src has no namespace dry4clj.core") (entry-point/problem "bbmain" @dir "dry4clj.core")))

  (it "accepts a bbmain source with the namespace in any extension"
    (doseq [ext ["clj" "cljc" "bb"]
            :let [file (fs/path @dir "src" "my_tool" (str "core." ext))]]
      (fs/create-dirs (fs/parent file))
      (spit (str file) "(ns my-tool.core)")
      (should-be-nil (entry-point/problem "bbmain" @dir "my-tool.core"))
      (fs/delete file)))

  (it "has nothing to check for other kinds"
    (should-be-nil (entry-point/problem "clj" @dir "cloverage.coverage"))
    (should-be-nil (entry-point/problem "local" @dir "-"))))
