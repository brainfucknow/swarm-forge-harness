(ns sf.catalogue-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.catalogue :as catalogue]
            [sf.property-support :refer [passes? word]]))

(def ^:private safe-chars (seq "ABCXYZabcxyz0189._-"))

(def ^:private unsafe-char (gen/elements (seq "/ ;$`'\"\\:@~{}\n")))

(def ^:private part
  "An owner or repository name github-repo accepts."
  (gen/such-that #(not (#{"." ".."} %))
                 (gen/fmap #(apply str %) (gen/not-empty (gen/vector (gen/elements safe-chars) 1 12)))))

(def ^:private sha
  (gen/fmap #(apply str %) (gen/vector (gen/elements (seq "0123456789abcdef")) 40)))

(def ^:private version
  "A pinned Maven version: a digit, then version characters, no -SNAPSHOT."
  (gen/such-that #(not (str/ends-with? % "-SNAPSHOT"))
                 (gen/let [digit (gen/elements (seq "0123456789"))
                           more (gen/vector (gen/elements (seq "0123456789.-+_abRC")) 0 10)]
                   (apply str digit more))))

(def ^:private any-text
  "Text from the version characters mixed with characters that break EDN
  strings, bash words and git option positions."
  (gen/fmap #(apply str %) (gen/vector (gen/one-of [(gen/elements (seq "0123456789.-+_aZ")) unsafe-char]) 0 12)))

(defn- replace-at [text index char]
  (let [index (mod index (count text))]
    (str (subs text 0 index) char (subs text (inc index)))))

(def ^:private source (gen/let [owner part repo part ref sha] {:source (str "github.com/" owner "/" repo) :ref ref}))

(def ^:private catalogue
  "The rows of a catalogue that keeps every rule: unique names, known kinds and
  rewrites, refs of each kind's form, and one ref per GitHub source."
  (gen/let [sources (gen/vector source 1 3)
            kinds (gen/vector (gen/elements ["bb" "bbmain" "clj" "local"]) 1 8)
            picks (gen/vector (gen/choose 0 2) (count kinds))
            versions (gen/vector version (count kinds))
            rewrites (gen/vector (gen/elements (vec catalogue/rewrites)) (count kinds))
            task word]
    (vec (map-indexed
          (fn [i kind]
            (let [name (str "t" i)
                  [source ref] (case kind
                                 ("bb" "bbmain") ((juxt :source :ref) (nth sources (mod (nth picks i) (count sources))))
                                 "clj" [(str "g/" name) (nth versions i)]
                                 "local" [(str "swarmforge/bin/" name) "-"])]
              [name kind source ref task (nth rewrites i) "-"]))
          kinds))))

(defn- problems [rows]
  (catalogue/problems (map catalogue/tool rows)))

(defn- own-problems [rows name]
  (filter #(str/starts-with? % (str "'" name "'")) (problems rows)))

(describe "github-repo"
  (it "is the repository of github.com/<owner>/<repo> in safe characters"
    (should (passes? (prop/for-all [owner part repo part]
                       (= repo (catalogue/github-repo (str "github.com/" owner "/" repo)))))))

  (it "is nil once the owner or repository holds an unsafe character, or a path follows"
    (should (passes? (prop/for-all [owner part repo part index gen/nat char unsafe-char extra part]
                       (every? nil? (map catalogue/github-repo
                                         [(str "github.com/" (replace-at owner index char) "/" repo)
                                          (str "github.com/" owner "/" (replace-at repo index char))
                                          (str "github.com/" owner "/" repo "/" extra)])))))))

(describe "commit-sha?"
  (it "holds for 40 lowercase hex digits and fails with one digit more, one less, or one replaced"
    (should (passes? (prop/for-all [ref sha index gen/nat char (gen/one-of [unsafe-char (gen/elements (seq "ABCDEFgxz"))])]
                       (and (catalogue/commit-sha? ref)
                            (not (catalogue/commit-sha? (subs ref 1)))
                            (not (catalogue/commit-sha? (str ref "a")))
                            (not (catalogue/commit-sha? (replace-at ref index char)))))))))

(describe "maven-version?"
  (it "holds for a pinned version and fails for its -SNAPSHOT"
    (should (passes? (prop/for-all [ref version]
                       (and (catalogue/maven-version? ref)
                            (not (catalogue/maven-version? (str ref "-SNAPSHOT"))))))))

  (it "accepts only text that an EDN string and a single-quoted bash word carry unescaped, never an option"
    (should (passes? (prop/for-all [text any-text]
                       (or (not (catalogue/maven-version? text))
                           (and (= (str "\"" text "\"") (pr-str text))
                                (not (str/includes? text "'"))
                                (not (str/starts-with? text "-")))))))))

(describe "problems"
  (it "is empty for a catalogue that keeps every rule"
    (should (passes? (prop/for-all [rows catalogue]
                       (= [] (problems rows))))))

  (it "names the row, and only it, when a later row of a source pins another commit"
    (should (passes? (prop/for-all [rows catalogue other sha]
                       (let [clones (filter #(#{"bb" "bbmain"} (second %)) rows)
                             [first-row later] (some (fn [[_ rows]] (when (second rows) rows))
                                                     (group-by #(nth % 2) clones))]
                         (or (nil? later)
                             (= other (nth later 3))
                             (let [rows (replace {later (assoc later 3 other)} rows)]
                               (= [(str "'" (first later) "' pins " (nth later 2) " at a different ref than '" (first first-row) "'")]
                                  (problems rows)))))))))

  (it "names only the columns of a row with the wrong number of them"
    (should (passes? (prop/for-all [rows catalogue index gen/nat size (gen/such-that #(not= 7 %) (gen/choose 1 10))]
                       (let [row (nth rows (mod index (count rows)))
                             changed (vec (take size (concat row (repeat "x"))))]
                         (= [(str "'" (first row) "' needs exactly " catalogue/columns)]
                            (own-problems (replace {row changed} rows) (first row))))))))

  (it "names a row listed twice, and nothing else, when a catalogue repeats a row"
    (should (passes? (prop/for-all [rows catalogue]
                       (= [(str "tool '" (first (peek rows)) "' listed twice")]
                          (problems (conj rows (peek rows)))))))))
