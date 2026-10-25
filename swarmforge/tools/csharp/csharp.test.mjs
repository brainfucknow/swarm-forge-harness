import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { after, before, describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { changedLinesFromDiff, overlapsChanged, toChangedPath } from './changes.mjs';
import { crapScore, parseCobertura, signatureStart } from './cobertura.mjs';
import { clonesTouchingChanges, parseClones } from './jscpd.mjs';
import { parseMutants, summarize, survivors } from './stryker.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.join(here, 'fixtures');
const read = (...parts) => fs.readFileSync(path.join(fixtures, ...parts), 'utf8');

describe('changed lines', () => {
  test('reads added lines per file and skips removed-only hunks and deleted files', () => {
    const diff = [
      'diff --git a/Bank/Ledger.cs b/Bank/Ledger.cs',
      '--- a/Bank/Ledger.cs',
      '+++ b/Bank/Ledger.cs',
      '@@ -7 +7 @@',
      '-            total += amount;',
      '+            total = total + amount;',
      '@@ -12,2 +12,0 @@',
      '-old',
      '-older',
      '@@ -30,0 +29,2 @@',
      '+new one',
      '+new two',
      'diff --git a/Bank/Gone.cs b/Bank/Gone.cs',
      '--- a/Bank/Gone.cs',
      '+++ /dev/null',
      '@@ -1 +0,0 @@',
      '-bye',
    ].join('\n');
    const changed = changedLinesFromDiff(diff);
    assert.deepEqual([...changed.keys()], ['Bank/Ledger.cs']);
    assert.deepEqual([...changed.get('Bank/Ledger.cs')], [7, 29, 30]);
  });

  test('maps tool paths back to changed files', () => {
    const changed = new Map([['Bank/Ledger.cs', new Set([7])]]);
    assert.equal(toChangedPath('Bank/Ledger.cs', changed, '/repo'), 'Bank/Ledger.cs');
    assert.equal(toChangedPath('/repo/Bank/Ledger.cs', changed, '/repo'), 'Bank/Ledger.cs');
    assert.equal(toChangedPath('/elsewhere/Bank/Ledger.cs', changed, '/repo'), 'Bank/Ledger.cs');
    assert.equal(toChangedPath('C:\\w\\Bank\\Ledger.cs', changed, '/repo'), 'Bank/Ledger.cs');
    assert.equal(toChangedPath('/repo/Other/Ledger.cs', changed, '/repo'), null);
    assert.equal(overlapsChanged('Bank/Ledger.cs', 5, 7, changed), true);
    assert.equal(overlapsChanged('Bank/Ledger.cs', 8, 9, changed), false);
  });
});

describe('cobertura', () => {
  test('crap follows comp² × (1 − cov)³ + comp', () => {
    assert.equal(crapScore(1, 1), 1);
    assert.equal(crapScore(4, 0), 20);
    assert.equal(crapScore(4, 0.25).toFixed(3), '10.750');
  });

  test('reads methods with spans, complexity and coverage; skips methods without lines', () => {
    const methods = parseCobertura(read('coverage', 'coverage.cobertura.xml'));
    assert.deepEqual(
      methods.map((m) => [m.className, m.name, m.file, m.complexity, m.coverage, m.firstLine, m.lastLine]),
      [
        ['Bank.Ledger', 'Deposit', 'Bank/Ledger.cs', 1, 1, 6, 8],
        ['Bank.Ledger', 'Withdraw', 'Bank/Ledger.cs', 4, 0.25, 13, 17],
        ['Bank.Ledger', 'Balance', 'Bank/Ledger.cs', 1, 1, 20, 22],
        ['Bank.Audit<T>', 'Record', 'Bank/Audit.cs', 2, 0, 4, 5],
      ],
    );
  });

  test('signature start climbs over attributes and wrapped parameters', () => {
    const source = read('Ledger.cs.txt').split('\n');
    assert.equal(signatureStart(source, 13), 10);
    assert.equal(signatureStart(source, 6), 5);
    assert.equal(signatureStart(source, 20), 19);
  });
});

describe('jscpd', () => {
  test('keeps clones with a changed side and marks it', () => {
    const clones = parseClones(JSON.parse(read('jscpd-report.json')));
    const changed = new Map([
      ['Bank/Ledger.cs', new Set([14])],
      ['Bank/Audit.cs', new Set([4])],
    ]);
    const touching = clonesTouchingChanges(clones, changed, '/nonexistent/checkout');
    assert.deepEqual(
      touching.map((c) => c.sides.map((s) => `${s.file}${s.changed ? '*' : ''}`)),
      [
        ['Bank/Ledger.cs*', 'Bank/Legacy.cs'],
        ['Bank/Audit.cs*', 'Bank\\Older.cs'],
      ],
    );
    assert.deepEqual(clonesTouchingChanges(clones, new Map([['Bank/Ledger.cs', new Set([1])]]), '/r'), []);
  });
});

describe('stryker', () => {
  test('lists survivors and scores killed over scored mutants', () => {
    const mutants = parseMutants(JSON.parse(read('mutation-report.json')));
    assert.deepEqual(survivors(mutants).map((m) => [m.file, m.line, m.status]), [
      ['Bank/Ledger.cs', 14, 'Survived'],
      ['Bank/Ledger.cs', 14, 'NoCoverage'],
    ]);
    const { counts, score } = summarize(mutants);
    assert.deepEqual(counts, { Timeout: 1, Killed: 1, Survived: 1, NoCoverage: 1, Ignored: 1 });
    assert.equal(score, 50);
  });
});

describe('sf-cs in a task worktree', () => {
  let repo;
  let bin;
  const sfcs = (...args) =>
    spawnSync('bash', [path.join(here, '..', '..', 'bin', 'sf-cs'), ...args], {
      cwd: repo,
      encoding: 'utf8',
      env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}` },
    });
  const git = (...args) => execFileSync('git', args, { cwd: repo, stdio: 'pipe', encoding: 'utf8' });

  before(() => {
    repo = fs.mkdtempSync(path.join(os.tmpdir(), 'sfcs-'));
    bin = path.join(repo, 'fakebin');
    fs.mkdirSync(bin);
    fs.writeFileSync(
      path.join(bin, 'dotnet'),
      '#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$FAKE_DOTNET_LOG"\nif [ "$1" = test ] && [ -n "$FAKE_COVERAGE" ]; then mkdir -p tmp/coverage/run && cp "$FAKE_COVERAGE" tmp/coverage/run/coverage.cobertura.xml; fi\n',
      { mode: 0o755 },
    );
    git('init', '--quiet', '--initial-branch=main');
    git('config', 'user.email', 'test@example.com');
    git('config', 'user.name', 'Test');
    fs.mkdirSync(path.join(repo, 'Bank'));
    fs.writeFileSync(path.join(repo, 'Bank', 'Ledger.cs'), read('Ledger.cs.txt'));
    fs.writeFileSync(path.join(repo, 'Bank', 'Legacy.cs'), 'namespace Bank { }\n');
    git('add', '.');
    git('commit', '--quiet', '-m', 'base');
    git('switch', '--quiet', '-c', 'swarm/abc1234');
    const ledger = read('Ledger.cs.txt').replace('if (amount > total', 'if (amount >= total');
    fs.writeFileSync(path.join(repo, 'Bank', 'Ledger.cs'), ledger);
    git('commit', '--quiet', '-am', 'task work');
    fs.writeFileSync(path.join(repo, 'Bank', 'Audit.cs'), 'namespace Bank\n{\n    public class Audit<T> { public void Record(T item) { if (item == null) return; } }\n}\n');
  });

  after(() => fs.rmSync(repo, { recursive: true, force: true }));

  test('crap gates only changed methods and fails over the limit', () => {
    const result = sfcs('crap', '--base', 'main', '--report', path.join(fixtures, 'coverage'));
    assert.equal(result.status, 1, result.stderr);
    assert.deepEqual(result.stdout.trimEnd().split('\n'), [
      'OVER crap   10.8  cc   4  cov  25%  Bank/Ledger.cs:10 Ledger.Withdraw',
      'ok   crap    6.0  cc   2  cov   0%  Bank/Audit.cs:4 Audit<T>.Record',
      '2 changed method(s), 1 over CRAP 10',
    ]);
  });

  test('crap --all reports every method', () => {
    const result = sfcs('crap', '--all', '--report', path.join(fixtures, 'coverage'));
    assert.equal(result.status, 1, result.stderr);
    assert.equal(result.stdout.trimEnd().split('\n').at(-1), '4 method(s), 1 over CRAP 10');
  });

  test('dry --report-only shows clones touching changed lines', () => {
    fs.mkdirSync(path.join(repo, 'tmp', 'jscpd'), { recursive: true });
    fs.copyFileSync(path.join(fixtures, 'jscpd-report.json'), path.join(repo, 'tmp', 'jscpd', 'jscpd-report.json'));
    const result = sfcs('dry', '--report-only', '--base', 'main');
    assert.equal(result.status, 1, result.stderr);
    assert.deepEqual(result.stdout.trimEnd().split('\n'), [
      '64 tokens  Bank/Ledger.cs:13-15 (changed)  ==  Bank/Legacy.cs:30-32',
      '52 tokens  Bank/Audit.cs:4-5 (changed)  ==  Bank\\Older.cs:9-10',
      '2 of 3 clone(s) touch changed lines',
    ]);
  });

  test('mutate --report-only lists survivors and the score', () => {
    fs.mkdirSync(path.join(repo, 'tmp', 'stryker', 'reports'), { recursive: true });
    fs.copyFileSync(path.join(fixtures, 'mutation-report.json'), path.join(repo, 'tmp', 'stryker', 'reports', 'mutation-report.json'));
    const result = sfcs('mutate', '--report-only');
    assert.equal(result.status, 1, result.stderr);
    const lines = result.stdout.trimEnd().split('\n');
    assert.equal(lines[0], 'Survived   Bank/Ledger.cs:14  Equality mutation  > replaced with >=');
    assert.equal(lines[1], 'NoCoverage Bank/Ledger.cs:14  Boolean mutation  true');
    assert.match(lines[2], /^mutation score 50\.0% \(/);
  });

  test('test, property, coverage and mutate build the dotnet command lines', () => {
    const log = path.join(repo, 'dotnet.log');
    const calls = (...args) => {
      const result = sfcs(...args);
      return { status: result.status, args: fs.readFileSync(log, 'utf8').trimEnd().split('\n'), stderr: result.stderr };
    };
    process.env.FAKE_DOTNET_LOG = log;
    assert.deepEqual(calls('test', '--no-build').args, ['test', '--filter', 'Category!=Property', '--no-build']);
    assert.deepEqual(calls('property').args, ['test', '--filter', 'Category=Property']);
    const mutate = calls('mutate', '--base', 'main', '--', '--project', 'Bank.csproj');
    assert.deepEqual(mutate.args, ['stryker', '--reporter', 'json', '--reporter', 'progress', '--output', 'tmp/stryker', '--since:main', '--project', 'Bank.csproj']);
    process.env.FAKE_COVERAGE = path.join(fixtures, 'coverage', 'coverage.cobertura.xml');
    const coverage = calls('coverage');
    assert.equal(coverage.status, 0, coverage.stderr);
    assert.deepEqual(coverage.args.slice(0, 7), ['test', '--filter', 'Category!=Property', '--collect', 'XPlat Code Coverage', '--results-directory', 'tmp/coverage']);
    assert.ok(fs.existsSync(path.join(repo, 'tmp', 'coverage', 'run', 'coverage.cobertura.xml')));
    delete process.env.FAKE_COVERAGE;
    delete process.env.FAKE_DOTNET_LOG;
  });

  test('usage errors exit 2', () => {
    assert.equal(sfcs('nope').status, 2);
    assert.equal(sfcs('crap', '--report', path.join(repo, 'missing')).status, 2);
  });
});
