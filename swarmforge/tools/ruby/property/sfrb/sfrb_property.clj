(ns sfrb.sfrb-property
  "Properties of sf-rb's report readers, its CRAP join with SimpleCov, its
  source selection and its mutant command line."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [property-support :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sfrb.commands :as commands]
            [sfrb.crap :as crap]
            [sfrb.flay :as flay]
            [sfrb.flog :as flog]
            [sfrb.mutant :as mutant]
            [sfrb.rubocop :as rubocop]))

(def trials 200)

(def root "/work")

(def ruby-file (gen/elements ["lib/a.rb" "lib/b/c.rb" "app/d.rb"]))

(def reported-path
  "A file as a tool reports it: relative, absolute under the root, or ./-led."
  (gen/bind ruby-file (fn [file] (gen/elements [[file file] [(str root "/" file) file] [(str "./" file) file]]))))

(def method-name
  (gen/fmap (fn [[stem suffix]] (str stem suffix))
            (gen/tuple (gen/elements ["go" "balance" "self.build" "[]" "label_for"]) (gen/elements ["" "?" "!" "="]))))

(def span (gen/tuple (gen/choose 1 80) (gen/choose 0 15)))

;; RuboCop

(def cop-prefix
  {"Metrics/CyclomaticComplexity" "Cyclomatic complexity"
   "Metrics/PerceivedComplexity" "Perceived complexity"})

(def offense
  (gen/one-of
   [(gen/fmap (fn [[cop [reported file] name [line length] complexity]]
                {:kind :method :cop cop :reported reported
                 :method {:file file :line line :last-line (+ line length) :name name :complexity complexity}})
              (gen/tuple (gen/elements (keys cop-prefix)) reported-path method-name span (gen/choose 1 30)))
    (gen/fmap (fn [[[reported file] line first-line]]
                {:kind :syntax :cop "Lint/Syntax" :reported reported :file file :line line :first-line first-line})
              (gen/tuple reported-path (gen/choose 1 80) (gen/elements ["unexpected 'end'" "expected an `end`"])))]))

(defn- render-offense [{:keys [kind cop method line first-line]}]
  (if (= :method kind)
    {"severity" "refactor" "cop_name" cop
     "message" (str (cop-prefix cop) " for `" (:name method) "` is too high. [" (:complexity method) "/0]")
     "location" {"start_line" (:line method) "last_line" (:last-line method)}}
    {"severity" "fatal" "cop_name" cop
     "message" (str first-line "\n(Using Ruby 3.3 parser; configure using `TargetRubyVersion` parameter)")
     "location" {"start_line" line "last_line" line}}))

(defn- rubocop-report [offenses]
  {"files" (for [[reported group] (group-by :reported offenses)]
             {"path" reported "offenses" (map render-offense group)})})

(defspec rubocop-reports-give-back-every-method-of-the-chosen-cop trials
  (prop/for-all [offenses (gen/vector offense 0 10) cop (gen/elements (keys cop-prefix))]
    (= (frequencies (keep #(when (= cop (:cop %)) (:method %)) offenses))
       (frequencies (rubocop/parse-report (rubocop-report offenses) cop root)))))

(defspec rubocop-reports-name-each-unparsable-source-once-per-error trials
  (prop/for-all [offenses (gen/vector offense 0 10)]
    (= (frequencies (for [{:keys [kind file line first-line]} offenses :when (= :syntax kind)]
                      (str "rubocop could not parse " file ":" line ": " first-line)))
       (frequencies (rubocop/analysis-errors (rubocop-report offenses) root)))))

;; flog

(def flog-entry
  (gen/fmap (fn [[tenths owner name [reported file] [line length]]]
              {:tenths tenths :name (str owner name) :reported reported
               :method {:file file :line line :last-line (+ line length) :name (str owner name)
                        :complexity (/ tenths 10.0)}})
            (gen/tuple (gen/choose 0 999) (gen/elements ["Ledger#" "Ledger::define_method#" "main#" "Calc::"])
                       (gen/elements ["balance" "empty?" "none" "label="]) reported-path span)))

(defn- flog-text [entries]
  (str/join "\n" (concat ["    30.3: flog total" "     4.3: flog/method average" ""]
                         (for [{:keys [tenths name reported method]} entries]
                           (String/format java.util.Locale/ROOT "%8.1f: %-30s %s:%d-%d"
                                          (to-array [(/ tenths 10.0) name reported
                                                     (int (:line method)) (int (:last-line method))]))))))

(defspec flog-reports-give-back-every-method-but-none trials
  (prop/for-all [entries (gen/vector flog-entry 0 10)]
    (= (map :method (remove #(str/ends-with? (:name %) "#none") entries))
       (flog/parse-report (flog-text entries) root))))

;; flay

(def flay-clone
  (gen/hash-map :mass (gen/choose 16 900)
                :sides (gen/vector (gen/fmap (fn [[file [line length]]] {:file file :start line :end (+ line length)})
                                             (gen/tuple ruby-file span))
                                   2 4)))

(defspec flay-reports-give-back-every-clone-and-side trials
  (prop/for-all [clones (gen/vector flay-clone 0 6)]
    (= clones
       (flay/parse-clones {"clones" (for [{:keys [mass sides]} clones]
                                      {"mass" mass
                                       "sides" (for [{:keys [file start end]} sides]
                                                 {"file" file "line" start "line_max" end})})}))))

;; CRAP join with SimpleCov

(def hits (gen/one-of [(gen/return nil) (gen/choose 0 3)]))

(def nesting
  "An outer method, one method nested strictly inside it, and the file's
  SimpleCov lines with two variants of the nested method's hits."
  (gen/bind (gen/tuple (gen/choose 1 10) (gen/choose 2 20) (gen/choose 0 10))
            (fn [[line length extra]]
              (let [last-line (+ line length)
                    size (+ last-line extra)]
                (gen/bind (gen/tuple (gen/choose (inc line) last-line) (gen/vector hits size))
                          (fn [[inner-line lines]]
                            (gen/bind (gen/tuple (gen/choose inner-line last-line) (gen/vector hits size))
                                      (fn [[inner-last other]]
                                        (gen/return
                                         {:outer {:file "lib/a.rb" :line line :last-line last-line :name "outer" :complexity 2}
                                          :inner {:file "lib/a.rb" :line inner-line :last-line inner-last :name "inner" :complexity 1}
                                          :lines lines
                                          :rehit (vec (map-indexed (fn [i hit] (if (<= (dec inner-line) i (dec inner-last)) (other i) hit))
                                                                   lines))})))))))))

(defn- own-hits [{:keys [outer inner]} lines]
  (keep (fn [line] (when-not (<= (:line inner) line (:last-line inner)) (get lines (dec line))))
        (range (inc (:line outer)) (inc (:last-line outer)))))

(defspec method-coverage-is-the-share-of-its-own-lines-that-ran trials
  (prop/for-all [{:keys [outer inner lines rehit] :as case} nesting]
    (let [methods [outer inner]
          coverage #(crap/method-coverage outer methods {"lines" % "methods" []})
          relevant (filter number? (own-hits case lines))]
      (and (<= 0.0 (coverage lines) 1.0)
           (== (coverage lines) (coverage rehit))
           (== (coverage lines) (if (seq relevant) (/ (count (filter pos? relevant)) (count relevant)) 0.0))))))

(defspec a-method-without-own-lines-is-covered-exactly-when-called trials
  (prop/for-all [[line length] span calls (gen/choose 0 3)]
    (let [method {:file "lib/a.rb" :line line :last-line (+ line length) :name "m" :complexity 1}
          entry {"lines" (vec (repeat (+ line length 1) nil))
                 "methods" [{"start_line" line "coverage" calls}]}]
      (== (if (pos? calls) 1.0 0.0) (crap/method-coverage method [method] entry)))))

(def scored-method
  (gen/fmap (fn [[file [line length] complexity]]
              {:file file :line line :last-line (+ line length) :name "m" :complexity complexity})
            (gen/tuple ruby-file span (gen/choose 1 30))))

(defspec scores-keep-every-method-between-its-complexity-and-the-uncovered-score trials
  (prop/for-all [methods (gen/vector scored-method 0 8) covered (gen/set ruby-file) lines (gen/vector hits 0 100)]
    (let [report {"coverage" (into {} (for [file covered] [(str root "/" file) {"lines" lines "methods" []}]))}
          scored (crap/score methods report root)]
      (and (= methods (map #(dissoc % :coverage :crap) scored))
           (every? (fn [{:keys [complexity crap]}] (<= complexity crap (+ (* complexity complexity) complexity))) scored)
           (every? (fn [{:keys [file coverage]}] (or (covered file) (== 0.0 coverage))) scored)))))

;; Ruby sources

(def path
  (gen/fmap (fn [[dirs name]] (str/join "/" (conj dirs name)))
            (gen/tuple (gen/vector (gen/elements ["lib" "app" "spec" "vendor" "tmp" "node_modules" "coverage" "engines"]) 0 3)
                       (gen/elements ["a.rb" "b_spec.rb" "c_property_spec.rb" "spec.rb" "d.txt" "Gemfile"]))))

(def ignored #{"node_modules" "dist" "build" "coverage" "tmp" "vendor"})

(defspec production-sources-are-the-sorted-ruby-sources-outside-specs-and-ignored-directories trials
  (prop/for-all [paths (gen/vector path 0 12)]
    (let [production (commands/production-sources paths)]
      (and (= production (sort production))
           (= production (commands/production-sources (shuffle paths)) (commands/production-sources production))
           (every? (set paths) production)
           (every? #(str/ends-with? % ".rb") production)
           (not-any? commands/spec-file? production)
           (not-any? #(some ignored (butlast (str/split % #"/"))) production)
           (= (set (remove commands/spec-file? (commands/ruby-sources paths))) (set production))))))

;; mutant

(def mutant-arg
  (gen/elements [["--jobs" "9"] ["-j" "2"] ["--jobs=3"] ["-j7"] ["Ledger*"] ["--fail-fast"]
                 ["--require" "./lib/ledger"] ["Calc#grade"]]))

(defn- jobs-flag? [arg]
  (boolean (or (#{"--jobs" "-j"} arg) (re-matches #"--jobs=.*|-j\d+" arg))))

(defspec mutant-runs-four-jobs-and-keeps-the-callers-other-arguments-in-order trials
  (prop/for-all [chunks (gen/vector mutant-arg 0 6) since (gen/elements [nil "abc123"])]
    (let [extra (vec (apply concat chunks))
          args (mutant/args since extra)
          kept (vec (apply concat (remove #(jobs-flag? (first %)) chunks)))]
      (and (= ["exec" "mutant" "run" "--jobs" "4"] (take 5 args))
           (= 1 (count (filter jobs-flag? args)))
           (= (some? since) (boolean (some #{"--since"} args)))
           (= kept (subvec args (- (count args) (count kept))))
           (some #{commands/property-pattern} args)))))

(def mutant-config
  (gen/map (gen/elements [:usage :integration :includes :requires :matcher])
           (gen/one-of [(gen/elements ["opensource" "commercial" "rspec" "lib"])
                        (gen/return {:usage "opensource"})])
           {:max-elements 4}))

(defspec a-mutant-configuration-declares-a-usage-only-at-the-top-level trials
  (prop/for-all [config mutant-config]
    (= (contains? config :usage) (mutant/declares-usage? (yaml/generate-string config)))))
