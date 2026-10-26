#!/usr/bin/env bash
# Executable form of swarmforge/tools/javascript/qa/sf-js.md.
#
# Drives swarmforge/bin/sf-js from the command line against real JavaScript and
# TypeScript projects with the real Vitest, Jest, tsc, ESLint, jscpd and StrykerJS.
# It checks only what a user sees: exit status, stdout, stderr, the `$ ` lines and
# files under the project. Run it from the task worktree root:
#
#   bash swarmforge/tools/javascript/qa/sf-js-qa.sh
#
# Needs git, bb, node, npm, pnpm and the npm registry; yarn and bun are optional.
# Everything is built under ./tmp/qa/. The script exits non-zero when a check fails.
# QA-12.3 is a by-hand documentation check and is not scripted.
set -uo pipefail

ROOT="$(pwd)"
SFJS="$ROOT/swarmforge/bin/sf-js"
QA="$ROOT/tmp/qa"
[ -x "$SFJS" ] || { echo "run from the task worktree root: no swarmforge/bin/sf-js" >&2; exit 2; }
for tool in git bb node npm pnpm; do
  command -v "$tool" >/dev/null || { echo "missing prerequisite on PATH: $tool" >&2; exit 2; }
done

export GIT_AUTHOR_NAME=qa GIT_AUTHOR_EMAIL=qa@example.com GIT_COMMITTER_NAME=qa GIT_COMMITTER_EMAIL=qa@example.com
# Jest and Vitest print only a summary when they detect an AI agent; QA checks the
# output a person at the terminal gets.
unset AI_AGENT CLAUDECODE
export npm_config_fund=false npm_config_audit=false npm_config_update_notifier=false CI=true

passed=0 failed=0 skipped=0
pass() { echo "ok   $1"; passed=$((passed + 1)); }
fail() { echo "FAIL $1" >&2; failed=$((failed + 1)); }
skip() { echo "SKIP $1"; skipped=$((skipped + 1)); }
section() { echo; echo "== $1"; }

# run CMD...: runs a command, keeping OUT, ERR and RC for the checks below.
run() {
  "$@" >"$QA/.out" 2>"$QA/.err"
  RC=$?
  OUT="$(cat "$QA/.out")"
  ERR="$(cat "$QA/.err")"
}
show_run() { printf '%s\n--- stderr\n%s\n' "$OUT" "$ERR" | tail -n 25 | sed 's/^/     | /' >&2; }
check() { if eval "$2"; then pass "$1"; else fail "$1"; show_run; fi; }
rc_is() { [ "$RC" -eq "$1" ]; }
out_has() { grep -qF -- "$1" <<<"$OUT"; }
out_lacks() { ! grep -qF -- "$1" <<<"$OUT"; }
out_matches() { grep -qE -- "$1" <<<"$OUT"; }
err_has() { grep -qF -- "$1" <<<"$ERR"; }
cmd_line() { grep -E "^\\\$ $1" <<<"$OUT" | head -n 1; }
no_cmd_lines() { ! grep -qE '^\$ ' <<<"$OUT"; }
last_line() { grep -v '^[[:space:]]*$' <<<"$OUT" | tail -n 1; }
line_has_all() { local line="$1" part; shift; for part in "$@"; do grep -qF -- "$part" <<<"$line" || return 1; done; }
dry_lines() { grep -E '^[0-9]+ tokens  |clone\(s\) touch changed lines$' <<<"$OUT"; }
line_no() { grep -nE -- "$1" <<<"$OUT" | head -n 1 | cut -d: -f1; }
dev_deps() { node -e 'const p=require(process.argv[1]);console.log(Object.keys(p.devDependencies||{}).sort().join(" "))' "$PWD/package.json"; }
has_dev_dep() { grep -qw -- "$1" <<<" $(dev_deps) "; }
set_pm() { node -e 'const fs=require("fs");const f=process.argv[1];const p=JSON.parse(fs.readFileSync(f));if(process.argv[2])p.packageManager=process.argv[2];else delete p.packageManager;fs.writeFileSync(f,JSON.stringify(p,null,2)+"\n")' "$PWD/package.json" "${1:-}"; }
commit() { git add -A && git commit -q -m "$1"; }
quiet() { "$@" >"$QA/.setup" 2>&1 || { echo "setup failed: $*" >&2; tail -n 30 "$QA/.setup" >&2; exit 2; }; }

new_repo() {
  rm -rf "$1" && mkdir -p "$1" && cd "$1" && git init -q -b main .
  printf 'node_modules/\ntmp/\n.stryker-tmp/\n' >.gitignore
}

legacy_block() {
  cat <<'EOF'
export function summarize(entries: { amount: number; kind: string }[]): string {
  let credits = 0;
  let debits = 0;
  let largest = 0;
  for (const entry of entries) {
    if (entry.kind === "credit") {
      credits += entry.amount;
    } else {
      debits += entry.amount;
    }
    if (entry.amount > largest) {
      largest = entry.amount;
    }
  }
  const balance = credits - debits;
  const label = balance >= 0 ? "surplus" : "deficit";
  const lines = [`credits ${credits}`, `debits ${debits}`, `largest ${largest}`];
  lines.push(`${label} ${Math.abs(balance)}`);
  return lines.join("\n");
}
EOF
}

build_p1() {
  new_repo "$QA/vitest-ts"
  cat >package.json <<'EOF'
{
  "name": "vitest-ts",
  "private": true,
  "type": "module"
}
EOF
  quiet npm install --save-dev vitest
  cat >tsconfig.json <<'EOF'
{
  "compilerOptions": {
    "strict": true,
    "noEmit": true,
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "skipLibCheck": true
  },
  "include": ["src"]
}
EOF
  mkdir -p src
  cat >src/ledger.ts <<'EOF'
export function deposit(total: number, amount: number): number {
  return total + amount;
}

export function withdraw(total: number, amount: number, overdraft: boolean): number {
  if (amount > total && !overdraft) {
    throw new Error("overdraft not allowed");
  }
  if (amount <= 0) {
    throw new Error("amount must be positive");
  }
  return total - amount;
}
EOF
  cat >src/ledger.test.ts <<'EOF'
import { describe, expect, it } from "vitest";
import { deposit, withdraw } from "./ledger";

describe("ledger", () => {
  it("deposits", () => {
    expect(deposit(10, 5)).toBe(15);
  });

  it("withdraws within the balance", () => {
    expect(withdraw(100, 30, false)).toBe(70);
  });
});
EOF
  cat >src/ledger.property.test.ts <<'EOF'
import { expect, it } from "vitest";
import fc from "fast-check";
import { deposit } from "./ledger";

it("PROPERTY-ONLY", () => {
  fc.assert(fc.property(fc.integer(), fc.integer(), (total, amount) => {
    expect(deposit(total, amount)).toBe(total + amount);
  }));
});
EOF
  legacy_block >src/legacy.ts
  echo 'throw new Error("project ESLint config was loaded");' >eslint.config.js
  commit "P1 base"
  git switch -q -c swarm/qa
}

build_p2() {
  local pnpm_version
  pnpm_version="$(cd "$QA" && pnpm --version)"
  new_repo "$QA/jest-js"
  cat >package.json <<EOF
{
  "name": "jest-js",
  "private": true,
  "packageManager": "pnpm@$pnpm_version"
}
EOF
  # pnpm takes the latest typescript, which may be newer than typescript-eslint
  # accepts; keeping it in range is the project's job, not sf-js tools'.
  local typescript_range
  typescript_range="$(cd "$QA" && npm view typescript-eslint peerDependencies.typescript)"
  quiet pnpm add --save-dev jest "typescript@$typescript_range"
  mkdir -p src
  cat >src/cart.js <<'EOF'
function total(items) {
  const sum = items.reduce((acc, item) => acc + item.price, 0);
  if (sum > 100) {
    return sum - 10;
  }
  return sum;
}

module.exports = { total };
EOF
  cat >src/cart.test.js <<'EOF'
const { total } = require("./cart");

test("adds prices", () => {
  expect(total([{ price: 2 }, { price: 3 }])).toBe(5);
});

test("discounts large carts", () => {
  expect(total([{ price: 60 }, { price: 60 }])).toBe(110);
});
EOF
  cat >src/cart.property.test.js <<'EOF'
const fc = require("fast-check");
const { total } = require("./cart");

test("CART-PROPERTY-ONLY", () => {
  fc.assert(fc.property(fc.array(fc.nat(50)), (prices) => {
    expect(total(prices.map((price) => ({ price })))).toBeGreaterThanOrEqual(0);
  }));
});
EOF
  commit "P2 base"
  git switch -q -c swarm/qa
}

rm -rf "$QA" && mkdir -p "$QA"
echo "building fixture projects under $QA"
build_p1
build_p2
cp -R "$QA/vitest-ts" "$QA/p1-pristine"

# ---------------------------------------------------------------- QA-1
section "QA-1 Help and usage"
cd "$QA/vitest-ts"
for form in help --help -h ""; do
  if [ -n "$form" ]; then run "$SFJS" "$form"; else run "$SFJS"; fi
  check "QA-1.1 sf-js ${form:-<no args>} exits 0 with a usage naming every command" \
    'rc_is 0 && out_has tools && out_has test && out_has property && out_has coverage && out_has crap && out_has dry && out_has mutate'
done
run "$SFJS" nope
check "QA-1.2 unknown command exits 2" "rc_is 2 && err_has \"sf-js: unknown command 'nope'\""
mkdir -p "$QA/empty" && cd "$QA/empty" && git init -q -b main .
run "$SFJS" test
check "QA-1.3 no package.json exits 2" 'rc_is 2 && err_has "sf-js: no package.json"'

# ---------------------------------------------------------------- QA-3
# Runs before QA-2: the QA-2 copy needs typescript, which `sf-js tools` installs,
# or `sf-js test` stops at `tsc --noEmit` before printing the vitest line.
section "QA-3 Tools"
cd "$QA/p1-pristine"
before="$(cat package.json)"
run env PATH="/usr/bin:/bin:$(dirname "$(command -v bb)")" "$SFJS" tools
if PATH="/usr/bin:/bin:$(dirname "$(command -v bb)")" command -v node >/dev/null; then
  skip "QA-3.4 node is in /usr/bin or /bin, so a PATH without node cannot be built"
else
  check "QA-3.4 no node on PATH exits 2 and installs nothing" \
    'rc_is 2 && err_has "missing on PATH: node" && [ "$(cat package.json)" = "$before" ]'
fi

cd "$QA/vitest-ts"
run "$SFJS" tools
install_line="$(cmd_line 'npm install --save-dev')"
packages="${install_line#\$ npm install --save-dev }"
check "QA-3.1 tools exits 0 and prints the npm install line" 'rc_is 0 && [ -n "$install_line" ]'
check "QA-3.1 missing packages are listed in sorted order" \
  '[ -n "$packages" ] && tr " " "\n" <<<"$packages" | LC_ALL=C sort -c'
check "QA-3.1 the jscpd version check is printed" 'out_has "$ npx --yes jscpd@4 --version"'
missing=""
for pkg in vitest @vitest/coverage-v8 typescript eslint typescript-eslint @stryker-mutator/core \
  @stryker-mutator/vitest-runner @stryker-mutator/typescript-checker fast-check; do
  has_dev_dep "$pkg" || missing="$missing $pkg"
done
check "QA-3.1 devDependencies hold every tool package${missing:+ (missing:$missing)}" '[ -z "$missing" ]'
run "$SFJS" tools
check "QA-3.2 a second tools run finds everything present" \
  'rc_is 0 && out_has "all JavaScript tools present" && out_lacks "npm install"'

cd "$QA/jest-js"
run "$SFJS" tools
check "QA-3.3 tools in P2 exits 0 with pnpm add" 'rc_is 0 && out_has "$ pnpm add --save-dev"'
check "QA-3.3 P2 devDependencies hold jest-runner and typescript, not vitest or the checker" \
  'has_dev_dep @stryker-mutator/jest-runner && has_dev_dep typescript && ! has_dev_dep vitest && ! has_dev_dep @stryker-mutator/typescript-checker'
# The tools edit package.json and the lockfile; record that on main so later diffs stay clean.
for p in "$QA/vitest-ts" "$QA/jest-js"; do
  cd "$p" && git switch -q main && commit "tools" && git switch -q swarm/qa && git merge -q --ff-only main
done

# ---------------------------------------------------------------- QA-2
section "QA-2 Package manager detection"
rm -rf "$QA/pm" && cp -R "$QA/vitest-ts" "$QA/pm" && cd "$QA/pm"
mv package-lock.json "$QA/package-lock.json.saved"
run "$SFJS" test
check "QA-2.1 no lockfile exits 2 before running anything" 'rc_is 2 && err_has "no lockfile" && no_cmd_lines'
mv "$QA/package-lock.json.saved" package-lock.json
set_pm pnpm@9.1.0
run "$SFJS" test
check "QA-2.2 packageManager pnpm with package-lock.json exits 2" \
  'rc_is 2 && err_has "packageManager names pnpm but the lockfile is package-lock.json"'
set_pm
: >yarn.lock
run "$SFJS" test
check "QA-2.3 lockfiles of two managers exit 2" \
  'rc_is 2 && err_has "lockfiles for several package managers: package-lock.json, yarn.lock"'
rm yarn.lock
set_pm deno@2.0.0
run "$SFJS" test
check "QA-2.4 unknown packageManager exits 2" "rc_is 2 && err_has \"unknown packageManager 'deno@2.0.0'\""
set_pm
run "$SFJS" test
check "QA-2.5 npm runs vitest through npx --no-install" '[ -n "$(cmd_line "npx --no-install vitest run")" ]'
if command -v yarn >/dev/null; then
  quiet yarn install --non-interactive
  rm package-lock.json
  set_pm "yarn@$(yarn --version)"
  run "$SFJS" test
  check "QA-2.6 yarn runs vitest through yarn" '[ -n "$(cmd_line "yarn vitest run")" ]'
else
  skip "QA-2.6 yarn is not installed"
fi
if command -v bun >/dev/null; then
  rm -f yarn.lock package-lock.json
  set_pm "bun@$(bun --version)"
  quiet bun install
  run "$SFJS" test
  check "QA-2.6 bun runs vitest through bunx" '[ -n "$(cmd_line "bunx vitest run")" ]'
else
  skip "QA-2.6 bun is not installed"
fi

# ---------------------------------------------------------------- QA-4
section "QA-4 Tests and typecheck (P1)"
cd "$QA/vitest-ts"
run "$SFJS" test
tsc_at="$(line_no '^\$ npx --no-install tsc --noEmit')"
vitest_at="$(line_no '^\$ npx --no-install vitest run')"
check "QA-4.1 test exits 0 and runs tsc before vitest" \
  'rc_is 0 && [ -n "$tsc_at" ] && [ -n "$vitest_at" ] && [ "$tsc_at" -lt "$vitest_at" ]'
check "QA-4.1 vitest runs src/ledger.test.ts and not the property test" \
  'out_has src/ledger.test.ts && out_lacks PROPERTY-ONLY && out_lacks ledger.property.test'
run "$SFJS" test --reporter=verbose
check "QA-4.2 runner arguments pass through at the end of the vitest line" \
  'rc_is 0 && cmd_line "npx --no-install vitest run" | grep -qE -- "--reporter=verbose$"'
cp src/ledger.ts "$QA/ledger.ts.saved"
echo 'const n: number = "x";' >>src/ledger.ts
run "$SFJS" test
check "QA-4.3 a type error exits 1 and skips vitest" \
  'rc_is 1 && grep -q "error TS" <<<"$OUT$ERR" && [ -z "$(cmd_line "npx --no-install vitest")" ]'
cp "$QA/ledger.ts.saved" src/ledger.ts
cp src/ledger.test.ts "$QA/ledger.test.ts.saved"
sed -i 's/toBe(15)/toBe(16)/' src/ledger.test.ts
run "$SFJS" test
check "QA-4.4 a failing assertion exits 1" 'rc_is 1'
cp "$QA/ledger.test.ts.saved" src/ledger.test.ts
# Vitest names the tests it ran only with the verbose reporter.
run "$SFJS" property --reporter=verbose
check "QA-4.5 property runs only the property test, with no typecheck" \
  'rc_is 0 && out_has PROPERTY-ONLY && out_lacks "src/ledger.test.ts" && [ -z "$(cmd_line "npx --no-install tsc")" ]'

# ---------------------------------------------------------------- QA-5
section "QA-5 Tests with Jest (P2)"
cd "$QA/jest-js"
run "$SFJS" test
check "QA-5.1 test exits 0 through pnpm exec jest, without tsc or the property test" \
  'rc_is 0 && [ -n "$(cmd_line "pnpm exec jest")" ] && grep -q "src/cart.test.js" <<<"$OUT$ERR" && ! grep -q "cart.property.test" <<<"$OUT$ERR" && [ -z "$(cmd_line ".*tsc")" ]'
run "$SFJS" property
check "QA-5.2 property runs only the property test" \
  'rc_is 0 && grep -q "src/cart.property.test.js" <<<"$OUT$ERR" && ! grep -q "src/cart.test.js" <<<"$OUT$ERR"'

# ---------------------------------------------------------------- QA-6
section "QA-6 Coverage and CRAP (P1)"
cd "$QA/vitest-ts"
run "$SFJS" coverage
check "QA-6.1 coverage exits 0 and names its report" \
  'rc_is 0 && out_has "coverage written to tmp/coverage/coverage-final.json"'
check "QA-6.1 the report is Istanbul JSON" \
  'node -e '"'"'const r=require(process.argv[1]);const v=Object.values(r);if(!v.length||!v.every(e=>e.fnMap&&e.statementMap&&e.s&&e.f))process.exit(1)'"'"' "$PWD/tmp/coverage/coverage-final.json"'
run "$SFJS" crap --base main
check "QA-6.2 no change since main: crap exits 0 despite the throwing eslint.config.js" \
  'rc_is 0 && [ "$(last_line)" = "0 changed function(s), 0 over CRAP 10" ]'

cat >src/ledger.ts <<'EOF'
export function deposit(total: number, amount: number): number {
  return total + amount;
}

export function withdraw(total: number, amount: number, overdraft: boolean): number {
  if (amount > total && !overdraft) {
    throw new Error("overdraft not allowed");
  }
  if (amount <= 0) {
    throw new Error("amount must be positive");
  }
  if (amount > 1000) {
    throw new Error("amount over limit");
  }
  if (total > 1000000) {
    throw new Error("total over limit");
  }
  if (!Number.isInteger(amount)) {
    throw new Error("amount must be whole");
  }
  if (total - amount < -100) {
    throw new Error("overdraft limit exceeded");
  }
  return total - amount;
}
EOF
commit "Grow withdraw"
run "$SFJS" coverage
run "$SFJS" crap --base main
withdraw_line="$(grep -E 'src/ledger\.ts:[0-9]+ withdraw$' <<<"$OUT")"
check "QA-6.3 untested branches put withdraw over CRAP 10" \
  'rc_is 1 && grep -qE "^OVER crap +[0-9.]+ +cc +([7-9]|[1-9][0-9]) +cov +([0-9]|[1-9][0-9])% " <<<"$withdraw_line"'
check "QA-6.3 the unchanged deposit is not listed" 'out_lacks deposit'
check "QA-6.3 summary counts one changed function over" '[ "$(last_line)" = "1 changed function(s), 1 over CRAP 10" ]'

cat >src/ledger.test.ts <<'EOF'
import { describe, expect, it } from "vitest";
import { deposit, withdraw } from "./ledger";

describe("ledger", () => {
  it("deposits", () => {
    expect(deposit(10, 5)).toBe(15);
  });

  it("withdraws within the balance", () => {
    expect(withdraw(100, 30, false)).toBe(70);
  });

  it("refuses an overdraft that is not allowed", () => {
    expect(() => withdraw(10, 20, false)).toThrow("overdraft not allowed");
  });

  it("refuses an amount that is not positive", () => {
    expect(() => withdraw(10, 0, false)).toThrow("amount must be positive");
  });

  it("refuses an amount over the limit", () => {
    expect(() => withdraw(5000, 1001, false)).toThrow("amount over limit");
  });

  it("refuses a total over the limit", () => {
    expect(() => withdraw(1000001, 1, false)).toThrow("total over limit");
  });

  it("refuses a fractional amount", () => {
    expect(() => withdraw(10, 1.5, false)).toThrow("amount must be whole");
  });

  it("refuses an overdraft past the limit", () => {
    expect(() => withdraw(0, 101, true)).toThrow("overdraft limit exceeded");
  });
});
EOF
commit "Cover withdraw"
run "$SFJS" coverage
run "$SFJS" crap --base main
withdraw_line="$(grep -E 'src/ledger\.ts:[0-9]+ withdraw$' <<<"$OUT")"
check "QA-6.4 covered withdraw is ok and crap exits 0" \
  'rc_is 0 && grep -qE "^ok +crap +[0-9.]+ +cc +[0-9]+ +cov +100% " <<<"$withdraw_line"'
run "$SFJS" crap --all
crap_values="$(grep -E '^(OVER|ok) ' <<<"$OUT" | awk '{print $3}')"
check "QA-6.5 crap --all lists deposit, worst first, with an all-function summary" \
  'out_matches "src/ledger\.ts:[0-9]+ deposit$" && [ "$crap_values" = "$(sort -rn <<<"$crap_values")" ] && last_line | grep -qE "^[0-9]+ function\(s\), [0-9]+ over CRAP 10$"'

cat >>src/ledger.ts <<'EOF'

export function doubled(amounts: number[]): number[] {
  return amounts.map((amount) => amount * 2);
}
EOF
commit "Add an untested arrow"
run "$SFJS" coverage
run "$SFJS" crap --base main
check "QA-6.6 an untested arrow callback shows as (anonymous)" \
  'out_matches "cov +0% +src/ledger\.ts:[0-9]+ \(anonymous\)$"'
mv tmp/coverage "$QA/coverage.saved"
run "$SFJS" crap --base main
check "QA-6.7 crap without coverage exits 2" \
  'rc_is 2 && err_has "no coverage-final.json under tmp/coverage; run sf-js coverage first"'
mv "$QA/coverage.saved" tmp/coverage

# ---------------------------------------------------------------- QA-7
section "QA-7 Coverage and CRAP (P2)"
cd "$QA/jest-js"
run "$SFJS" coverage
check "QA-7.1 jest coverage exits 0 and writes the report" \
  'rc_is 0 && [ -f tmp/coverage/coverage-final.json ] && cmd_line "pnpm exec jest" | grep -qF -- "--coverageDirectory=tmp/coverage"'
sed -i 's/return sum - 10;/return sum - 15;/' src/cart.js
sed -i 's/toBe(110)/toBe(105)/' src/cart.test.js
commit "Bigger discount"
run "$SFJS" coverage
run "$SFJS" crap --base main
check "QA-7.2 crap runs eslint through pnpm and lists total" \
  '[ -n "$(cmd_line "pnpm exec eslint")" ] && out_matches "src/cart\.js:[0-9]+ total$"'

# ---------------------------------------------------------------- QA-8
section "QA-8 DRY (P1)"
cd "$QA/vitest-ts"
legacy_block >src/copy.ts
commit "Copy the legacy block"
run "$SFJS" dry --base main
dry_findings="$(dry_lines)"
check "QA-8.1 dry runs jscpd with the JS/TS formats and exits 1" \
  'rc_is 1 && cmd_line "npx --yes jscpd@4" | grep -qF -- "--format javascript,typescript,jsx,tsx"'
check "QA-8.1 the clone of src/copy.ts and src/legacy.ts is listed as changed" \
  'out_matches "^[0-9]+ tokens  (src/copy\.ts:[0-9]+-[0-9]+ \(changed\)  ==  src/legacy\.ts:[0-9]+-[0-9]+|src/legacy\.ts:[0-9]+-[0-9]+  ==  src/copy\.ts:[0-9]+-[0-9]+ \(changed\))$"'
check "QA-8.1 summary reads 1 of 1" '[ "$(last_line)" = "1 of 1 clone(s) touch changed lines" ]'
run "$SFJS" dry --report-only --base main
check "QA-8.2 --report-only prints the same findings without running jscpd" \
  'rc_is 1 && no_cmd_lines && [ "$(dry_lines)" = "$dry_findings" ] && [ -n "$dry_findings" ]'
mkdir -p node_modules/x dist
legacy_block >node_modules/x/index.js
legacy_block >dist/out.js
legacy_block >src/types.d.ts
run "$SFJS" dry --base main
check "QA-8.3 node_modules, dist and .d.ts files are ignored" \
  'out_lacks node_modules/x && out_lacks dist/out.js && out_lacks types.d.ts'
git rm -q src/copy.ts && git commit -q -m "Drop the copy"
run "$SFJS" dry --base main
check "QA-8.4 without the copy dry exits 0" 'rc_is 0'
rm -rf node_modules/x dist src/types.d.ts
run "$SFJS" dry --base main --min-tokens 500
check "QA-8.5 --min-tokens reaches the jscpd line" 'cmd_line "npx --yes jscpd@4" | grep -qF -- "--min-tokens 500"'
legacy_block >src/copy2.ts
run "$SFJS" dry --base main
check "QA-8.6 an untracked copy counts as changed" 'rc_is 1 && out_has "src/copy2.ts:" && out_matches "src/copy2\.ts:[0-9]+-[0-9]+ \(changed\)"'
rm src/copy2.ts

# ---------------------------------------------------------------- QA-9
section "QA-9 Mutation (P1)"
cd "$QA/vitest-ts"
sed -i 's/if (amount > total \&\& !overdraft)/if (amount >= total \&\& !overdraft)/' src/ledger.ts
commit "Refuse withdrawing the whole balance"
run "$SFJS" mutate --base main
stryker_line="$(cmd_line 'npx --no-install stryker run')"
check "QA-9.1 mutate runs stryker with the expected arguments" \
  'line_has_all "$stryker_line" "--mutate src/ledger.ts" --incremental "--testRunner vitest" "--checkers typescript" "--concurrency 4"'
check "QA-9.1 survivors exit 1 and are listed" \
  'rc_is 1 && out_matches "^(Survived|NoCoverage) +src/ledger\.ts:" && last_line | grep -q "^mutation score "'
check "QA-9.1 the JSON and incremental reports exist" '[ -f tmp/stryker/mutation.json ] && [ -f tmp/stryker/incremental.json ]'
mutate_findings="$(grep -E '^(Survived|NoCoverage|mutation score)' <<<"$OUT")"
run "$SFJS" mutate --report-only
check "QA-9.2 --report-only prints the same findings without stryker" \
  'no_cmd_lines && [ "$(grep -E "^(Survived|NoCoverage|mutation score)" <<<"$OUT")" = "$mutate_findings" ]'

cat >>src/ledger.test.ts <<'EOF'

describe("ledger boundaries", () => {
  it("refuses to withdraw the whole balance without an overdraft", () => {
    expect(() => withdraw(50, 50, false)).toThrow("overdraft not allowed");
  });

  it("allows an overdraft when asked", () => {
    expect(withdraw(10, 20, true)).toBe(-10);
  });

  it("allows the smallest amount", () => {
    expect(withdraw(10, 1, false)).toBe(9);
  });

  it("allows the largest amount", () => {
    expect(withdraw(5000, 1000, false)).toBe(4000);
  });

  it("allows the largest total", () => {
    expect(withdraw(1000000, 1, false)).toBe(999999);
  });

  it("allows an overdraft up to the limit", () => {
    expect(withdraw(0, 100, true)).toBe(-100);
  });

  it("doubles every amount", () => {
    expect(doubled([3, 5])).toEqual([6, 10]);
  });
});
EOF
sed -i 's/import { deposit, withdraw } from ".\/ledger";/import { deposit, doubled, withdraw } from ".\/ledger";/' src/ledger.test.ts
commit "Kill the mutants"
run "$SFJS" mutate --base main
check "QA-9.3 with the mutants killed mutate exits 0" 'rc_is 0'
check "QA-9.3 stryker reuses the incremental report" 'grep -qE "Incremental report" <<<"$OUT$ERR" && grep -qE "[1-9][0-9]* of [0-9]+ mutant result\(s\) are reused" <<<"$OUT$ERR"'

git switch -q -c swarm/qa-tests-only main
sed -i 's/toBe(15)/toBe(5 + 10)/' src/ledger.test.ts
commit "Reword a test"
run "$SFJS" mutate --base main
check "QA-9.4 only a test changed: nothing to mutate, no stryker" \
  'rc_is 0 && out_has "no changed source files to mutate" && no_cmd_lines'
git switch -q swarm/qa

run "$SFJS" mutate --base main -- --concurrency 2
stryker_line="$(cmd_line 'npx --no-install stryker run')"
check "QA-9.5 a passed --concurrency replaces the default" \
  'grep -qE -- "--concurrency 2$" <<<"$stryker_line" && ! grep -qF -- "--concurrency 4" <<<"$stryker_line"'

cd "$QA/jest-js"
run "$SFJS" mutate --base main
stryker_line="$(cmd_line 'pnpm exec stryker run')"
check "QA-9.6 P2 mutates through pnpm with the jest runner and no checker" \
  '[ -n "$stryker_line" ] && grep -qF -- "--testRunner jest" <<<"$stryker_line" && ! grep -qF -- "--checkers" <<<"$stryker_line"'
check "QA-9.6 P2 mutation completes with a score" 'last_line | grep -q "^mutation score "'

# ---------------------------------------------------------------- QA-10
section "QA-10 Base branch"
rm -rf "$QA/base" && cp -R "$QA/vitest-ts" "$QA/base" && cd "$QA/base"
legacy_block >src/copy.ts
run "$SFJS" dry --base main
run "$SFJS" dry --report-only --base main
main_out="$OUT" main_rc="$RC"
git branch -m main master
run "$SFJS" dry --report-only
check "QA-10.1 master is found as the base" '[ "$RC" = "$main_rc" ] && [ "$OUT" = "$main_out" ]'
git branch -m master trunk
run "$SFJS" dry --report-only
check "QA-10.2 no main or master exits 2" 'rc_is 2 && err_has "no base branch found"'
run env SWARMFORGE_BASE=trunk "$SFJS" dry --report-only
check "QA-10.2 SWARMFORGE_BASE names the base" '[ "$RC" = "$main_rc" ] && [ "$OUT" = "$main_out" ]'

# ---------------------------------------------------------------- QA-11
section "QA-11 Installation through sf tool"
# sf reads tools.conf from the main checkout, which gains sf-js only when the task
# merges; a clone of the task branch stands in for the merged checkout.
rm -rf "$QA/merged" && quiet git clone -q "$ROOT" "$QA/merged" && cd "$QA/merged"
quiet git checkout -q "$(git -C "$ROOT" rev-parse HEAD)"
run swarmforge/bin/sf tool ensure sf-js
check "QA-11.1 sf tool ensure sf-js succeeds" 'rc_is 0'
run swarmforge/bin/sf tool require sf-js
check "QA-11.1 sf tool require sf-js reports OK" 'rc_is 0 && out_matches "^OK: sf-js .+"'

# ---------------------------------------------------------------- QA-12
section "QA-12 Release checks"
cd "$ROOT"
run bb --config swarmforge/tools/csharp/bb.edn test
check "QA-12.1 the sf-cs tests pass" 'rc_is 0'
run bb --config swarmforge/tools/javascript/bb.edn test
check "QA-12.2 the sf-js unit tests pass" 'rc_is 0'
if command -v gherkin-parser >/dev/null || swarmforge/bin/sf tool ensure gherkin-parser >/dev/null 2>&1; then
  run bb --config swarmforge/tools/javascript/bb.edn acceptance
  check "QA-12.2 the sf-js acceptance tests pass" 'rc_is 0'
else
  fail "QA-12.2 gherkin-parser could not be installed for the acceptance tests"
fi

echo
echo "$passed passed, $failed failed, $skipped skipped"
[ "$failed" -eq 0 ]
