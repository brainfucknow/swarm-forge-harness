#!/usr/bin/env bash
# A fake JavaScript tool for the sf-js acceptance tests, installed under every
# tool name. It writes the report the real tool would, exits with the status
# the scenario chose, and reads both from the directory FAKE_JS_STATE names.
state="${FAKE_JS_STATE:?FAKE_JS_STATE is not set}"
name="${0##*/}"
case "$name" in
  npx) while [ $# -gt 0 ] && [ "${1#-}" != "$1" ]; do shift; done ;;
  pnpm) if [ "${1:-}" = exec ]; then shift; fi ;;
esac
tool="${1:-$name}"
case "$name:$tool" in
  npm:*|bun:*|node:*|pnpm:add|yarn:add) tool="$name" ;;
esac
tool="${tool%%@*}"

value_of() {
  local key="$1" previous="" arg
  shift
  for arg in "$@"; do
    case "$arg" in "$key="*) echo "${arg#*=}"; return ;; esac
    if [ "$previous" = "$key" ]; then echo "$arg"; return; fi
    previous="$arg"
  done
}

has() {
  local wanted="$1" arg
  shift
  for arg in "$@"; do [ "$arg" = "$wanted" ] && return 0; done
  return 1
}

report() {
  mkdir -p "${1%/*}"
  printf '%s\n' "$2" > "$1"
}

case "$tool" in
  vitest)
    if has --coverage.enabled "$@" && [ ! -e "$state/no-coverage-vitest" ]; then
      report "$(value_of --coverage.reportsDirectory "$@")/coverage-final.json" '{}'
    fi ;;
  jest)
    if has --coverage "$@" && [ ! -e "$state/no-coverage-jest" ]; then
      report "$(value_of --coverageDirectory "$@")/coverage-final.json" '{}'
    fi ;;
  eslint)
    messages='[]'
    if [ -f "$state/eslint.json" ]; then messages="$(< "$state/eslint.json")"; fi
    report "$(value_of --output-file "$@")" "$messages" ;;
  jscpd)
    if ! has --version "$@"; then
      report "$(value_of --output "$@")/jscpd-report.json" '{"duplicates":[]}'
    fi ;;
  stryker)
    if [ -e "$state/no-report-stryker" ]; then
      :
    elif [ -f "$state/stryker.json" ]; then
      report tmp/stryker/mutation.json "$(< "$state/stryker.json")"
    else
      report tmp/stryker/mutation.json '{"schemaVersion":"2","thresholds":{"high":80,"low":60},"files":{}}'
    fi ;;
esac

status=0
if [ -f "$state/status-$tool" ]; then read -r status < "$state/status-$tool"; fi
exit "$status"
