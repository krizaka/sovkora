#!/usr/bin/env node
/**
 * The Java test counts of this build, per module, from the Surefire and Failsafe reports (a Markdown table for the CI
 * summary). It is the proof the consolidation gate reads (ADR-029: the counts may only be at least the baselines).
 *   node scripts/test-counts.mjs [--json]
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

/** Code-point order, the order `sort()` gives strings without a compare function, said explicitly. */
const byCodePoint = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/** The totals of the JUnit XML reports in a directory: { tests, failures, errors, skipped }. */
export function totals(xmls) {
  const sum = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  for (const xml of xmls) {
    const head = /<testsuite\b([^>]*)>/.exec(xml)?.[1] ?? "";
    for (const k of Object.keys(sum)) sum[k] += Number(new RegExp(`\\b${k}="(\\d+)"`).exec(head)?.[1] ?? 0);
  }
  return sum;
}

function reports(dir) {
  return existsSync(dir) ? readdirSync(dir).filter((f) => /^TEST-.*\.xml$/.test(f)).map((f) => readFileSync(join(dir, f), "utf8")) : [];
}

function modules(dir, out = []) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (!e.isDirectory() || ["node_modules", ".git", "apps", "docs", ".krizaka-java"].includes(e.name)) continue;
    const path = join(dir, e.name);
    if (e.name === "target") {
      out.push(dirname(path));
      continue;
    }
    modules(path, out);
  }
  return out;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const rows = modules(ROOT).sort(byCodePoint).map((m) => ({
    module: relative(ROOT, m),
    unit: totals(reports(join(m, "target/surefire-reports"))),
    it: totals(reports(join(m, "target/failsafe-reports"))),
  })).filter((r) => r.unit.tests + r.it.tests > 0);
  if (process.argv.includes("--json")) {
    console.log(JSON.stringify(rows, null, 2));
  } else {
    console.log("| Module | Unit | IT | Total | Failed | Skipped |\n|---|---:|---:|---:|---:|---:|");
    let all = 0;
    for (const r of rows) {
      const total = r.unit.tests + r.it.tests;
      all += total;
      const failed = r.unit.failures + r.unit.errors + r.it.failures + r.it.errors;
      console.log(`| ${r.module} | ${r.unit.tests} | ${r.it.tests} | ${total} | ${failed} | ${r.unit.skipped + r.it.skipped} |`);
    }
    console.log(`| **all** | | | **${all}** | | |`);
  }
}
