import fs from 'node:fs';
import path from 'node:path';
import { git, refExists, repoRoot } from './shell.mjs';

const SOURCE_GLOB = '*.cs';

/**
 * Reads `git diff -U0` output into a map from new-side path to the set of
 * line numbers that were added or rewritten.
 */
export function changedLinesFromDiff(diffText) {
  const result = new Map();
  let current = null;
  let pendingNew = 0;
  let pendingOld = 0;
  let cursor = 0;
  for (const raw of diffText.split(/\r?\n/)) {
    if (pendingNew > 0 || pendingOld > 0) {
      if (raw.startsWith('+')) {
        pendingNew -= 1;
        if (current) result.get(current).add(cursor);
        cursor += 1;
      } else if (raw.startsWith('-')) {
        pendingOld -= 1;
      }
      continue;
    }
    if (raw.startsWith('+++ ')) {
      const name = raw.slice(4).replace(/\t.*$/, '');
      current = name === '/dev/null' ? null : name.replace(/^b\//, '');
      if (current && !result.has(current)) result.set(current, new Set());
      continue;
    }
    const hunk = /^@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/.exec(raw);
    if (hunk) {
      pendingOld = hunk[1] === undefined ? 1 : Number(hunk[1]);
      cursor = Number(hunk[2]);
      pendingNew = hunk[3] === undefined ? 1 : Number(hunk[3]);
    }
  }
  return result;
}

export function defaultBase(cwd = process.cwd()) {
  const candidates = [process.env.SWARMFORGE_BASE, 'main', 'master'].filter(Boolean);
  const found = candidates.find((ref) => refExists(ref, cwd));
  if (!found) throw new Error('no base branch found; pass --base <ref> or set SWARMFORGE_BASE');
  return found;
}

/**
 * Changed C# lines for the task: everything since the merge base with `base`,
 * committed or not, plus whole untracked files. Paths are repo-relative.
 */
export function collectChangedLines({ base, cwd = process.cwd() }) {
  const root = repoRoot(cwd);
  const mergeBase = git(['merge-base', base, 'HEAD'], cwd).trim();
  const changed = changedLinesFromDiff(git(['diff', '-U0', '--no-color', '--no-ext-diff', mergeBase, '--', SOURCE_GLOB], root));
  const untracked = git(['ls-files', '--others', '--exclude-standard', '--full-name', '--', SOURCE_GLOB], root);
  for (const file of untracked.split('\n').filter(Boolean)) {
    const count = fs.readFileSync(path.join(root, file), 'utf8').split('\n').length;
    changed.set(file, new Set(Array.from({ length: count }, (_, index) => index + 1)));
  }
  return changed;
}

/** Normalizes a path reported by a tool (absolute, relative, Windows) to a repo-relative changed path. */
export function toChangedPath(reported, changed, root) {
  let candidate = reported.replace(/\\/g, '/');
  const prefix = root ? `${root.replace(/\\/g, '/').replace(/\/+$/, '')}/` : null;
  if (prefix && candidate.startsWith(prefix)) candidate = candidate.slice(prefix.length);
  candidate = candidate.replace(/^\.\//, '');
  if (changed.has(candidate)) return candidate;
  for (const file of changed.keys()) {
    if (candidate.endsWith(`/${file}`)) return file;
  }
  return null;
}

export function overlapsChanged(file, start, end, changed) {
  const lines = changed.get(file);
  if (!lines) return false;
  for (let line = start; line <= end; line += 1) if (lines.has(line)) return true;
  return false;
}
