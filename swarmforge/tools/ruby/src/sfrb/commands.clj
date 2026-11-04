(ns sfrb.commands
  "Where sf-rb keeps its reports, which files are Ruby sources and specs, and
  the command lines of the tools it runs."
  (:require [clojure.string :as str]))

(def coverage-dir "tmp/coverage")
(def coverage-file "coverage.json")
(def coverage-report (str coverage-dir "/" coverage-file))
(def jscpd-dir "tmp/jscpd")
(def jscpd-report (str jscpd-dir "/jscpd-report.json"))
(def flay-dir "tmp/flay")
(def flay-report (str flay-dir "/flay-report.json"))

(def source-pathspecs
  "git pathspecs for the changed-file scope."
  ["*.rb"])

(def property-pattern "**/*_property_spec.rb")

(def ^:private ignored-dirs ["node_modules" "dist" "build" "coverage" "tmp" "vendor"])

(defn spec-file? [path]
  (boolean (or (str/ends-with? path "_spec.rb") (re-find #"(^|/)spec/" path))))

(defn- ignored-path? [path]
  (boolean (some (set ignored-dirs) (butlast (str/split path #"/")))))

(defn ruby-sources
  "The `.rb` files among `paths` outside the ignored directories, sorted."
  [paths]
  (vec (sort (filter #(and (str/ends-with? % ".rb") (not (ignored-path? %))) paths))))

(defn production-sources
  "The Ruby sources among `paths` that are not specs."
  [paths]
  (vec (remove spec-file? (ruby-sources paths))))

(defn rspec-args
  "`bundle` arguments running rspec without the property specs."
  [extra]
  (into ["exec" "rspec" "--exclude-pattern" property-pattern] extra))

(defn coverage-env
  "The environment that makes rspec load `setup`, sf-rb's SimpleCov start,
  after bundler/setup: bundle exec must exec rspec rather than load it into
  its own process, where RUBYOPT has already been read."
  [rubyopt setup]
  {"RUBYOPT" (str/join " " (remove str/blank? [rubyopt (str "-r" setup)]))
   "BUNDLE_DISABLE_EXEC_LOAD" "true"})

(def ^:private jscpd ["npx" "--yes" "jscpd@4"])

(defn jscpd-args [min-tokens]
  (into jscpd ["--format" "ruby" "--min-tokens" (str min-tokens) "--reporters" "json" "--output" jscpd-dir
               "--ignore" (str/join "," (map #(str "**/" % "/**") ignored-dirs)) "."]))

(defn flay-args
  "`bundle` arguments running sf-rb's flay reporter `script` over `files`."
  [script mass files]
  (into ["exec" "ruby" script (str mass) flay-report] files))
