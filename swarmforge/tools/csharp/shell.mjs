import { execFileSync, spawnSync } from 'node:child_process';

export const EXIT_OK = 0;
export const EXIT_FINDINGS = 1;
export const EXIT_USAGE = 2;

export class UsageError extends Error {}

export function git(args, cwd = process.cwd()) {
  return execFileSync('git', ['-c', 'core.quotePath=false', ...args], {
    cwd,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

export function repoRoot(cwd = process.cwd()) {
  return git(['rev-parse', '--show-toplevel'], cwd).trim();
}

export function refExists(ref, cwd) {
  const result = spawnSync('git', ['rev-parse', '--verify', '--quiet', `${ref}^{commit}`], { cwd, stdio: 'ignore' });
  return result.status === 0;
}

/** Runs a tool in the foreground, streaming its output; returns its exit status. */
export function run(command, args, { cwd = process.cwd(), env = process.env } = {}) {
  console.log(`$ ${[command, ...args].join(' ')}`);
  const result = spawnSync(command, args, { cwd, env, stdio: 'inherit' });
  if (result.error) throw new Error(`${command}: ${result.error.message}`);
  return result.status ?? EXIT_USAGE;
}

export function available(command) {
  const probe = process.platform === 'win32' ? 'where' : 'which';
  return spawnSync(probe, [command], { stdio: 'ignore' }).status === 0;
}
