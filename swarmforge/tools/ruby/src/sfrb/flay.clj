(ns sfrb.flay
  "The report sf-rb's flay reporter writes, read into the clones the shared
  jscpd review prints.")

(defn parse-clones
  "Each clone's mass and every side's file and line range."
  [report]
  (mapv (fn [clone]
          {:mass (get clone "mass")
           :sides (mapv (fn [side] {:file (get side "file") :start (get side "line") :end (get side "line_max")})
                        (get clone "sides"))})
        (get report "clones")))
