(ns sf.feature-block-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.feature-block :as block]
            [sf.property-support :refer [hex7 passes? sentence]]))

(defn- text [lines] (apply str (map #(str % "\n") lines)))

(def ^:private block-text
  "An optional stamp line, then a manifest of comment lines."
  (gen/let [stamp (gen/one-of [(gen/return nil) (gen/fmap #(str "# mutation-stamp: sha256=" %) hex7)])
            entries (gen/vector (gen/fmap #(str "# " %) sentence) 0 4)]
    (text (concat (when stamp [stamp])
                  ["# acceptance-mutation-manifest-begin"]
                  entries
                  ["# acceptance-mutation-manifest-end"]))))

(def ^:private feature
  "A feature text: a Feature line, then indented and blank lines."
  (gen/let [title sentence
            lines (gen/vector (gen/one-of [(gen/return "") (gen/fmap #(str "  " %) sentence)]) 0 5)]
    (text (cons (str "Feature: " title) lines))))

(def ^:private blanks (gen/fmap #(apply str (repeat % "\n")) (gen/choose 0 3)))

(describe "the feature block"
  (it "comes back off a feature that with-block put it on"
    (should (passes? (prop/for-all [b block-text f feature]
                       (= {:block b :body f} (block/split (block/with-block b f)))))))

  (it "is not found in a feature without one"
    (should (passes? (prop/for-all [f feature]
                       (nil? (block/split f))))))

  (it "leaves no block behind, so a second stash finds nothing"
    (should (passes? (prop/for-all [b block-text gap blanks f feature]
                       (let [{:keys [body]} (block/split (str b gap f))]
                         (and (= f body) (nil? (block/split body))))))))

  (it "keeps the feature whole across a stash and a restore"
    (should (passes? (prop/for-all [b block-text f feature]
                       (let [stored (block/with-block b f)
                             {:keys [block body]} (block/split stored)]
                         (and (str/starts-with? stored block)
                              (= stored (block/with-block block body)))))))))
