(ns sf.lore
  "Lore trailers (github.com/Ian-stetsenko/lore-protocol) on task commits: where
  the commit-msg hook puts the byline and the Lore-id, and the LORE: lines that
  sf start and sf show print."
  (:require [clojure.string :as str])
  (:import (java.security SecureRandom)))

(def ^:private lore-keys
  #{"Constraint" "Rejected" "Confidence" "Scope-risk" "Reversibility" "Directive"
    "Tested" "Not-tested" "Supersedes" "Depends-on" "Related" "Lore-id"})

(defn lore-key? [key]
  (contains? lore-keys key))

(defn trailer-key
  "The key of a `Key: value` trailer line, or nil."
  [line]
  (second (re-matches #"([^:]+):.*" line)))

(defn- byline [role]
  (str "By " role "."))

(defn- byline-role
  "The role in a `By <role>.` line, or nil."
  [line]
  (second (re-matches #"By (\S+)\." line)))

(def ^:private random (SecureRandom.))

(defn new-id
  "A random Lore-id: 8 lowercase hexadecimal digits."
  []
  (format "%08x" (bit-and (.nextLong random) 0xffffffff)))

(defn- head-and-block
  "The text before the last paragraph, and the last paragraph."
  [text]
  (let [lines (vec (str/split-lines text))
        gap (last (keep-indexed #(when (str/blank? %2) %1) lines))]
    [(str/join "\n" (subvec lines 0 gap))
     (str/join "\n" (subvec lines (inc gap)))]))

(defn sign
  "The commit message with the byline `By <role>.` and a Lore-id added.
  trailer-keys are the keys of the trailers that git reads from the message's
  last paragraph, empty when it has no trailer block. The byline goes in its own
  paragraph before that block, or at the end when there is none. A block with a
  Lore trailer and no Lore-id gets `Lore-id: <(make-id)>` as its first line. A
  message that needs neither is returned as it is."
  [message role trailer-keys make-id]
  (let [text (str/replace message #"\s+\z" "")
        signature (byline role)
        add-byline? (not-any? #{signature} (str/split-lines text))
        add-id? (and (some lore-key? trailer-keys) (not-any? #{"Lore-id"} trailer-keys))
        [head block] (if (seq trailer-keys) (head-and-block text) [text nil])]
    (if (or add-byline? add-id?)
      (str (str/join "\n\n" (remove nil? [head
                                          (when add-byline? signature)
                                          (if add-id? (str "Lore-id: " (make-id) "\n" block) block)]))
           "\n")
      message)))

(defn- role-of
  "The role of the last `By <role>.` line, or -."
  [message]
  (or (last (keep byline-role (str/split-lines message))) "-"))

(defn lines
  "One `LORE: <sha> <role> <Key>: <value>` line per Lore trailer. commits are
  maps of :sha, :message, and :trailers (the unfolded trailer lines git reads)."
  [commits]
  (for [{:keys [sha message trailers]} commits
        trailer trailers
        :let [key (trailer-key trailer)]
        :when (lore-key? key)]
    (str "LORE: " sha " " (role-of message) " " key ": " (str/triml (subs trailer (inc (count key)))))))

(def log-format
  "The git log --format that read-log reads."
  "%h%x1f%B%x1f%(trailers:only,unfold)%x1e")

(defn read-log
  "The commits in git log output made with log-format."
  [output]
  (vec (for [entry (str/split output #"\x1e")
             :let [[sha message trailers] (str/split (str/replace entry #"^\n" "") #"\x1f" -1)]
             :when (not (str/blank? sha))]
         {:sha sha :message message :trailers (vec (remove str/blank? (str/split-lines trailers)))})))
