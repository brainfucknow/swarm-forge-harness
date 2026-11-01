(ns sfvb.cli
  (:require [sfcs.cli :as dotnet]
            [sfcore.shell :as shell]))

(defn -main [& argv]
  (shell/run-main "sf-vb" #(dotnet/main :vbnet %) argv))
