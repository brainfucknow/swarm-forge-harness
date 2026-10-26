(ns sfjs.acceptance.steps
  "Step handlers for the sf-js features. Each handler takes the world and the
  regex captures and returns the next world; a failed check throws."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [sfjs.acceptance.world :as world]))

(defn- fail [& parts]
  (throw (ex-info (apply str parts) {:assertion true})))

(defn- check [ok? & parts]
  (when-not ok? (apply fail parts)))

;; package.json, lockfiles, tsconfig.json

(defn- write-package [{:keys [package] :as w}]
  (world/write-json w "package.json" (or package {}))
  w)

(defn- list-package [w name section]
  (write-package (update-in w [:package section] assoc name "*")))

(defn- list-dev-dependencies [w names]
  (let [names (remove str/blank? (str/split names #"\s+"))]
    (write-package (-> w
                       (update-in [:package "dependencies"] dissoc "vitest" "jest")
                       (assoc-in [:package "devDependencies"] (zipmap names (repeat "*")))))))

(defn- declare-manager [w declared]
  (write-package (assoc-in w [:package "packageManager"] declared)))

(defn- add-lockfile
  "A lockfile comes with a package.json, empty unless other steps fill it."
  [w lockfile]
  (world/write-file w lockfile "")
  (write-package w))

(defn- tsconfig [w state]
  (if (= state "present")
    (world/write-file w "tsconfig.json" "{}\n")
    (fs/delete-if-exists (world/path w "tsconfig.json")))
  w)

;; Task branch changes

(defn- add-on-task-branch [w file]
  (world/write-file w file (world/numbered-lines file 20))
  (world/commit-all w (str "add " file))
  w)

(defn- change-line [w n file state]
  (let [n (parse-long n)]
    (case state
      "changed on the task branch"
      (do (world/rewrite-line w file n) (world/commit-all w (str "change " file)))
      "changed but not committed"
      (world/rewrite-line w file n)
      "changed on main after the task began"
      (do (world/git w "switch" "--quiet" (:base w))
          (world/rewrite-line w file n)
          (world/commit-all w (str "change " file " on main"))
          (world/git w "switch" "--quiet" "swarm/task")))
    w))

(defn- untracked [w file n]
  (world/write-file w file (world/numbered-lines file (parse-long n)))
  w)

;; ESLint, Istanbul, jscpd and Stryker reports

(defn- add-eslint-message [w name line file complexity]
  (let [w (update-in w [:eslint (world/path w file)] (fnil conj [])
                     {:ruleId "complexity" :severity 1
                      :message (str (if (= name "(anonymous)") "Arrow function" (str "Function '" name "'"))
                                    " has a complexity of " complexity ". Maximum allowed is 0.")
                      :line (parse-long line) :column 1 :endLine (parse-long line) :endColumn 2})]
    (world/state-file w "eslint.json"
                      (json/generate-string (for [[path messages] (:eslint w)] {:filePath path :messages messages})))
    w))

(defn- coverage-dir [w]
  (or (:coverage-dir w) "tmp/coverage"))

(defn- location [start-line start-column end-line end-column]
  {:start {:line start-line :column start-column} :end {:line end-line :column end-column}})

(defn- add-function
  "One Istanbul function spanning lines from..to with `statements` statements
  of its own on its first line, `covered` of them hit, and `calls` calls."
  [entry name from to statements covered calls]
  (let [fid (str (count (:fnMap entry)))
        sids (map #(str (+ (count (:statementMap entry)) %)) (range statements))]
    (reduce (fn [entry [i sid]]
              (-> entry
                  (assoc-in [:statementMap sid] (location from (inc i) from (+ 2 i)))
                  (assoc-in [:s sid] (if (< i covered) 1 0))))
            (-> entry
                (assoc-in [:fnMap fid] {:name name :decl (location from 0 to 999) :loc (location from 0 to 999) :line from})
                (assoc-in [:f fid] calls))
            (map-indexed vector sids))))

(defn- write-coverage [w]
  (world/write-json w (str (coverage-dir w) "/coverage-final.json") (:coverage w))
  w)

(defn- cover [w file name from to statements covered calls]
  (let [path (world/path w file)
        empty-entry {:path path :statementMap {} :fnMap {} :branchMap {} :s {} :f {} :b {}}]
    (write-coverage (update-in w [:coverage path]
                               #(add-function (or % empty-entry) name from to statements covered calls)))))

(defn- cover-statements [w covered statements name from to file]
  (let [covered (parse-long covered)]
    (cover w file name (parse-long from) (parse-long to) (parse-long statements) covered (if (pos? covered) 1 0))))

(defn- count-calls [w calls name from to file]
  (cover w file name (parse-long from) (parse-long to) 0 0 (parse-long calls)))

(defn- move-coverage [w dir]
  (let [from (world/path w (str (coverage-dir w) "/coverage-final.json"))]
    (fs/create-dirs (world/path w dir))
    (fs/move from (world/path w (str dir "/coverage-final.json")))
    (assoc w :coverage-dir dir)))

(defn- stale-coverage [w state]
  (when (= state "present") (world/write-file w "tmp/coverage/coverage-final.json" "{}\n"))
  w)

(defn- add-clone [w tokens first-file a b second-file c d]
  (let [w (update w :clones (fnil conj [])
                  {:format "typescript" :tokens (parse-long tokens) :lines (inc (- (parse-long b) (parse-long a)))
                   :firstFile {:name first-file :start (parse-long a) :end (parse-long b)}
                   :secondFile {:name second-file :start (parse-long c) :end (parse-long d)}})]
    (world/write-json w "tmp/jscpd/jscpd-report.json" {:duplicates (:clones w)})
    w))

(defn- add-mutant [w mutant]
  (let [w (update w :mutants (fnil conj []) mutant)
        files (group-by :file (:mutants w))]
    (world/write-json w "tmp/stryker/mutation.json"
                      {:schemaVersion "2" :thresholds {:high 80 :low 60}
                       :files (into {} (for [[f mutants] files]
                                         [f {:language "typescript" :source ""
                                             :mutants (for [[i {:keys [line status mutator replacement]}] (map-indexed vector mutants)]
                                                        {:id (str f "#" i) :mutatorName mutator :replacement replacement
                                                         :status status
                                                         :location (location line 1 line 2)})}]))})
    w))

(defn- one-mutant [w status mutator file line replacement]
  (add-mutant w {:file file :line (parse-long line) :status status :mutator mutator :replacement replacement}))

(defn- many-mutants [w n status]
  (reduce (fn [w _] (add-mutant w {:file "src/ledger.ts" :line (inc (count (:mutants w))) :status status
                                    :mutator "ConditionalExpression" :replacement "false"}))
          w (range (parse-long n))))

;; Fakes and environment

(defn- fake-status [w tool status]
  (world/state-file w (str "status-" tool) status)
  w)

(defn- stryker-without-report [w status]
  (world/state-file w "no-report-stryker" "")
  (fake-status w "stryker" status))

(defn- no-coverage-report [w tool]
  (world/state-file w (str "no-coverage-" tool) "")
  w)

(defn- remove-from-path [w command]
  (fs/delete-if-exists (fs/path (:bin w) command))
  w)

(defn- swarmforge-base [w value]
  (if (str/blank? value) (update w :env dissoc "SWARMFORGE_BASE") (assoc-in w [:env "SWARMFORGE_BASE"] value)))

;; Running

(defn- run-sf-js [w args]
  (check (:fakes w) "fake JavaScript tools are not on PATH")
  (assoc w :result (world/run-sf-js w (remove str/blank? (str/split args #" ")))))

(defn- result [w]
  (or (:result w) (fail "sf-js has not run")))

(defn- command-lines [w]
  (keep #(when (str/starts-with? % "$ ") (subs % 2)) (:lines (result w))))

(defn- report-lines [w]
  (remove #(str/starts-with? % "$ ") (:lines (result w))))

(defn- show [w]
  (let [{:keys [status out err]} (result w)]
    (str "\n  status " status "\n  stdout:\n" out "\n  stderr:\n" err)))

(defn- runs-tool? [line tool]
  (some #(or (= % tool) (str/starts-with? % (str tool "@"))) (str/split line #" ")))

(defn- tool-line [w tool]
  (or (first (filter #(runs-tool? % tool) (command-lines w)))
      (fail "no command line runs " tool (show w))))

(defn- exit-status [w status]
  (check (= (parse-long status) (:status (result w))) "expected exit status " status (show w))
  w)

(defn- ran [w command]
  (check (some #(str/starts-with? % command) (command-lines w)) "sf-js did not run " command (show w))
  w)

(defn- ran-before [w first-command second-command]
  (let [index (fn [command] (first (keep-indexed #(when (str/starts-with? %2 command) %1) (command-lines w))))
        a (index first-command)
        b (index second-command)]
    (check (and a b (< a b)) "expected " first-command " before " second-command (show w))
    w))

(defn- did-not-run [w text]
  (check (not-any? #(str/includes? % text) (command-lines w)) "sf-js ran " text (show w))
  w)

(defn- ran-nothing [w]
  (check (empty? (command-lines w)) "sf-js ran a command" (show w))
  w)

(def ^:private add-prefixes ["npm install --save-dev " "pnpm add --save-dev " "yarn add --dev " "bun add --dev "])

(defn- added [w packages]
  (let [adds (for [line (command-lines w)
                   prefix add-prefixes
                   :when (str/starts-with? line prefix)]
               (str/split (subs line (count prefix)) #" "))]
    (check (= 1 (count adds)) "expected one add command" (show w))
    (check (= (str/split packages #" ") (first adds)) "expected to add " packages (show w))
    w))

(defn- command-line-check [w tool relation text]
  (let [line (tool-line w tool)]
    (check (case relation
             "contains" (str/includes? line text)
             "does not contain" (not (str/includes? line text))
             "ends with" (str/ends-with? line text))
           "the " tool " command line " relation " " text ": " line)
    w))

(defn- output-check [w relation text]
  (let [lines (:lines (result w))]
    (check (case relation
             "has the line" (some #{text} lines)
             "ends with the line" (= text (last lines))
             "has a line starting with" (some #(str/starts-with? % text) lines)
             "mentions" (str/includes? (:out (result w)) text)
             "does not mention" (not (str/includes? (:out (result w)) text)))
           "the output " relation " " text (show w))
    w))

(defn- output-line [w n text]
  (check (= text (nth (vec (report-lines w)) (dec (parse-long n)) nil)) "output line " n " is not " text (show w))
  w)

(defn- error-contains [w text]
  (check (str/includes? (:err (result w)) text) "stderr lacks " text (show w))
  w)

(def steps
  [[#"a main branch where \"([^\"]+)\" has (\d+) lines" (fn [w file n] (assoc-in w [:main-files file] (parse-long n)))]
   [#"a task worktree branched from main" (fn [w] (world/create-repo w "main"))]
   [#"a task worktree branched from \"([^\"]+)\"" world/create-repo]
   [#"fake JavaScript tools on PATH" world/install-fakes]
   [#"the fake (\S+) exits with status (\d+)" fake-status]
   [#"the fake stryker exits with status (\d+) without a report" stryker-without-report]
   [#"the fake (\S+) writes no coverage report" no-coverage-report]
   [#"a stale coverage report is (present|absent) in tmp/coverage" stale-coverage]
   [#"\"([^\"]+)\" is not on PATH" remove-from-path]
   [#"SWARMFORGE_BASE is \"([^\"]*)\"" swarmforge-base]
   [#"package.json lists \"([^\"]+)\" in (dependencies|devDependencies)" list-package]
   [#"package.json lists the devDependencies \"([^\"]*)\"" list-dev-dependencies]
   [#"package.json declares packageManager \"([^\"]+)\"" declare-manager]
   [#"the worktree has the lockfile \"([^\"]+)\"" add-lockfile]
   [#"tsconfig.json is (present|absent)" tsconfig]
   [#"\"([^\"]+)\" is added on the task branch" add-on-task-branch]
   [#"line (\d+) of \"([^\"]+)\" (?:is )?(changed on the task branch|changed but not committed|changed on main after the task began)" change-line]
   [#"\"([^\"]+)\" is untracked with (\d+) lines" untracked]
   [#"ESLint reports \"([^\"]+)\" at line (\d+) of \"([^\"]+)\" with complexity (\d+)" add-eslint-message]
   [#"Istanbul covers (\d+) of (\d+) statements of \"([^\"]+)\" on lines (\d+) to (\d+) of \"([^\"]+)\"" cover-statements]
   [#"Istanbul counts (\d+) calls of \"([^\"]+)\" on lines (\d+) to (\d+) of \"([^\"]+)\" with no statements" count-calls]
   [#"the coverage report is moved to \"([^\"]+)\"" move-coverage]
   [#"the jscpd report has a (\d+)-token clone of \"([^\"]+)\" lines (\d+) to (\d+) and \"([^\"]+)\" lines (\d+) to (\d+)" add-clone]
   [#"the Stryker report has an? (\S+) \"([^\"]+)\" mutant in \"([^\"]+)\" at line (\d+) replacing it with \"([^\"]*)\"" one-mutant]
   [#"the Stryker report has (\d+) mutants with status \"([^\"]+)\"" many-mutants]
   [#"I run sf-js with \"([^\"]*)\"" run-sf-js]
   [#"I run \"sf tool ensure sf-js\" and then \"sf tool require sf-js\"" world/run-sf-tool]
   [#"the exit status is (\d+)" exit-status]
   [#"sf-js ran \"([^\"]+)\" before \"([^\"]+)\"" ran-before]
   [#"sf-js ran \"([^\"]+)\"" ran]
   [#"sf-js did not run \"([^\"]+)\"" did-not-run]
   [#"sf-js ran nothing" ran-nothing]
   [#"sf-js added the devDependencies \"([^\"]*)\"" added]
   [#"the command line that runs \"([^\"]+)\" (contains|does not contain|ends with) \"([^\"]*)\"" command-line-check]
   [#"the output (has the line|ends with the line|has a line starting with|mentions|does not mention) \"([^\"]*)\"" output-check]
   [#"output line (\d+) is \"([^\"]*)\"" output-line]
   [#"the error output contains \"([^\"]*)\"" error-contains]])

(def handlers
  {:steps steps :fresh world/fresh :cleanup world/cleanup})
