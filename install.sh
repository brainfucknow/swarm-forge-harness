#!/usr/bin/env bash
# Install the SwarmForge harness into a project.
#
#   install.sh [target-dir]
#
# target-dir  the project to install into (default: current directory)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
target="$(cd "${1:-.}" && pwd)"

[ -d "$target/.git" ] || { echo "$target is not a git repository" >&2; exit 1; }

# Run from $HERE, which has no bb.edn, so the caller's bb.edn cannot affect it.
minimum=""
min_pattern=':min-bb-version "([^"]*)"'
while IFS= read -r line; do
  [[ $line =~ $min_pattern ]] && minimum="${BASH_REMATCH[1]}"
done < "$HERE/swarmforge/tools/sf/bb.edn"
needs="install.sh: SwarmForge needs Babashka $minimum or newer: https://babashka.org"
command -v bb >/dev/null || { echo "$needs" >&2; exit 1; }
if ! check_err="$(cd "$HERE" && bb "$HERE/swarmforge/bin/sf" help 2>&1 >/dev/null)"; then
  [ -z "$check_err" ] || echo "$check_err" >&2
  if echo "$check_err" | grep -q '^sf: Babashka .* is too old; sf needs '; then
    echo "$needs" >&2
  else
    echo "install.sh: the Babashka check failed" >&2
  fi
  exit 1
fi

config_sources=("$HERE/swarmforge/constitution/articles/project.md")
for file in "$HERE"/swarmforge/*.conf; do
  [ "$(basename "$file")" = verify.conf ] || config_sources+=("$file")
done

check_config_destination() {
  if [ -L "$1" ] || { [ -e "$1" ] && [ ! -f "$1" ]; }; then
    echo "install.sh: project configuration conflict: $1 is not a regular file" >&2
    exit 1
  fi
}

for file in "${config_sources[@]}"; do
  check_config_destination "$target/${file#"$HERE/"}"
done

shipped=()
copy_file() {
  local src="$1" dst="$2" config
  shipped+=("${src#"$HERE/"}")
  for config in "${config_sources[@]}"; do
    if [ "$src" = "$config" ]; then
      check_config_destination "$dst"
      [ ! -e "$dst" ] || return 0
      break
    fi
  done
  mkdir -p "$(dirname "$dst")"
  cp -P "$src" "$dst"
}

copy_tree() {
  local src="$1" dst="$2" entry
  mkdir -p "$dst"
  for entry in "$src"/* "$src"/.[!.]* "$src"/..?*; do
    [ -e "$entry" ] || [ -L "$entry" ] || continue
    if [ -d "$entry" ] && [ ! -L "$entry" ]; then
      copy_tree "$entry" "$dst/$(basename "$entry")"
    else
      copy_file "$entry" "$dst/$(basename "$entry")"
    fi
  done
}

mkdir -p "$target/swarmforge"
# verify.conf holds this repository's own checks; a project writes its own.
for file in "$HERE"/swarmforge/*.md "$HERE"/swarmforge/*.conf; do
  case "$(basename "$file")" in
    verify.conf) ;;
    *) copy_file "$file" "$target/swarmforge/$(basename "$file")" ;;
  esac
done
for directory in bin constitution languages roles; do
  copy_tree "$HERE/swarmforge/$directory" "$target/swarmforge/$directory"
done
for module in "$HERE"/swarmforge/tools/*; do
  for directory in src resources; do
    if [ -d "$module/$directory" ]; then
      copy_tree "$module/$directory" "$target/swarmforge/tools/$(basename "$module")/$directory"
    fi
  done
done
for module in csharp javascript ruby java; do
  copy_file "$HERE/swarmforge/tools/$module/bb.edn" "$target/swarmforge/tools/$module/bb.edn"
done
copy_tree "$HERE/.claude/skills" "$target/.claude/skills"
copy_tree "$HERE/.agents/skills" "$target/.agents/skills"
copy_tree "$HERE/.kilo/command" "$target/.kilo/command"

append_once() {
  local file="$1" line="$2"
  touch "$file"
  grep -qxF "$line" "$file" && return 0
  [ ! -s "$file" ] || [ "$(tail -c 1 "$file")" = "" ] || echo >> "$file"
  echo "$line" >> "$file"
}

append_once "$target/AGENTS.md" "Follow swarmforge/README.md and swarmforge/roles/orchestrator.md when asked to swarm a feature; delegate subagent roles by name and keep all task bookkeeping in swarmforge/bin/sf."
# A project whose AGENTS.md links to CLAUDE.md (or the reverse) already loads
# the instructions; importing AGENTS.md there would import the file into itself.
[ "$target/CLAUDE.md" -ef "$target/AGENTS.md" ] || append_once "$target/CLAUDE.md" "@AGENTS.md"
mkdir -p "$target/.github"
append_once "$target/.github/copilot-instructions.md" "Follow AGENTS.md at the repository root."
append_once "$target/.gitignore" ".worktrees/"
append_once "$target/.gitignore" ".swarmforge/"
append_once "$target/.gitignore" "tmp/"
append_once "$target/.gitignore" ".metrics/"
append_once "$target/.gitignore" ".kilo/worktrees/"
append_once "$target/.gitignore" ".kilo/agent-manager.json"
# Keeps swarmforge/bin tracked when the project ignores every bin/ directory,
# as Visual Studio's "[Bb]in/" does. A file inside swarmforge/bin is never read
# under such a rule; "!/bin/**" also covers rules like "**/bin/**". The leading
# slash leaves other bin/ directories inside swarmforge/ ignored.
append_once "$target/swarmforge/.gitignore" "!/bin/"
append_once "$target/swarmforge/.gitignore" "!/bin/**"

(cd "$target" && ./swarmforge/bin/sf adapters >/dev/null && ./swarmforge/bin/sf init)

# Compare only payload destinations; unknown provenance never permits deletion.
printf '%s\0' "${shipped[@]}" | (cd "$HERE" && bb -e '
  (require (quote [babashka.fs :as fs]) (quote [clojure.string :as str]))
  (let [root (fs/path (first *command-line-args*))
        expected (set (str/split (slurp *in*) #"\u0000"))
        found (atom [])
        remember (fn [path _] (swap! found conj (str (fs/relativize root path))) :continue)
        caches #{".git" ".swarmforge" ".metrics" ".worktrees" ".cpcache" "node_modules" "tmp"}
        managed ["swarmforge/bin" "swarmforge/constitution" "swarmforge/languages"
                 "swarmforge/roles" "swarmforge/tools" ".claude/skills" ".agents/skills" ".kilo/command"]]
    (doseq [relative managed :let [directory (fs/path root relative)] :when (fs/exists? directory)]
      (fs/walk-file-tree directory
        {:pre-visit-dir (fn [path _] (if (caches (fs/file-name path)) :skip-subtree :continue))
         :visit-file remember}))
    (doseq [path (fs/list-dir (fs/path root "swarmforge")) :when (not (fs/directory? path))]
      (remember path nil))
    (doseq [path (sort (distinct @found))
            :when (and (not (expected path))
                       (not= path "swarmforge/.gitignore")
                       (not (re-matches #"swarmforge/[^/]+\.conf" path))
                       (not= path "swarmforge/constitution/articles/project.md"))]
      (println "UNSHIPPED (manual review; may be a user file):" path)))
  ' "$target")

cat <<EOF
Installed SwarmForge into $target.
  swarmforge/roles.conf       role catalogue (edit, then: swarmforge/bin/sf adapters)
  swarmforge/routes.conf      named routes a task can take
  swarmforge/tiers.conf       model per tier per harness
  .claude/agents, .codex/agents, .github/agents, .kilo/agent, .vibe/agents   generated role agents
  /swarm <request>            start a task from Kilo, Claude Code, Codex, Copilot, or Vibe
EOF
