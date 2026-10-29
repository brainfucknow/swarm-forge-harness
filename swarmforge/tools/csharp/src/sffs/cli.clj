(ns sffs.cli
  (:require [sfcs.cli :as dotnet]
            [sfcore.shell :as shell]))

(defn -main [& argv]
  (shell/run-main "sf-fs" #(dotnet/main :fsharp %) argv))
