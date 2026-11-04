(ns sfcore.crap
  "crap4j scoring, the limit, and the report every sf-* crap command prints."
  (:require [sfcore.changes :as changes]
            [sfcore.outcome :as outcome]))

(def crap-limit
  "The CRAP limit, unless a wrapper sets one for its complexity source."
  10)

(defn crap-score
  "crap4j: comp² × (1 − coverage)³ + comp, with coverage as a fraction."
  [complexity coverage]
  (+ (* complexity complexity (Math/pow (- 1.0 coverage) 3.0)) complexity))

(defn over? [limit {:keys [crap]}]
  (> crap limit))

(defn fmt [pattern & args]
  (String/format java.util.Locale/ROOT pattern (to-array args)))

(defn- whole [n]
  (if (== n (Math/floor n)) (str (long n)) (str n)))

(defn format-row
  "One scored unit: verdict against `limit`, CRAP, complexity, coverage,
  file:line and name."
  [limit {:keys [crap complexity coverage file line name] :as unit}]
  (let [cov (fmt "%4s" (str (Math/round (* coverage 100.0)) "%"))]
    (fmt "%s crap %6.1f  cc %3s  cov %s  %s:%s %s"
         (if (over? limit unit) "OVER" "ok  ") (double crap) (whole complexity) cov file line name)))

(defn sort-worst-first [units]
  (sort-by (juxt (comp - :crap) :file :line) units))

(defn select
  "The scored units whose :line to :last-line span touches a changed line
  (every one when `changed` is nil), worst first."
  [units changed]
  (->> units
       (filter #(or (nil? changed) (changes/overlaps? (:file %) (:line %) (:last-line %) changed)))
       sort-worst-first))

(defn format-summary
  "`noun` names the unit, such as \"method\"; `changed?` says whether the list
  is limited to changed units."
  [limit units noun changed?]
  (str (count units) " " (when changed? "changed ") noun "(s), "
       (count (filter (partial over? limit) units)) " over CRAP " limit))

(defn review
  "Each unit as `(format-unit limit unit)` renders it, then the summary;
  findings when any unit is over `limit`. `units` arrive in the order to print."
  [limit units format-unit noun changed?]
  (outcome/report (conj (mapv (partial format-unit limit) units) (format-summary limit units noun changed?))
                  (some (partial over? limit) units)))
