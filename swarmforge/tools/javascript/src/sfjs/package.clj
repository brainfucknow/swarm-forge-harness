(ns sfjs.package
  "package.json facts: the package manager, the test runner, and the tool
  packages sf-js needs."
  (:require [clojure.string :as str]
            [sfcore.outcome :as outcome]))

(def lockfile-managers
  {"package-lock.json" "npm"
   "npm-shrinkwrap.json" "npm"
   "pnpm-lock.yaml" "pnpm"
   "yarn.lock" "yarn"
   "bun.lock" "bun"
   "bun.lockb" "bun"})

(def lockfiles (sort (keys lockfile-managers)))

(def ^:private managers (set (vals lockfile-managers)))

(defn- lockfiles-of [manager]
  (sort (keep (fn [[file owner]] (when (= owner manager) file)) lockfile-managers)))

(defn- declared-manager [package]
  (when-let [declared (get package "packageManager")]
    (let [name (first (str/split declared #"@" 2))]
      (when-not (managers name)
        (throw (outcome/usage-error (str "unknown packageManager '" declared "'"))))
      name)))

(defn- no-lockfile [expected]
  (outcome/usage-error (str "no lockfile (" (str/join ", " expected) "); install the dependencies first")))

(defn detect-manager
  "The package manager: packageManager's name when declared, which its own
  lockfile must back; else the one manager whose lockfiles are present."
  [package present]
  (let [present (sort (filter lockfile-managers present))
        found (distinct (map lockfile-managers present))]
    (if-let [declared (declared-manager package)]
      (cond
        (some #{declared} found) declared
        (empty? present) (throw (no-lockfile (lockfiles-of declared)))
        :else (throw (outcome/usage-error (str "packageManager names " declared " but the lockfile is "
                                             (str/join ", " present)))))
      (case (count found)
        0 (throw (no-lockfile lockfiles))
        1 (first found)
        (throw (outcome/usage-error (str "lockfiles for several package managers: " (str/join ", " present))))))))

(def ^:private exec-prefix
  {"npm" ["npx" "--no-install"] "pnpm" ["pnpm" "exec"] "yarn" ["yarn"] "bun" ["bunx"]})

(def ^:private add-prefix
  {"npm" ["npm" "install" "--save-dev"] "pnpm" ["pnpm" "add" "--save-dev"]
   "yarn" ["yarn" "add" "--dev"] "bun" ["bun" "add" "--dev"]})

(defn exec-command
  "Runs a project-installed tool through the package manager."
  [manager tool-args]
  (into (exec-prefix manager) tool-args))

(defn add-command [manager packages]
  (into (add-prefix manager) packages))

(defn required-commands [manager]
  (distinct (concat ["node" "npx" manager] (take 1 (exec-prefix manager)))))

(defn- listed? [package name]
  (some #(contains? (get package %) name) ["dependencies" "devDependencies"]))

(defn- first-listed [package names]
  (some #(when (listed? package %) %) names))

(defn- required [runner]
  (or runner (throw (outcome/usage-error "no test runner (vitest or jest) in package.json"))))

(def ^:private test-runners
  "Preference for test, property and coverage: vitest, else jest."
  ["vitest" "jest"])

(def ^:private stryker-runners
  "Preference for mutate and tools: jest, else vitest."
  ["jest" "vitest"])

(defn runner [package]
  (first-listed package test-runners))

(defn require-runner [package]
  (required (runner package)))

(defn stryker-runner [package]
  (first-listed package stryker-runners))

(defn require-stryker-runner [package]
  (required (stryker-runner package)))

(defn complexity-linter
  "The linter sf-js crap takes complexity from: oxlint when listed, else
  eslint when listed, else oxlint."
  [package]
  (or (first-listed package ["oxlint" "eslint"]) "oxlint"))

(def ^:private linter-packages
  {"oxlint" ["oxlint"] "eslint" ["eslint" "typescript-eslint"]})

(defn tool-packages
  "Every package the sf-js commands run, for the project's runner (vitest
  when none is listed) and complexity linter."
  [package tsconfig?]
  (let [runner (or (stryker-runner package) "vitest")]
    (cond-> (into [runner "typescript" "fast-check" "@stryker-mutator/core" (str "@stryker-mutator/" runner "-runner")]
                  (linter-packages (complexity-linter package)))
      (= runner "vitest") (conj "@vitest/coverage-v8")
      tsconfig? (conj "@stryker-mutator/typescript-checker"))))

(defn missing-packages [package tsconfig?]
  (vec (sort (remove #(listed? package %) (tool-packages package tsconfig?)))))
