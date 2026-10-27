(ns sfcore.crap
  "crap4j scoring, the limit, and the report every sf-* crap command prints."
  (:require [sfcore.outcome :as outcome]))

(def crap-limit 10)

(defn crap-score
  "crap4j: comp² × (1 − coverage)³ + comp, with coverage as a fraction."
  [complexity coverage]
  (+ (* complexity complexity (Math/pow (- 1.0 coverage) 3.0)) complexity))

(defn over? [{:keys [crap]}]
  (> crap crap-limit))

(defn fmt [pattern & args]
  (String/format java.util.Locale/ROOT pattern (to-array args)))

(defn- whole [n]
  (if (== n (Math/floor n)) (str (long n)) (str n)))

(defn format-row
  "One scored unit: verdict, CRAP, complexity, coverage, file:line and name."
  [{:keys [crap complexity coverage file line name] :as unit}]
  (let [cov (fmt "%4s" (str (Math/round (* coverage 100.0)) "%"))]
    (fmt "%s crap %6.1f  cc %3s  cov %s  %s:%s %s"
         (if (over? unit) "OVER" "ok  ") (double crap) (whole complexity) cov file line name)))

(defn sort-worst-first [units]
  (sort-by (juxt (comp - :crap) :file :line) units))

(defn format-summary
  "`noun` names the unit, such as \"method\"; `changed?` says whether the list
  is limited to changed units."
  [units noun changed?]
  (str (count units) " " (when changed? "changed ") noun "(s), "
       (count (filter over? units)) " over CRAP " crap-limit))

(defn review
  "Each unit as `format-unit` renders it, then the summary; findings when any
  unit is over the limit. `units` arrive in the order to print."
  [units format-unit noun changed?]
  (outcome/report (conj (mapv format-unit units) (format-summary units noun changed?))
                  (some over? units)))
