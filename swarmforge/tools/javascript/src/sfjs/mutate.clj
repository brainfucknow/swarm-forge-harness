(ns sfjs.mutate
  "The StrykerJS command line and the config file sf-js runs it with."
  (:require [clojure.string :as str]
            [sfjs.commands :as commands]))

(def config-path (str commands/stryker-dir "/stryker.config.mjs"))
(def report-path (str commands/stryker-dir "/mutation.json"))
(def incremental-path (str commands/stryker-dir "/incremental.json"))
(def default-concurrency "4")

(defn- sets-concurrency? [extra]
  (some #(or (#{"--concurrency" "-c"} %) (str/starts-with? % "--concurrency=")) extra))

(defn stryker-args
  "`files` are the files to mutate, or nil to leave them to Stryker's own
  configuration; `extra` are the caller's arguments after --."
  [{:keys [runner tsconfig? files extra]}]
  (cond-> ["stryker" "run" config-path "--testRunner" runner "--reporters" "json,progress"
           "--incremental" "--incrementalFile" incremental-path]
    (not (sets-concurrency? extra)) (into ["--concurrency" default-concurrency])
    tsconfig? (into ["--checkers" "typescript"])
    (seq files) (into ["--mutate" (str/join "," files)])
    :always (into extra)))

(def ^:private project-configs
  (for [base ["stryker.conf" "stryker.config" ".stryker.conf" ".stryker.config"]
        extension [".json" ".js" ".mjs" ".cjs"]]
    (str base extension)))

(def ^:private linked-plugins
  "Stryker finds its plugins next to its own real path. A linked
  node_modules (pnpm) keeps the runner and checker plugins elsewhere, so the
  config appends the project's copies by resolved URL."
  (str "const plugins = \"node_modules/@stryker-mutator\";\n"
       "const linked = existsSync(resolve(plugins, \"core\"))\n"
       "  && realpathSync(resolve(plugins, \"core\")) !== resolve(plugins, \"core\");\n"
       "const linkedPlugins = !linked ? []\n"
       "  : readdirSync(plugins).filter((name) => /-(runner|checker)$/.test(name))\n"
       "    .map((name) => import.meta.resolve(`@stryker-mutator/${name}`));\n\n"))

(defn stryker-config
  "A Stryker config that loads the project's own config file, when there is
  one, appends the project's linked Stryker plugins, and sends the JSON report
  to tmp/stryker/mutation.json."
  []
  (str "import { existsSync, readdirSync, readFileSync, realpathSync } from \"node:fs\";\n"
       "import { resolve } from \"node:path\";\n"
       "import { pathToFileURL } from \"node:url\";\n\n"
       "const names = [" (str/join ", " (map pr-str project-configs)) "];\n"
       "const found = names.find((name) => existsSync(name));\n"
       "const project = !found ? {}\n"
       "  : found.endsWith(\".json\") ? JSON.parse(readFileSync(found, \"utf8\"))\n"
       "  : (await import(pathToFileURL(resolve(found)).href)).default;\n\n"
       linked-plugins
       "export default {\n"
       "  ...project,\n"
       "  appendPlugins: [...(project.appendPlugins ?? []), ...linkedPlugins],\n"
       "  jsonReporter: { ...project.jsonReporter, fileName: " (pr-str report-path) " },\n"
       "};\n"))
