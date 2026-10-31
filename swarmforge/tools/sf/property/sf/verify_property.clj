(ns sf.verify-property
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speclj.core :refer [describe it should]]
            [sf.property-support :refer [passes? sentence]]
            [sf.verify :as verify]))

(def ^:private padding (gen/elements ["" " " "  " "\t"]))

(def ^:private command
  "A command and how it may appear on its line of verify.conf."
  (gen/let [text (gen/such-that #(not (str/starts-with? % "#")) sentence)
            left padding
            right padding]
    {:command text :line (str left text right)}))

(def ^:private skipped
  "A blank or comment line, which holds no command."
  (gen/let [left padding
            comment (gen/one-of [(gen/return "") (gen/fmap #(str "#" %) sentence)])]
    {:line (str left comment)}))

(def ^:private conf
  "The text of a verify.conf and the commands in it, in order."
  (gen/let [lines (gen/vector (gen/frequency [[3 command] [2 skipped]]) 0 12)
            ending (gen/elements ["\n" "\r\n"])
            tail (gen/elements ["" "\n"])]
    {:commands (vec (keep :command lines))
     :text (str (str/join ending (map :line lines)) tail)}))

(describe "the verification commands"
  (it "are the trimmed command lines, in file order, whatever the blank lines, comments and line endings"
    (should (passes? (prop/for-all [{:keys [text commands]} conf]
                       (= commands (verify/commands text))))))

  (it "read back unchanged when written one per line"
    (should (passes? (prop/for-all [{:keys [text]} conf]
                       (let [commands (verify/commands text)]
                         (= commands (verify/commands (str/join "\n" commands)))))))))
