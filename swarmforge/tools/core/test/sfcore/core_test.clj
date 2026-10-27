(ns sfcore.core-test
  "Boundaries and defaults of the shared core that the language suites do not
  pin: diff edge cases, path matching, scoring thresholds, report text, git
  and process edges."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sfcore.changes :as changes]
            [sfcore.command :as command]
            [sfcore.crap :as crap]
            [sfcore.git :as git]
            [sfcore.jscpd :as jscpd]
            [sfcore.outcome :as outcome]
            [sfcore.shell :as shell]
            [sfcore.stryker :as stryker]))

(defn- diff [& lines] (str/join "\n" lines))

(deftest diff-deletions-change-the-line-after-them
  (testing "a deletion changes the new-side line that now follows it"
    (is (= {"a.js" #{3}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -3 +2,0 @@" "-gone"))))
    (is (= {"a.js" #{10}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -10,2 +9,0 @@" "-one" "-two")))))
  (testing "a deletion at the top of the file changes line 1"
    (is (= {"a.js" #{1}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -1,2 +0,0 @@" "-one" "-two")))))
  (testing "deletions and additions in one file"
    (is (= {"a.js" #{2 7}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -2 +2 @@" "-old" "+new" "@@ -8 +6,0 @@" "-gone")))))
  (testing "a deleted file has no new side"
    (is (= {}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ /dev/null" "@@ -1,2 +0,0 @@" "-one" "-two"))))))

(deftest diff-file-headers
  (testing "a file named twice keeps the lines found so far"
    (is (= {"a.js" #{2 9}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -1,0 +2 @@" "+x"
                  "--- a/a.js" "+++ b/a.js" "@@ -8,0 +9 @@" "+y")))))
  (testing "an added line that looks like a file header is a line, not a file"
    (is (= {"a.js" #{4 5}}
           (changes/changed-lines-from-diff
            (diff "--- a/a.js" "+++ b/a.js" "@@ -3,0 +4,2 @@" "+++ b/b.js" "+z"))))))

(deftest path-matching-among-several-changed-files
  (let [changed {"src/a.js" #{1} "src/b.js" #{2}}]
    (is (= "src/b.js" (changes/to-changed-path "/elsewhere/src/b.js" changed "/repo")))
    (is (= "src/a.js" (changes/to-changed-path "/elsewhere/src/a.js" changed "/repo")))
    (is (nil? (changes/to-changed-path "/elsewhere/src/c.js" changed "/repo")))))

(deftest crap-thresholds-and-rows
  (is (false? (crap/over? {:crap 10.0})))
  (is (true? (crap/over? {:crap 10.01})))
  (is (= "ok   crap   10.0  cc   4  cov  25%  a.js:1 f"
         (crap/format-row {:crap 10.0 :complexity 4 :coverage 0.25 :file "a.js" :line 1 :name "f"})))
  (is (= "OVER crap   12.0  cc 2.5  cov  67%  a.js:1 f"
         (crap/format-row {:crap 12.0 :complexity 2.5 :coverage 0.666 :file "a.js" :line 1 :name "f"}))))

(deftest stryker-mutants-and-score
  (let [mutants (stryker/parse-mutants
                 {"files" {"a.js" {"mutants" [{"status" "Survived" "mutatorName" "M"
                                               "description" "described" "replacement" "swapped"
                                               "location" {"start" {"line" 3}}}
                                              {"status" "Killed" "mutatorName" "M"}]}}})]
    (is (= [["a.js" 0 "Killed"] ["a.js" 3 "Survived"]]
           (map (juxt :file :line :status) mutants)))
    (is (= "Survived   a.js:3  M  described" (stryker/format-mutant (second mutants))))
    (is (= "Killed     a.js:0  M" (stryker/format-mutant (first mutants)))))
  (testing "the score is the share of scored mutants that were killed"
    (let [statuses (concat (repeat 3 "Killed") ["Survived"])]
      (is (= 75.0 (:score (stryker/summarize (map (fn [s] {:status s}) statuses))))))))

(deftest jscpd-sides-need-an-overlap-to-count-as-changed
  (let [clone {:tokens 60 :lines 5 :sides [{:file "a" :start 1 :end 2} {:file "b" :start 5 :end 6}]}
        [touching] (jscpd/clones-touching-changes [clone] {"a" #{1} "b" #{9}} "/r")]
    (is (= [true false] (map :changed (:sides touching))))))

(deftest outcomes
  (is (true? (:usage (ex-data (outcome/usage-error "m")))))
  (is (= [0 1 2] [outcome/exit-ok outcome/exit-findings outcome/exit-usage])))

(defn with-out-str-status [f]
  (let [result (volatile! nil)]
    (with-out-str (vreset! result (f)))
    @result))

(deftest shell-edges
  (is (= 1 (with-out-str-status #(shell/run "false" []))))
  (is (= 0 (with-out-str-status #(shell/run "true" []))))
  (is (true? (shell/available? "bb")))
  (is (false? (shell/available? "no-such-command-xyz"))))

;; Git, in scratch repositories.

(defn- sh [dir & args]
  (apply p/shell {:dir (str dir) :out :string :err :string} args))

(defn- scratch-repo
  "A repository whose only branch is `branch`, with one committed file."
  [branch]
  (let [dir (str (fs/canonicalize (fs/create-temp-dir {:prefix "sfcore-git"})))]
    (sh dir "git" "init" "--quiet" (str "--initial-branch=" branch))
    (sh dir "git" "config" "user.email" "t@example.com")
    (sh dir "git" "config" "user.name" "T")
    (sh dir "git" "config" "commit.gpgsign" "false")
    (spit (str (fs/path dir "a.js")) "one\n")
    (sh dir "git" "add" ".")
    (sh dir "git" "commit" "--quiet" "-m" "base")
    dir))

(defmacro with-repo [[sym branch] & body]
  `(let [~sym (scratch-repo ~branch)]
     (try ~@body (finally (fs/delete-tree ~sym)))))

(deftest git-reports-its-own-failures
  (with-repo [dir "main"]
    (is (str/starts-with? (try (git/git ["rev-parse" "--verify" "nope"] :dir dir)
                               (catch clojure.lang.ExceptionInfo e (ex-message e)))
                          "git rev-parse --verify nope: "))
    (is (= (str dir) (git/repo-root :dir dir)))))

(deftest refs-are-checked-in-the-given-or-bound-directory
  (with-repo [dir "only-here"]
    (is (true? (git/ref-exists? "only-here" dir)))
    (is (false? (git/ref-exists? "nope" dir)))
    (binding [shell/*dir* dir]
      (is (true? (git/ref-exists? "only-here" nil))))))

(deftest the-default-base-is-the-first-existing-candidate
  (with-repo [dir "main"]
    (is (= "main" (git/default-base dir))))
  (with-repo [dir "trunk"]
    (is (= "no base branch found; pass --base <ref> or set SWARMFORGE_BASE"
           (try (git/default-base dir) (catch clojure.lang.ExceptionInfo e (ex-message e))))))
  (is (= "main" (git/first-existing ["nope" nil "main" "master"] #{"main" "master"})))
  (is (nil? (git/first-existing [nil "nope"] #{"main"})))
  (is (= "feature" (git/base-ref {:base "feature"}))))

(deftest untracked-files-count-whole-files
  (with-repo [dir "main"]
    (spit (str (fs/path dir "new.js")) "a\nb\nc\n")
    (let [changed (git/collect-changed-lines {:base "main" :dir dir :pathspecs ["*.js"]})
          lines (get changed "new.js")]
      (is (every? lines [1 2 3]))
      (is (not (contains? lines 0)))
      (is (= #{"new.js"} (set (keys changed)))))
    (is (nil? (git/changed-since-base {:all true} ["*.js"])))))

(deftest commands-receive-their-arguments
  (let [seen (atom nil)
        spec (fn [help-option?]
               {:usage "usage: x" :help-option? help-option?
                :option-spec {:coerce {:all :boolean :help :boolean} :alias {:h :help}}
                :commands {"run" (fn [call] (reset! seen call) 7)}})
        main (fn [help-option? & argv] (let [result (volatile! nil)] (with-out-str (vreset! result (command/main (spec help-option?) (vec argv)))) @result))]
    (testing "arguments, options and the words after --"
      (is (= 7 (main false "run" "a" "--all" "--" "x" "y")))
      (is (= {:args ["a" "--all" "--" "x" "y"] :opts {:all true} :extra ["x" "y"]} @seen)))
    (testing "no words after -- means no extras"
      (main false "run" "a")
      (is (= [] (:extra @seen))))
    (testing "help, no command, or an unknown command"
      (is (= 0 (main false)))
      (is (= "usage: x\n" (with-out-str (command/main (spec false) []))))
      (is (= 0 (main false "help")))
      (is (= 0 (main false "--help")))
      (is (= 0 (main false "-h")))
      (is (= "unknown command 'nope'\nusage: x"
             (try (main false "nope") (catch clojure.lang.ExceptionInfo e (ex-message e))))))
    (testing "--help after a command: usage when asked, else the command's own"
      (reset! seen nil)
      (is (= 0 (main true "run" "--help")))
      (is (nil? @seen))
      (is (= 7 (main false "run" "--help")))
      (is (= {:help true} (:opts @seen))))))
