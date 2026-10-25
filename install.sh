#!/usr/bin/env bash
# Install the SwarmForge harness pack into a project.
#
#   install.sh [pack] [target-dir]
#
# pack        six-pack (default), four-pack, or two-pack
# target-dir  the project to install into (default: current directory)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
pack="${1:-six-pack}"
target="$(cd "${2:-.}" && pwd)"

[ -f "$HERE/swarmforge/packs/$pack.conf" ] || { echo "unknown pack '$pack'" >&2; exit 1; }
[ -d "$target/.git" ] || { echo "$target is not a git repository" >&2; exit 1; }

copy_tree() {
  local src="$1" dst="$2"
  mkdir -p "$dst"
  cp -R "$src/." "$dst/"
}

copy_tree "$HERE/swarmforge" "$target/swarmforge"
copy_tree "$HERE/.claude/skills" "$target/.claude/skills"
copy_tree "$HERE/.agents/skills" "$target/.agents/skills"
cp "$HERE/swarmforge/packs/$pack.conf" "$target/swarmforge/roles.conf"

append_once() {
  local file="$1" line="$2"
  touch "$file"
  grep -qxF "$line" "$file" && return 0
  [ ! -s "$file" ] || [ "$(tail -c 1 "$file")" = "" ] || echo >> "$file"
  echo "$line" >> "$file"
}

append_once "$target/AGENTS.md" "Follow swarmforge/README.md and swarmforge/roles/orchestrator.md when asked to swarm a feature; delegate subagent roles by name and keep all task bookkeeping in swarmforge/bin/sf."
append_once "$target/CLAUDE.md" "@AGENTS.md"
mkdir -p "$target/.github"
append_once "$target/.github/copilot-instructions.md" "Follow AGENTS.md at the repository root."
append_once "$target/.gitignore" ".worktrees/"
append_once "$target/.gitignore" "tmp/"

(cd "$target" && ./swarmforge/bin/sf adapters >/dev/null && ./swarmforge/bin/sf init)

cat <<EOF
Installed $pack into $target.
  swarmforge/roles.conf       pipeline (edit, then: swarmforge/bin/sf adapters)
  swarmforge/tiers.conf       model per tier per harness
  .claude/agents, .codex/agents, .github/agents   generated role agents
  /swarm <request>            start a task from Claude Code, Codex, or Copilot
EOF
