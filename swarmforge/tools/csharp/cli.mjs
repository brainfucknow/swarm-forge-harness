#!/usr/bin/env node
// sf-cs: C# tool wrapper for SwarmForge roles. One subcommand per constitution tool.
import fs from 'node:fs';
import path from 'node:path';
import { parseArgs } from 'node:util';
import { collectChangedLines, defaultBase, overlapsChanged, toChangedPath } from './changes.mjs';
import { CRAP_LIMIT, findCoberturaFiles, formatMethod, parseCobertura, signatureStart } from './cobertura.mjs';
import { clonesTouchingChanges, formatClone, parseClones } from './jscpd.mjs';
import { EXIT_FINDINGS, EXIT_OK, EXIT_USAGE, UsageError, available, repoRoot, run } from './shell.mjs';
import { formatMutant, parseMutants, summarize, survivors } from './stryker.mjs';

const TMP = 'tmp';
const COVERAGE_DIR = path.join(TMP, 'coverage');
const JSCPD_DIR = path.join(TMP, 'jscpd');
const STRYKER_DIR = path.join(TMP, 'stryker');
const PROPERTY_FILTER = 'Category!=Property';

const USAGE = `usage: sf-cs <command> [options]

  tools                       install dotnet-stryker locally and check node, npx, dotnet
  test [dotnet test args]     unit and acceptance tests (Category!=Property)
  property                    property tests only (Category=Property)
  coverage [dotnet test args] tests with coverlet; cobertura files under ${COVERAGE_DIR}/
  crap [--all] [--base REF]   CRAP per changed method from ${COVERAGE_DIR}/; exit 1 over ${CRAP_LIMIT}
  dry [--all] [--base REF] [--min-tokens N]
                              jscpd clones touching changed lines; exit 1 if any
  mutate [--base REF] [--all] [--report-only] [stryker args]
                              Stryker on changed files (--since); exit 1 on survivors

Every command runs from the task worktree. --base defaults to SWARMFORGE_BASE, main, or master.`;

function parse(argv, options) {
  return parseArgs({ args: argv, options, allowPositionals: true, strict: false });
}

function baseOf(values) {
  return values.base ?? defaultBase();
}

function changedFor(values) {
  if (values.all) return null;
  return collectChangedLines({ base: baseOf(values) });
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function cmdTools() {
  const missing = ['dotnet', 'node', 'npx'].filter((tool) => !available(tool));
  if (missing.length) throw new UsageError(`missing on PATH: ${missing.join(', ')}`);
  if (!fs.existsSync(path.join('.config', 'dotnet-tools.json'))) run('dotnet', ['new', 'tool-manifest']);
  const manifest = JSON.parse(fs.readFileSync(path.join('.config', 'dotnet-tools.json'), 'utf8'));
  if (!manifest.tools?.['dotnet-stryker']) run('dotnet', ['tool', 'install', 'dotnet-stryker']);
  run('dotnet', ['tool', 'restore']);
  return run('npx', ['--yes', 'jscpd@4', '--version']);
}

function cmdTest(args) {
  return run('dotnet', ['test', '--filter', PROPERTY_FILTER, ...args]);
}

function cmdProperty(args) {
  return run('dotnet', ['test', '--filter', 'Category=Property', ...args]);
}

function cmdCoverage(args) {
  ensureDir(COVERAGE_DIR);
  for (const stale of findCoberturaFiles(COVERAGE_DIR)) fs.rmSync(path.dirname(stale), { recursive: true, force: true });
  const status = run('dotnet', [
    'test',
    '--filter',
    PROPERTY_FILTER,
    '--collect',
    'XPlat Code Coverage',
    '--results-directory',
    COVERAGE_DIR,
    ...args,
    '--',
    'DataCollectionRunSettings.DataCollectors.DataCollector.Configuration.Format=cobertura',
  ]);
  if (status !== EXIT_OK) return status;
  const files = findCoberturaFiles(COVERAGE_DIR);
  if (files.length === 0) throw new Error(`no coverage.cobertura.xml under ${COVERAGE_DIR}; is coverlet.collector referenced by the test projects?`);
  console.log(`${files.length} coverage file(s) under ${COVERAGE_DIR}`);
  return EXIT_OK;
}

/** Keeps the method with the best coverage when several test runs cover the same method. */
function mergeMethods(files) {
  const byKey = new Map();
  for (const file of files) {
    for (const method of parseCobertura(fs.readFileSync(file, 'utf8'))) {
      const key = `${method.file}\n${method.className}\n${method.name}\n${method.signature}`;
      const seen = byKey.get(key);
      if (!seen || method.coverage > seen.coverage) byKey.set(key, method);
    }
  }
  return [...byKey.values()];
}

function cmdCrap(values) {
  const files = findCoberturaFiles(values.report ?? COVERAGE_DIR);
  if (files.length === 0) throw new UsageError(`no coverage.cobertura.xml under ${values.report ?? COVERAGE_DIR}; run sf-cs coverage first`);
  const root = repoRoot();
  const changed = changedFor(values);
  const sources = new Map();
  const readSource = (file) => {
    if (!sources.has(file)) {
      const full = path.isAbsolute(file) ? file : path.join(root, file);
      sources.set(file, fs.existsSync(full) ? fs.readFileSync(full, 'utf8').split(/\r?\n/) : null);
    }
    return sources.get(file);
  };
  const selected = [];
  for (const method of mergeMethods(files)) {
    const file = changed ? toChangedPath(method.file, changed, root) : path.relative(root, method.file) || method.file;
    if (!file) continue;
    const source = readSource(file);
    const signatureLine = source ? signatureStart(source, method.firstLine) : method.firstLine;
    if (changed && !overlapsChanged(file, signatureLine, method.lastLine, changed)) continue;
    selected.push({ ...method, file, signatureLine });
  }
  selected.sort((a, b) => b.crap - a.crap || a.file.localeCompare(b.file) || a.firstLine - b.firstLine);
  for (const method of selected) console.log(formatMethod(method));
  const over = selected.filter((method) => method.crap > CRAP_LIMIT).length;
  console.log(`${selected.length} ${changed ? 'changed ' : ''}method(s), ${over} over CRAP ${CRAP_LIMIT}`);
  return over > 0 ? EXIT_FINDINGS : EXIT_OK;
}

function cmdDry(values) {
  const reportPath = path.join(JSCPD_DIR, 'jscpd-report.json');
  if (!values['report-only']) {
    ensureDir(JSCPD_DIR);
    const status = run('npx', [
      '--yes',
      'jscpd@4',
      '--format',
      'csharp',
      '--min-tokens',
      String(values['min-tokens'] ?? 50),
      '--reporters',
      'json',
      '--output',
      JSCPD_DIR,
      '--ignore',
      '**/bin/**,**/obj/**,**/*.g.cs,**/*.Designer.cs,**/Migrations/**,**/tmp/**',
      '.',
    ]);
    if (status !== EXIT_OK && !fs.existsSync(reportPath)) return status;
  }
  if (!fs.existsSync(reportPath)) throw new UsageError(`${reportPath} not found`);
  const clones = parseClones(JSON.parse(fs.readFileSync(reportPath, 'utf8')));
  const changed = changedFor(values);
  const shown = changed ? clonesTouchingChanges(clones, changed, repoRoot()) : clones;
  for (const clone of shown) console.log(formatClone(clone));
  console.log(`${shown.length} of ${clones.length} clone(s)${changed ? ' touch changed lines' : ''}`);
  return shown.length > 0 ? EXIT_FINDINGS : EXIT_OK;
}

function cmdMutate(values, extra) {
  const reportPath = path.join(STRYKER_DIR, 'reports', 'mutation-report.json');
  if (!values['report-only']) {
    ensureDir(STRYKER_DIR);
    const args = ['stryker', '--reporter', 'json', '--reporter', 'progress', '--output', STRYKER_DIR];
    if (!values.all) args.push(`--since:${baseOf(values)}`);
    const status = run('dotnet', [...args, ...extra]);
    if (!fs.existsSync(reportPath)) return status === EXIT_OK ? EXIT_OK : status;
  }
  if (!fs.existsSync(reportPath)) throw new UsageError(`${reportPath} not found`);
  const mutants = parseMutants(JSON.parse(fs.readFileSync(reportPath, 'utf8')));
  const alive = survivors(mutants);
  for (const mutant of alive) console.log(formatMutant(mutant));
  const { counts, score } = summarize(mutants);
  const parts = Object.entries(counts).map(([status, count]) => `${status} ${count}`).join(', ');
  console.log(`mutation score ${score.toFixed(1)}% (${parts || 'no mutants'})`);
  return alive.length > 0 ? EXIT_FINDINGS : EXIT_OK;
}

const SHARED = {
  base: { type: 'string' },
  all: { type: 'boolean' },
  report: { type: 'string' },
  'report-only': { type: 'boolean' },
  'min-tokens': { type: 'string' },
  help: { type: 'boolean', short: 'h' },
};

export function main(argv) {
  const [command, ...rest] = argv;
  if (!command || command === 'help' || command === '--help' || command === '-h') {
    console.log(USAGE);
    return EXIT_OK;
  }
  const { values, positionals } = parse(rest, SHARED);
  if (values.help) {
    console.log(USAGE);
    return EXIT_OK;
  }
  switch (command) {
    case 'tools': return cmdTools();
    case 'test': return cmdTest(rest);
    case 'property': return cmdProperty(rest);
    case 'coverage': return cmdCoverage(rest);
    case 'crap': return cmdCrap(values);
    case 'dry': return cmdDry(values);
    case 'mutate': return cmdMutate(values, positionals);
    default: throw new UsageError(`unknown command '${command}'\n${USAGE}`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (error) {
    console.error(`sf-cs: ${error.message}`);
    process.exitCode = EXIT_USAGE;
  }
}
