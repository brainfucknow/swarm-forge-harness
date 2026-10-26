(ns sfjs.package-test
  (:require [clojure.test :refer [deftest is testing]]
            [sfjs.package :as pkg]))

(defn- message-of [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-message e))))

(deftest manager-from-the-field
  (is (= "pnpm" (pkg/detect-manager {"packageManager" "pnpm@9.1.0"} ["pnpm-lock.yaml"])))
  (is (= "yarn" (pkg/detect-manager {"packageManager" "yarn@4.5.0+sha512.abc"} ["yarn.lock" "package-lock.json"])))
  (is (= "bun" (pkg/detect-manager {"packageManager" "bun@1.1.30"} ["bun.lockb"])))
  (is (= "unknown packageManager 'deno@2.0.0'"
         (message-of #(pkg/detect-manager {"packageManager" "deno@2.0.0"} ["package-lock.json"]))))
  (is (= "packageManager names pnpm but the lockfile is yarn.lock"
         (message-of #(pkg/detect-manager {"packageManager" "pnpm@9.1.0"} ["yarn.lock"]))))
  (is (re-find #"^no lockfile" (message-of #(pkg/detect-manager {"packageManager" "pnpm@9.1.0"} [])))))

(deftest manager-from-the-lockfile
  (is (= "npm" (pkg/detect-manager {} ["npm-shrinkwrap.json"])))
  (is (= "npm" (pkg/detect-manager {} ["package-lock.json" "npm-shrinkwrap.json"])))
  (is (= "pnpm" (pkg/detect-manager {} ["pnpm-lock.yaml"])))
  (is (= "bun" (pkg/detect-manager {} ["bun.lock"])))
  (is (re-find #"^no lockfile" (message-of #(pkg/detect-manager {} []))))
  (is (= "lockfiles for several package managers: bun.lockb, pnpm-lock.yaml"
         (message-of #(pkg/detect-manager {} ["pnpm-lock.yaml" "bun.lockb"])))))

(deftest manager-commands
  (is (= ["npx" "--no-install" "vitest"] (pkg/exec-command "npm" ["vitest"])))
  (is (= ["pnpm" "exec" "eslint"] (pkg/exec-command "pnpm" ["eslint"])))
  (is (= ["yarn" "stryker"] (pkg/exec-command "yarn" ["stryker"])))
  (is (= ["bunx" "tsc"] (pkg/exec-command "bun" ["tsc"])))
  (is (= ["yarn" "add" "--dev" "a" "b"] (pkg/add-command "yarn" ["a" "b"])))
  (is (= ["npm" "install" "--save-dev" "a"] (pkg/add-command "npm" ["a"])))
  (is (= ["node" "npx" "pnpm"] (pkg/required-commands "pnpm")))
  (is (= ["node" "npx" "bun" "bunx"] (pkg/required-commands "bun"))))

(deftest runner-choice
  (is (= "vitest" (pkg/runner {"devDependencies" {"vitest" "^3"}})))
  (is (= "vitest" (pkg/runner {"dependencies" {"vitest" "^3"} "devDependencies" {"jest" "^30"}})))
  (is (= "jest" (pkg/runner {"dependencies" {"jest" "^30"}})))
  (is (nil? (pkg/runner {"devDependencies" {"mocha" "^10"}})))
  (is (= "no test runner (vitest or jest) in package.json"
         (message-of #(pkg/require-runner {"devDependencies" {"mocha" "^10"}})))))

(deftest tool-packages
  (testing "vitest is the default runner"
    (is (= ["@stryker-mutator/core" "@stryker-mutator/vitest-runner" "@vitest/coverage-v8" "eslint"
            "fast-check" "typescript" "typescript-eslint" "vitest"]
           (pkg/missing-packages {} false))))
  (testing "jest, with a tsconfig.json, keeping what is listed"
    (is (= ["@stryker-mutator/core" "@stryker-mutator/jest-runner" "@stryker-mutator/typescript-checker"
            "fast-check" "typescript-eslint"]
           (pkg/missing-packages {"devDependencies" {"jest" "1" "typescript" "1" "eslint" "1"}} true))))
  (testing "dependencies count as listed"
    (is (not-any? #{"vitest" "fast-check"}
                  (pkg/missing-packages {"dependencies" {"vitest" "1" "fast-check" "1"}} false)))))
