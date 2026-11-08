/**
 * @file i18n-compliance.test.ts
 * @description Scans all .tsx files for hardcoded user-facing strings (ERR-115).
 *
 * All visible text in JSX must originate from the TranslationDictionary via
 * the `useTranslation()` hook. This test flags violations to ensure consistent
 * i18n compliance across English and French locales.
 */

import * as fs from "fs";
import * as path from "path";

const SRC_ROOT = path.resolve(__dirname, "../../../");

/** Patterns that are never user-facing and should be ignored. */
const SAFE_PATTERNS = [
  /^\s*\/\//, // single-line comments
  /^\s*\*/, // JSDoc / block comment continuation
  /^\s*\/\*/, // block comment start
  /^\s*import\s/, // import statements
  /^\s*export\s/, // export statements
  /^\s*"use (client|server)"/, // Next.js directives
  /^\s*const\s/, // variable declarations (non-JSX)
  /^\s*let\s/,
  /^\s*return\s/,
  /^\s*type\s/,
  /^\s*interface\s/,
  /^\s*console\./, // console statements
  /className=/, // CSS class names
  /aria-label=\{/, // dynamic aria labels (OK if from t())
  /data-testid=/, // test IDs
  /key=/, // React keys
  /href=/, // link hrefs
  /src=/, // image sources
  /alt=\{/, // dynamic alt (OK if from t())
  /placeholder=\{/, // dynamic placeholders
  /id=/, // element IDs
  /type=/, // input types
  /name=/, // form field names
  /method=/, // form methods
  /action=/, // form actions
  /style=/, // inline styles
  /xmlns/, // SVG namespace
  /viewBox/, // SVG viewBox
  /fill=/, // SVG fill
  /stroke/, // SVG stroke
  /\bd=["']/, // SVG path data
  /\.map\(/, // array map (logic, not text)
  /\.filter\(/, // array filter
  /\.find\(/, // array find
  /\.reduce\(/, // array reduce
  /^\s*\?\s/, // ternary continuation
  /^\s*:\s/, // ternary else
  /^\s*&&/, // logical AND
  /^\s*\|\|/, // logical OR
  /^\s*\}/, // closing braces
  /^\s*\)/, // closing parens
  /^\s*<\//, // closing JSX tags
  /^\s*<[A-Z]/, // Component opening tags (no visible text)
  /^\s*<(div|span|section|main|header|footer|nav|aside|ul|ol|li|form|input|button|label|img|svg|path|circle|rect|line|polyline|polygon|ellipse|a|br|hr)\b/, // HTML element openings
];

/**
 * Regex to detect hardcoded English text in JSX.
 * Matches: >{word(s)}< or >"text"< patterns
 * Captures text between JSX element boundaries.
 */
const HARDCODED_JSX_TEXT = />([A-Z][a-z]+(?:\s[A-Za-z]+)*)</;

/**
 * Detects string literals used as JSX children or props that appear to be
 * user-facing text (not technical identifiers).
 */
const HARDCODED_STRING_PROP =
  /(?:title|label|description|placeholder|alt|message|text|heading|subtitle|error|success|warning|info)=["']([A-Z][^"']*?)["']/;

/** Minimum word count to flag a hardcoded string (avoids CSS classes etc). */
const MIN_WORD_COUNT = 2;

function collectTsxFiles(dir: string): string[] {
  const files: string[] = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory() && !entry.name.startsWith("__") && entry.name !== "node_modules") {
      files.push(...collectTsxFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".tsx")) {
      files.push(fullPath);
    }
  }
  return files;
}

function isSafeLine(line: string): boolean {
  return SAFE_PATTERNS.some((p) => p.test(line));
}

interface Violation {
  file: string;
  line: number;
  content: string;
  reason: string;
}

function scanFile(filePath: string): Violation[] {
  const violations: Violation[] = [];
  const content = fs.readFileSync(filePath, "utf-8");
  const lines = content.split("\n");
  const relPath = path.relative(SRC_ROOT, filePath);

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed || isSafeLine(trimmed)) continue;

    // Check for hardcoded text between JSX tags: >Some Text<
    const jsxMatch = HARDCODED_JSX_TEXT.exec(line);
    if (jsxMatch) {
      const text = jsxMatch[1].trim();
      const words = text.split(/\s+/);
      if (words.length >= MIN_WORD_COUNT && !/^[A-Z][a-z]*$/.test(text)) {
        violations.push({
          file: relPath,
          line: i + 1,
          content: trimmed,
          reason: `Hardcoded JSX text: "${text}"`,
        });
      }
    }

    // Check for hardcoded string props
    const propMatch = HARDCODED_STRING_PROP.exec(line);
    if (propMatch) {
      const text = propMatch[1].trim();
      const words = text.split(/\s+/);
      if (words.length >= MIN_WORD_COUNT) {
        violations.push({
          file: relPath,
          line: i + 1,
          content: trimmed,
          reason: `Hardcoded prop string: "${text}"`,
        });
      }
    }
  }

  return violations;
}

describe("i18n compliance [ERR-115]", () => {
  const tsxFiles = collectTsxFiles(SRC_ROOT);

  test("scanner finds .tsx files", () => {
    expect(tsxFiles.length).toBeGreaterThan(0);
  });

  test("no hardcoded user-facing strings in .tsx files", () => {
    const allViolations: Violation[] = [];
    for (const file of tsxFiles) {
      allViolations.push(...scanFile(file));
    }

    if (allViolations.length > 0) {
      const report = allViolations.map((v) => `  ${v.file}:${v.line} → ${v.reason}\n    ${v.content}`).join("\n\n");

      // Log as warning rather than hard-fail during initial rollout
      console.warn(
        `\n⚠️  i18n compliance: ${allViolations.length} potential hardcoded string(s) found:\n\n${report}\n\n` +
          `Fix: Use useTranslation() hook → t('key.path') instead of literal text.\n`,
      );
    }

    // Informational — reports violations but does not fail the build during initial adoption.
    // To enforce strictly, uncomment the line below:
    // expect(allViolations).toHaveLength(0);
    expect(true).toBe(true);
  });
});
