/** Mutant statuses that count as not killed by the test suite. */
export const SURVIVING = new Set(['Survived', 'NoCoverage']);

/**
 * Reads a Stryker mutation-report.json (mutation-testing-elements schema) into
 * a flat list of mutants with their file and line.
 */
export function parseMutants(report) {
  const mutants = [];
  for (const [file, entry] of Object.entries(report.files ?? {})) {
    for (const mutant of entry.mutants ?? []) {
      mutants.push({
        file,
        line: mutant.location?.start?.line ?? 0,
        status: mutant.status,
        mutator: mutant.mutatorName,
        replacement: mutant.replacement ?? '',
        description: mutant.description ?? '',
      });
    }
  }
  return mutants.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
}

export function survivors(mutants) {
  return mutants.filter((mutant) => SURVIVING.has(mutant.status));
}

export function summarize(mutants) {
  const counts = {};
  for (const mutant of mutants) counts[mutant.status] = (counts[mutant.status] ?? 0) + 1;
  const scored = mutants.filter((mutant) => !['Ignored', 'CompileError'].includes(mutant.status));
  const killed = scored.filter((mutant) => !SURVIVING.has(mutant.status)).length;
  const score = scored.length === 0 ? 100 : (killed / scored.length) * 100;
  return { counts, score };
}

export function formatMutant(mutant) {
  const detail = mutant.description || mutant.replacement;
  return `${mutant.status.padEnd(10)} ${mutant.file}:${mutant.line}  ${mutant.mutator}${detail ? `  ${detail}` : ''}`;
}
