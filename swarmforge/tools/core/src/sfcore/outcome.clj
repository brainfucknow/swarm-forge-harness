(ns sfcore.outcome
  "What an sf-* command ends with: an exit status, a usage error (status 2),
  or a report of lines to print with its verdict.")

(def exit-ok 0)
(def exit-findings 1)
(def exit-usage 2)

(defn usage-error [message]
  (ex-info message {:usage true}))

(defn report
  "Lines to print, then exit 1 when there are `findings?`, else 0."
  [lines findings?]
  {:lines (vec lines) :status (if findings? exit-findings exit-ok)})
