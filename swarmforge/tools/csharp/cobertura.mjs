import fs from 'node:fs';
import path from 'node:path';

export const CRAP_LIMIT = 10;

const decode = (text) =>
  text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

const attribute = (tag, name) => {
  const match = new RegExp(`\\b${name}="([^"]*)"`).exec(tag);
  return match ? decode(match[1]) : null;
};

/** crap4j: comp² × (1 − coverage)³ + comp, with coverage as a fraction. */
export function crapScore(complexity, coverage) {
  return complexity * complexity * (1 - coverage) ** 3 + complexity;
}

/**
 * Reads every method out of a coverlet Cobertura file. Each method carries its
 * class, the source file as coverlet wrote it (resolved against <source>
 * entries when relative), cyclomatic complexity, line coverage, and the span of
 * coverable lines.
 */
export function parseCobertura(xml) {
  const sources = [...xml.matchAll(/<source>([^<]*)<\/source>/g)].map((match) => decode(match[1]));
  const methods = [];
  for (const [classTag, classBody] of blocks(xml, 'class')) {
    const className = attribute(classTag, 'name');
    const filename = resolveFile(attribute(classTag, 'filename'), sources);
    for (const [methodTag, methodBody] of blocks(classBody, 'method')) {
      const numbers = [...methodBody.matchAll(/<line number="(\d+)"/g)].map((match) => Number(match[1]));
      if (numbers.length === 0) continue;
      const complexity = Number(attribute(methodTag, 'complexity') ?? 1);
      const coverage = Number(attribute(methodTag, 'line-rate') ?? 0);
      methods.push({
        className,
        name: attribute(methodTag, 'name'),
        signature: attribute(methodTag, 'signature') ?? '',
        file: filename,
        complexity,
        coverage,
        crap: crapScore(complexity, coverage),
        firstLine: Math.min(...numbers),
        lastLine: Math.max(...numbers),
      });
    }
  }
  return methods;
}

function* blocks(xml, element) {
  const pattern = new RegExp(`<${element}\\b([^>]*)>([\\s\\S]*?)<\\/${element}>`, 'g');
  for (const match of xml.matchAll(pattern)) yield [match[1], match[2]];
}

function resolveFile(filename, sources) {
  if (!filename) return '';
  if (path.isAbsolute(filename) || /^[A-Za-z]:[\\/]/.test(filename)) return filename;
  const base = sources.find((source) => fs.existsSync(path.join(source, filename)));
  return base ? path.join(base, filename) : filename;
}

/** Finds coverage.cobertura.xml files under a results directory, newest first. */
export function findCoberturaFiles(directory) {
  if (!fs.existsSync(directory)) return [];
  const found = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name === 'coverage.cobertura.xml') found.push(full);
    }
  };
  walk(directory);
  return found.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
}

/** Widens a method's coverable span upward over its signature and attributes. */
export function signatureStart(sourceLines, firstLine) {
  let line = firstLine;
  while (line > 1) {
    const above = (sourceLines[line - 2] ?? '').trim();
    if (above === '' || above.startsWith('//') || /[;{}]$/.test(above)) break;
    line -= 1;
  }
  return line;
}

export function formatMethod(method) {
  const verdict = method.crap > CRAP_LIMIT ? 'OVER' : 'ok  ';
  const shortClass = method.className.split('.').pop();
  const coverage = `${Math.round(method.coverage * 100)}%`.padStart(4);
  return `${verdict} crap ${method.crap.toFixed(1).padStart(6)}  cc ${String(method.complexity).padStart(3)}  cov ${coverage}  ${method.file}:${method.signatureLine ?? method.firstLine} ${shortClass}.${method.name}`;
}
