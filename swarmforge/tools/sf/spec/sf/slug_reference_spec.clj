(ns sf.slug-reference-spec
  "Checks sf.record/slug against a reference built on java.text.Normalizer,
  for every code point of U+0000..U+024F and U+1E00..U+1EFF. Babashka older
  than 1.12.205 has no Normalizer; there the spec is pending."
  (:require [clojure.string :as str]
            [speclj.core :refer [describe it pending should=]]
            [sf.record :as record]))

(def ^:private nfd
  (when (resolve 'java.text.Normalizer)
    (eval '(fn [text] (java.text.Normalizer/normalize text java.text.Normalizer$Form/NFD)))))

(defn- reference [title]
  (let [plain (-> (str/lower-case title)
                  (str/escape {\å "aa" \ä "ae" \æ "ae" \ö "oe" \ø "oe"})
                  nfd
                  (str/replace #"\p{M}" "")
                  (str/replace #"[^a-z0-9]+" "-")
                  (str/replace #"^-|-$" ""))]
    (if (str/blank? plain) "task" plain)))

(def ^:private code-points
  (concat (range 0x0000 0x0250) (range 0x1E00 0x1F00)))

(defn- mismatches [wrap]
  (for [cp code-points
        :let [title (wrap (str (char cp)))]
        :when (not= (reference title) (record/slug title))]
    (format "U+%04X %s: %s, reference %s" cp (pr-str title) (record/slug title) (reference title))))

(describe "slug against the Normalizer reference"
  (it "matches for each code point alone"
    (if nfd
      (should= [] (vec (mismatches identity)))
      (pending "this Babashka has no java.text.Normalizer")))

  (it "matches for each code point between x and y"
    (if nfd
      (should= [] (vec (mismatches #(str "x" % "y"))))
      (pending "this Babashka has no java.text.Normalizer"))))
