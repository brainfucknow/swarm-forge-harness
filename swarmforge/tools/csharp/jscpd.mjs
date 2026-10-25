import { overlapsChanged, toChangedPath } from './changes.mjs';

/** Flattens a jscpd JSON report into clones with both sides' file and line range. */
export function parseClones(report) {
  return (report.duplicates ?? []).map((duplicate) => ({
    tokens: duplicate.tokens,
    lines: duplicate.lines,
    sides: [duplicate.firstFile, duplicate.secondFile].map((side) => ({ file: side.name, start: side.start, end: side.end })),
  }));
}

/** Marks each side that touches a changed line; keeps clones with at least one such side. */
export function clonesTouchingChanges(clones, changed, root) {
  return clones
    .map((clone) => ({
      ...clone,
      sides: clone.sides.map((side) => {
        const file = toChangedPath(side.file, changed, root);
        return { ...side, file: file ?? side.file, changed: file ? overlapsChanged(file, side.start, side.end, changed) : false };
      }),
    }))
    .filter((clone) => clone.sides.some((side) => side.changed));
}

export function formatClone(clone) {
  const side = (s) => `${s.file}:${s.start}-${s.end}${s.changed ? ' (changed)' : ''}`;
  return `${clone.tokens} tokens  ${side(clone.sides[0])}  ==  ${side(clone.sides[1])}`;
}
