(ns sfrb.flay-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [sfrb.flay :as flay]))

(def fixtures (str (fs/path (fs/parent (fs/parent (fs/parent *file*))) "fixtures")))

(deftest clones-carry-mass-and-every-side
  (is (= [{:mass 585 :sides [{:file "lib/a.rb" :start 2 :end 11} {:file "lib/b.rb" :start 2 :end 11}
                             {:file "lib/c.rb" :start 2 :end 11}]}
          {:mass 34 :sides [{:file "lib/d.rb" :start 2 :end 4} {:file "lib/d.rb" :start 5 :end 7}]}]
         (flay/parse-clones (json/parse-string (slurp (str (fs/path fixtures "flay-report.json"))))))))
