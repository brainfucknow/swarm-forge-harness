(ns sf.property-support
  (:require [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]))

(def runs 200)

(defn passes?
  "Runs the property; prints the shrunk counterexample when it fails."
  [property]
  (let [result (tc/quick-check runs property)]
    (when-not (:pass? result) (prn (:shrunk result)))
    (:pass? result)))

(def word (gen/not-empty gen/string-alphanumeric))

(def sentence (gen/fmap #(str/join " " %) (gen/not-empty (gen/vector word 1 4))))

(def role (gen/elements ["specifier" "coder" "cleaner" "architect" "hardener" "qa"]))

(def hex7 (gen/fmap #(apply str %) (gen/vector (gen/elements (seq "0123456789abcdef")) 7)))

(defn commit-with
  "A commit with an optional Lore-id from id-gen first, then up to four
  trailers from trailer-gen, as the hook writes it."
  [id-gen trailer-gen]
  (gen/let [sha hex7
            r role
            id (gen/one-of [(gen/return nil) id-gen])
            trailers (gen/vector trailer-gen 0 4)]
    {:sha sha :id id :message (str "M\n\nBy " r ".\n")
     :trailers (vec (cond->> trailers id (cons (str "Lore-id: " id))))}))
