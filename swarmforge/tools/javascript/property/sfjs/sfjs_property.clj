(ns sfjs.sfjs-property
  "Properties of sf-js's package, source-file, complexity-linter, CRAP and
  Stryker policy."
  (:require [clojure.string :as str]
            [property-support :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sfcore.crap :as core-crap]
            [sfjs.commands :as commands]
            [sfjs.crap :as crap]
            [sfjs.eslint :as eslint]
            [sfjs.mutate :as mutate]
            [sfjs.oxlint :as oxlint]
            [sfjs.package :as pkg]))

(def trials 200)

;; Package manager

(defn- outcome [package present]
  (try
    [:ok (pkg/detect-manager package present)]
    (catch clojure.lang.ExceptionInfo e
      (if (:usage (ex-data e)) [:usage] (throw e)))))

(defn- expected
  "The rule: a declared manager needs its own lockfile; without one, exactly
  one manager's lockfiles must be present."
  [declared present]
  (let [found (set (map pkg/lockfile-managers present))
        known (set (vals pkg/lockfile-managers))]
    (cond
      (and declared (not (known declared))) [:usage]
      declared (if (found declared) [:ok declared] [:usage])
      (= 1 (count found)) [:ok (first found)]
      :else [:usage])))

(defspec the-manager-follows-the-declaration-and-the-lockfiles trials
  (prop/for-all [declared (gen/elements [nil "npm" "pnpm" "yarn" "bun" "deno"])
                 version (gen/elements ["" "@9.1.0" "@4.5.0+sha512.abc"])
                 present (gen/fmap shuffle (gen/set (gen/elements pkg/lockfiles)))
                 shuffled gen/boolean]
    (let [package (if declared {"packageManager" (str declared version)} {})
          result (outcome package present)]
      (and (= (expected declared present) result)
           (= result (outcome package (if shuffled (reverse present) (sort present))))))))

;; Complexity linter and tool packages

(def package-with-tools
  (gen/fmap (fn [[deps dev]] {"dependencies" (zipmap deps (repeat "1")) "devDependencies" (zipmap dev (repeat "1"))})
            (gen/tuple (gen/set (gen/elements ["oxlint" "eslint" "vitest" "react"]))
                       (gen/set (gen/elements ["oxlint" "eslint" "typescript-eslint" "jest" "vitest" "typescript"])))))

(defn- listed [package]
  (into (set (keys (get package "dependencies"))) (keys (get package "devDependencies"))))

(defspec the-complexity-linter-is-oxlint-unless-only-eslint-is-listed trials
  (prop/for-all [package package-with-tools]
    (let [names (listed package)]
      (= (if (and (names "eslint") (not (names "oxlint"))) "eslint" "oxlint")
         (pkg/complexity-linter package)))))

(defspec tools-adds-only-the-chosen-linters-packages-and-then-nothing-more trials
  (prop/for-all [package package-with-tools tsconfig? gen/boolean]
    (let [linter (pkg/complexity-linter package)
          wanted (set (pkg/tool-packages package tsconfig?))
          missing (pkg/missing-packages package tsconfig?)
          installed (update package "devDependencies" merge (zipmap missing (repeat "1")))]
      (and (= (= "oxlint" linter) (contains? wanted "oxlint"))
           (= (= "eslint" linter) (contains? wanted "eslint") (contains? wanted "typescript-eslint"))
           (not-any? (listed package) missing)
           (= linter (pkg/complexity-linter installed))
           (= [] (pkg/missing-packages installed tsconfig?))))))

;; Complexity messages

(def identifier
  (gen/fmap (fn [[c more]] (str c more)) (gen/tuple gen/char-alpha gen/string-alphanumeric)))

(defn- message [kind complexity]
  (str kind " has a complexity of " complexity ". Maximum allowed is 0."))

(defspec oxlint-messages-give-back-the-name-and-complexity trials
  (prop/for-all [kind (gen/elements ["function" "method" "getter" "setter" "private method" "class static block" "arrow function"])
                 name (gen/one-of [(gen/return nil) identifier])
                 complexity (gen/choose 1 500)]
    (let [text (message (if name (str kind " `" name "`") kind) complexity)]
      (and (= (cond (nil? name) "(anonymous)" (= "private method" kind) (str "#" name) :else name)
              (oxlint/function-name text))
           (= complexity (:complexity (crap/complexity-message text)))))))

(defspec eslint-messages-give-back-the-name-and-complexity trials
  (prop/for-all [kind (gen/elements ["Function" "Method" "Static method" "Getter" "Async function"])
                 shape (gen/elements [:quoted :private :anonymous])
                 name identifier
                 complexity (gen/choose 1 500)]
    (let [text (message (case shape
                          :quoted (str kind " '" name "'")
                          :private (str "Private method #" name)
                          :anonymous "Arrow function")
                        complexity)]
      (and (= (case shape :quoted name :private (str "#" name) :anonymous "(anonymous)")
              (eslint/function-name text))
           (= complexity (:complexity (crap/complexity-message text)))))))

(defspec oxlint-functions-end-on-the-line-they-start trials
  (prop/for-all [diagnostics (gen/vector (gen/tuple (gen/elements ["eslint(complexity)" "eslint(no-debugger)"])
                                                    (gen/choose 1 300) (gen/choose 1 80) (gen/choose 1 40))
                                         0 8)]
    (let [report {"diagnostics" (for [[code line column complexity] diagnostics]
                                  {"code" code "filename" "/w/src/a.ts" "message" (message "function `f`" complexity)
                                   "labels" [{"span" {"line" line "column" column}}]})}
          functions (oxlint/parse-report report "/w")]
      (= (for [[code line column complexity] diagnostics :when (= "eslint(complexity)" code)]
           {:file "src/a.ts" :line line :column column :end-line line :name "f" :complexity complexity})
         functions))))

;; oxlint-disable directives

(def hiding-directives
  ["// oxlint-disable-next-line complexity" "// oxlint-disable-next-line eslint/complexity" "/* oxlint-disable */"
   "return 0; // oxlint-disable-line" "/* oxlint-disable no-debugger, complexity */"
   "// oxlint-disable-line no-debugger complexity -- legacy code" "//oxlint-disable"])

(def harmless-lines
  ["export const a = 1;" "// oxlint-disable-next-line no-debugger" "// eslint-disable-next-line complexity"
   "// oxlint-enable complexity" "// oxlint-disable-next-line no-complexity-check"
   "// oxlint-disable-next-line no-debugger -- complexity is fine" "const note = \"oxlint-disable\";" ""])

(defspec hiding-lines-are-exactly-the-directives-that-can-hide-complexity trials
  (prop/for-all [lines (gen/vector (gen/one-of [(gen/fmap #(vector :hides %) (gen/elements hiding-directives))
                                                (gen/fmap #(vector :harmless %) (gen/elements harmless-lines))])
                                   0 15)]
    (= (vec (keep-indexed (fn [i [kind _]] (when (= :hides kind) (inc i))) lines))
       (oxlint/hiding-lines (str/join "\n" (map second lines))))))

;; Source files

(def path
  (gen/fmap (fn [[dirs tests? base ext]] (str (str/join "/" (cond-> dirs tests? (conj "__tests__"))) "/" base "." ext))
            (gen/tuple (gen/vector (gen/elements ["src" "lib" "app" "test"]) 0 2)
                       gen/boolean
                       (gen/elements ["ledger" "index" "a.b"])
                       (gen/elements (concat commands/extensions
                                             ["d.ts" "d.mts" "json" "md" "test.ts" "spec.jsx" "property.test.ts" "min.js"])))))

(defspec production-sources-keep-exactly-the-non-test-sources trials
  (prop/for-all [paths (gen/vector path 0 10)]
    (let [sources (commands/production-sources paths)
          wanted? #(and (commands/source-file? %) (not (commands/test-file? %)))]
      (and (= sources (sort (filter wanted? paths)))
           (= sources (commands/production-sources sources))
           (not-any? #(re-find #"\.d\.[mc]?ts$|\.(test|spec)\.|__tests__/" %) sources)))))

;; CRAP per function

(defn- location [[l1 c1 l2 c2]]
  {"start" {"line" l1 "column" c1} "end" {"line" l2 "column" c2}})

(def span
  (gen/fmap (fn [[line column length width]] (location [line column (+ line length) (+ column width)]))
            (gen/tuple (gen/choose 1 40) (gen/choose 0 20) (gen/choose 0 10) (gen/choose 1 20))))

(defn- numbered [xs]
  (into {} (map-indexed (fn [i x] [(str i) x]) xs)))

(def istanbul-entry
  (gen/fmap (fn [[functions statements]]
              {"fnMap" (numbered (map (fn [[loc _]] {"name" "f" "loc" loc}) functions))
               "f" (numbered (map second functions))
               "statementMap" (numbered (map first statements))
               "s" (numbered (map second statements))})
            (gen/tuple (gen/vector (gen/tuple span (gen/choose 0 3)) 0 6)
                       (gen/vector (gen/tuple span (gen/choose 0 3)) 0 12))))

(defspec function-coverage-is-a-share-of-each-function trials
  (prop/for-all [entry istanbul-entry]
    (let [functions (crap/function-coverage entry)
          all-hit? (and (every? pos? (vals (get entry "s"))) (every? pos? (vals (get entry "f"))))
          none-hit? (and (every? zero? (vals (get entry "s"))) (every? zero? (vals (get entry "f"))))]
      (and (= (count (get entry "fnMap")) (count functions))
           (every? #(<= 0.0 (:coverage %) 1.0) functions)
           (or (not all-hit?) (every? #(== 1.0 (:coverage %)) functions))
           (or (not none-hit?) (every? #(== 0.0 (:coverage %)) functions))))))

(def eslint-function
  (gen/fmap (fn [[line length complexity]]
              {:file "src/a.ts" :line line :column 1 :end-line (+ line length) :name "f" :complexity complexity})
            (gen/tuple (gen/choose 1 60) (gen/choose 0 10) (gen/choose 1 30))))

(defspec uncovered-functions-score-their-full-complexity trials
  (prop/for-all [functions (gen/vector eslint-function 0 8)]
    (let [scored (crap/score functions {} "/repo")]
      (= (map (fn [{:keys [file line end-line complexity]}]
                [file line end-line 0.0 (double (+ (* complexity complexity) complexity))])
              functions)
         (map (juxt :file :line :last-line :coverage :crap) scored)))))

(defspec selection-keeps-changed-functions-worst-first trials
  (prop/for-all [functions (gen/vector eslint-function 0 8) lines (gen/set (gen/choose 1 80))]
    (let [scored (crap/score functions {} "/repo")
          changed {"src/a.ts" lines}
          selected (crap/select scored changed)]
      (and (= (set (crap/select scored nil)) (set scored))
           (= (set selected) (set (filter #(some lines (range (:line %) (inc (:last-line %)))) scored)))
           (= selected (core-crap/sort-worst-first selected))))))

;; Stryker command line

(def extra
  (gen/elements [[] ["--concurrency" "8"] ["-c" "2"] ["--concurrency=3"] ["--logLevel" "debug"]]))

(defspec stryker-runs-with-one-concurrency-and-the-callers-arguments-last trials
  (prop/for-all [runner (gen/elements ["vitest" "jest"])
                 tsconfig? gen/boolean
                 files (gen/one-of [(gen/return nil) (gen/vector (gen/elements ["src/a.ts" "src/b.js"]) 1 3)])
                 extra extra]
    (let [args (mutate/stryker-args {:runner runner :tsconfig? tsconfig? :files files :extra extra})
          concurrency (count (filter #(or (#{"--concurrency" "-c"} %) (str/starts-with? % "--concurrency=")) args))]
      (and (= 1 concurrency)
           (= extra (vec (take-last (count extra) args)))
           (= (boolean (seq files)) (boolean (some #{"--mutate"} args)))
           (= tsconfig? (boolean (some #{"--checkers"} args)))
           (= runner (second (drop-while #(not= "--testRunner" %) args)))))))
