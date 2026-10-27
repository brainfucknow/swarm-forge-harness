(ns sf.lore-spec
  (:require [speclj.core :refer [describe it should= should-be-nil context]]
            [sf.lore :as lore]))

(defn- sign [message trailer-keys]
  (lore/sign message "qa" trailer-keys (constantly "0badc0de")))

(describe "sign"
  (context "byline"
    (it "appends the byline to a message without a trailer block"
      (should= "Fix\n\nBy qa.\n" (sign "Fix\n" [])))

    (it "appends the byline after body text"
      (should= "Fix\n\nBody text\n\nBy qa.\n" (sign "Fix\n\nBody text\n" [])))

    (it "puts the byline in its own paragraph before the trailer block"
      (should= "Fix\n\nBody text\n\nBy qa.\n\nSigned-off-by: A <a@b>\nSession: s1\n"
               (sign "Fix\n\nBody text\n\nSigned-off-by: A <a@b>\nSession: s1\n" ["Signed-off-by" "Session"])))

    (it "keeps the paragraphs before the trailer block as they are"
      (should= "Fix\n\nOne\n\n\nTwo\n\nBy qa.\n\nSession: s1\n"
               (sign "Fix\n\nOne\n\n\nTwo\n\nSession: s1\n" ["Session"])))

    (it "drops trailing blank lines before it adds the byline"
      (should= "Fix\n\nBy qa.\n" (sign "Fix\n\n\n" [])))

    (it "leaves a message that has the byline alone"
      (should= "Fix\n\nBody text\nBy qa.\n" (sign "Fix\n\nBody text\nBy qa.\n" []))
      (should= "Fix\n\nBy qa.\n\nCo-Authored-By: A <a@b>\n"
               (sign "Fix\n\nBy qa.\n\nCo-Authored-By: A <a@b>\n" ["Co-Authored-By"])))

    (it "adds the byline when only another role signed"
      (should= "Fix\n\nBy coder.\n\nBy qa.\n" (sign "Fix\n\nBy coder.\n" []))))

  (context "Lore-id"
    (it "adds a Lore-id as the first trailer when the block has a Lore trailer"
      (should= "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nCo-Authored-By: A <a@b>\nTested: x\n"
               (sign "Fix\n\nCo-Authored-By: A <a@b>\nTested: x\n" ["Co-Authored-By" "Tested"])))

    (it "adds a Lore-id to a signed message"
      (should= "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x\n"
               (sign "Fix\n\nBy qa.\n\nTested: x\n" ["Tested"])))

    (it "adds no Lore-id when the block has no Lore trailer"
      (should= "Fix\n\nBy qa.\n\nTested-by: A <a@b>\n" (sign "Fix\n\nTested-by: A <a@b>\n" ["Tested-by"])))

    (it "keeps an existing Lore-id"
      (should= "Fix\n\nBy qa.\n\nTested: x\nLore-id: 1234abcd\n"
               (sign "Fix\n\nTested: x\nLore-id: 1234abcd\n" ["Tested" "Lore-id"])))

    (it "asks for a new id for each message"
      (let [ids (atom ["11111111" "22222222"])
            next-id #(let [[id] @ids] (swap! ids rest) id)]
        (should= ["Fix\n\nBy qa.\n\nLore-id: 11111111\nTested: x\n"
                  "Fix\n\nBy qa.\n\nLore-id: 22222222\nTested: x\n"]
                 (repeatedly 2 #(lore/sign "Fix\n\nTested: x\n" "qa" ["Tested"] next-id)))))))

(def ^:private template "\n# Please enter the commit message for your changes.\n#\n# On branch b\n")

(defn- sign-commit
  ([message trailer-keys] (sign-commit message "#" trailer-keys))
  ([message comment-char trailer-keys]
   (lore/sign-commit message comment-char "qa" trailer-keys (constantly "0badc0de"))))

(describe "sign-commit"
  (it "signs a message without a comment block as sign does"
    (should= "Fix\n\nBy qa.\n" (sign-commit "Fix\n" []))
    (should= "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x\n" (sign-commit "Fix\n\nTested: x\n" ["Tested"])))

  (it "keeps the trailer block above git's comment block"
    (should= (str "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x\n" template)
             (sign-commit (str "Fix\n\nTested: x\n" template) ["Tested"])))

  (it "appends the byline above git's comment block"
    (should= (str "Fix\n\nBy qa.\n" template) (sign-commit (str "Fix\n" template) [])))

  (it "keeps a comment line with text after it in place"
    (should= (str "Fix\n\n#1 done\nBody\n\nBy qa.\n" template) (sign-commit (str "Fix\n\n#1 done\nBody\n" template) [])))

  (it "takes everything from the scissors line on as comments"
    (let [verbose (str template "# ------------------------ >8 ------------------------\n"
                       "# Do not modify or remove the line above.\ndiff --git a/f b/f\n+Tested: no\n")]
      (should= (str "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x\n" verbose)
               (sign-commit (str "Fix\n\nTested: x\n" verbose) ["Tested"]))))

  (it "uses the comment character it is given"
    (should= "Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x\n\n; Please enter\n"
             (sign-commit "Fix\n\nTested: x\n\n; Please enter\n" ";" ["Tested"])))

  (it "leaves a signed message as it is"
    (should= (str "Fix\n\nBy qa.\n" template) (sign-commit (str "Fix\n\nBy qa.\n" template) [])))

  (it "leaves a message with no text alone, so git aborts an empty commit"
    (should= template (sign-commit template []))
    (should= "" (sign-commit "" []))))

(describe "lore-key?"
  (it "knows every Lore trailer key"
    (should= [true true true true true true true true true true true true]
             (map lore/lore-key? ["Constraint" "Rejected" "Confidence" "Scope-risk" "Reversibility" "Directive"
                                  "Tested" "Not-tested" "Supersedes" "Depends-on" "Related" "Lore-id"])))

  (it "rejects other keys"
    (should= [false false false] (map lore/lore-key? ["Tested-by" "Co-Authored-By" "tested"]))))

(describe "new-id"
  (it "is 8 lowercase hexadecimal digits"
    (should= true (every? #(re-matches #"[0-9a-f]{8}" %) (repeatedly 200 lore/new-id))))

  (it "differs between calls"
    (should= 50 (count (set (repeatedly 50 lore/new-id))))))

(describe "trailer-key"
  (it "is the text before the first colon"
    (should= "Rejected" (lore/trailer-key "Rejected: a | b: c")))

  (it "is nil for a line without a colon"
    (should-be-nil (lore/trailer-key "By qa."))))

(describe "lines"
  (it "prints one line per Lore trailer with the commit's sha and role"
    (should= ["LORE: abc1234 qa Lore-id: 0badc0de"
              "LORE: abc1234 qa Tested: placement"
              "LORE: abc1234 qa Not-tested: Windows"]
             (lore/lines [{:sha "abc1234"
                           :message "Verify\n\nBy qa.\n\nLore-id: 0badc0de\nTested: placement\nNot-tested: Windows\n"
                           :trailers ["Lore-id: 0badc0de" "Tested: placement" "Not-tested: Windows"]}])))

  (it "keeps the commits in the order given"
    (should= ["LORE: c1 specifier Constraint: keep the byline" "LORE: c3 qa Tested: x"]
             (lore/lines [{:sha "c1" :message "Spec\n\nBy specifier.\n" :trailers ["Constraint: keep the byline"]}
                          {:sha "c2" :message "Code\n\nBy coder.\n" :trailers []}
                          {:sha "c3" :message "Verify\n\nBy qa.\n" :trailers ["Tested: x"]}])))

  (it "leaves out trailers that are not Lore trailers"
    (should= ["LORE: c1 qa Tested: x"]
             (lore/lines [{:sha "c1" :message "V\n\nBy qa.\n"
                           :trailers ["Co-Authored-By: A <a@b>" "Tested: x" "Tested-by: B <c@d>"]}])))

  (it "uses the role of the last byline"
    (should= ["LORE: c1 qa Tested: x"]
             (lore/lines [{:sha "c1" :message "V\n\nBy coder.\n\nBy qa.\n" :trailers ["Tested: x"]}])))

  (it "shows the role as - when the commit has no byline"
    (should= ["LORE: c1 - Tested: x"]
             (lore/lines [{:sha "c1" :message "V\n\nBy qa\nsigned by qa.\n" :trailers ["Tested: x"]}])))

  (it "keeps a colon in the value"
    (should= ["LORE: c1 qa Rejected: a | b: c"]
             (lore/lines [{:sha "c1" :message "V\n\nBy qa.\n" :trailers ["Rejected: a | b: c"]}]))))

(describe "read-log"
  (it "reads the commits that git log prints in log-format"
    (should= [{:sha "c1" :message "Spec\n\nBy specifier.\n\nTested: x\n" :trailers ["Tested: x"]}
              {:sha "c2" :message "Code\n" :trailers []}]
             (lore/read-log (str "c1\u001fSpec\n\nBy specifier.\n\nTested: x\n\u001fTested: x\n\u001e\n"
                                 "c2\u001fCode\n\u001f\u001e\n"))))

  (it "reads no commits from empty output"
    (should= [] (lore/read-log ""))))
