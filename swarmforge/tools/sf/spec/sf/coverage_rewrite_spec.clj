(ns sf.coverage-rewrite-spec
  (:require [speclj.core :refer [describe it should=]]
            [sf.coverage-rewrite :as coverage-rewrite]))

(describe "the bash lines of a plan"
  (it "move into the directory and set the quoted arguments"
    (should= "cd '/m/it'\\''s' || exit 1\nset -- '-s' 'src' ''\n"
             (coverage-rewrite/bash {:dir "/m/it's" :args ["-s" "src" ""]})))

  (it "stay without a directory and clear the arguments without any"
    (should= "set --\n" (coverage-rewrite/bash {:dir nil :args []}))))
