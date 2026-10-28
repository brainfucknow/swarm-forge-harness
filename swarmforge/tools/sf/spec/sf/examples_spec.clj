(ns sf.examples-spec
  "describe and context register the values of their body forms. A let around
  several it, context or describe forms has one value, its last, so the others
  never run. This spec finds such lets in the speclj spec and property files."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as edamame]
            [speclj.core :refer [describe it should= should-fail]]))

(def ^:private example-heads '#{it context describe})

(defn- forms-within [form]
  (tree-seq coll? seq form))

(defn- let-hidden
  "The example forms a let form hides: all but the last in its body."
  [form]
  (when (and (seq? form) (= 'let (first form)))
    (butlast (filter #(and (seq? %) (example-heads (first %))) (drop 2 form)))))

(defn hidden
  "One line for each example form a let in the source text hides."
  [path text]
  (for [form (edamame/parse-string-all text {:all true :auto-resolve name :row-key :line})
        let-form (forms-within form)
        example (let-hidden let-form)]
    (format "%s:%d: %s %s, hidden by the let at line %d"
            path (:line (meta example)) (first example) (second example) (:line (meta let-form)))))

(def ^:private sf-dir
  (-> (io/resource "sf/examples_spec.clj") fs/path fs/parent fs/parent fs/parent))

(defn- hidden-in-tree []
  (for [dir ["spec" "property"]
        file (sort (map str (fs/glob (fs/path sf-dir dir) "**.clj")))
        line (hidden (str (fs/relativize sf-dir file)) (slurp file))]
    line))

(describe "hidden"
  (it "names every example but the last in a let, with its line and the let's line"
    (should= ["s.clj:3: it first, hidden by the let at line 2"
              "s.clj:4: context second, hidden by the let at line 2"]
             (hidden "s.clj" (str "(describe \"d\"\n"
                                  "  (let [x 1]\n"
                                  "    (it \"first\" (should x))\n"
                                  "    (context \"second\" (it \"a\" (should x)))\n"
                                  "    (it \"last\" (should x))))"))))

  (it "passes a let with one example"
    (should= [] (hidden "s.clj" "(describe \"d\" (let [x 1] (it \"alone\" (should x))))")))

  (it "finds a let at any depth"
    (should= ["s.clj:3: describe deep, hidden by the let at line 2"]
             (hidden "s.clj" (str "(describe \"d\" (context \"c\" (do\n"
                                  "  (let [y 2]\n"
                                  "    (describe \"deep\" (it \"a\" (should y)))\n"
                                  "    (it \"last\" (should y))))))"))))

  (it "ignores other forms that hold several examples, and non-example forms in a let"
    (should= [] (hidden "s.clj" (str "(describe \"d\" (do (it \"a\" 1) (it \"b\" 2))"
                                     " (let [x 1] (should x) (it \"only\" (should x))))"))))

  (it "reads reader macros and auto-resolved keywords"
    (should= [] (hidden "s.clj" "(def f #(inc %)) (def r #\"re\") (def k ::k) (def v #'f) (def q `(a ~@b))"))))

(describe "the spec and property files"
  (it "hide no example inside a let"
    (when-let [lines (seq (hidden-in-tree))]
      (should-fail (str/join "\n" lines)))))
