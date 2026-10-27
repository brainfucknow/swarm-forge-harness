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

copy_tree() {
  local src="$1" dst="$2"
  mkdir -p "$dst"
  cp -R "$src/." "$dst/"
}

copy_tree "$HERE/swarmforge" "$target/swarmforge"
copy_tree "$HERE/.claude/skills" "$target/.claude/skills"
copy_tree "$HERE/.agents/skills" "$target/.agents/skills"

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
append_once "$target/.gitignore" ".swarmforge/"
append_once "$target/.gitignore" "tmp/"
append_once "$target/.gitignore" ".metrics/"

(cd "$target" && ./swarmforge/bin/sf adapters >/dev/null && ./swarmforge/bin/sf init)

cat <<EOF
Installed SwarmForge into $target.
  swarmforge/roles.conf       role catalogue (edit, then: swarmforge/bin/sf adapters)
  swarmforge/routes.conf      named routes a task can take
  swarmforge/tiers.conf       model per tier per harness
  .claude/agents, .codex/agents, .github/agents   generated role agents
  /swarm <request>            start a task from Claude Code, Codex, or Copilot
EOF
