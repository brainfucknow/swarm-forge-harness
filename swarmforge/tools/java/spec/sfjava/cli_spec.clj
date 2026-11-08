(ns sfjava.cli-spec
  (:require [speclj.core :refer :all]
            [sfcore.shell :as shell]
            [sfjava.cli :as cli]
            [sfjava.quality :as quality]
            [sfjava.runner :as runner]))

(describe "Java command dispatch"
  (it "returns the selected operation's result without changing its options"
    (with-redefs [runner/tests #(if (= "property" %) 7 6)
                  runner/coverage (constantly 5)
                  quality/crap #(if (= "topic" (:base %)) 4 0)
                  quality/dry (constantly 3)
                  quality/mutate (constantly 2)]
      (should= 6 (cli/main ["test"]))
      (should= 7 (cli/main ["property"]))
      (should= 5 (cli/main ["coverage"]))
      (should= 4 (cli/main ["crap" "--base" "topic"]))
      (should= 3 (cli/main ["dry"]))
      (should= 2 (cli/main ["mutate"]))
      (should= 0 (binding [*out* (java.io.StringWriter.)] (cli/main ["help"])))))
  (it "requires every installed tool before preparing the runner runtime"
    (let [calls (atom [])]
      (with-redefs [runner/validate-project (constantly nil)
                    shell/run (fn [_ args] (swap! calls conj args) 0)
                    runner/install-runtime #(swap! calls conj :runtime)]
        (should= 0 (cli/main ["tools"]))
        (should= [["tool" "ensure" "crap4java"] ["tool" "require" "crap4java"]
                  ["tool" "ensure" "dry4java"] ["tool" "require" "dry4java"]
                  ["tool" "ensure" "mutate4java"] ["tool" "require" "mutate4java"] :runtime]
                 @calls))))
  (it "stops installation at the first failed tool"
    (with-redefs [runner/validate-project (constantly nil)
                  shell/run (constantly 1)]
      (should-throw Exception "cannot install crap4java" (cli/main ["tools"])))))
