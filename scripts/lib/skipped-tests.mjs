// Integration tests that skip themselves (an Assumption that finds no workspace, no Docker...) report green in
// Failsafe, so a whole class such as StudioRunLifecycleIT (47 tests) can stop protecting anything unseen
// (orazaka-studio#98). This reads Failsafe's reports and names every skipped test not on the allow-list.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const attr = (tag, name) => new RegExp(String.raw`\s${name}="([^"]*)"`).exec(tag)?.[1];

/** One Failsafe report (TEST-<class>.xml) -> { name, tests, skipped }. */
export function readReport(xml) {
  const tag = /<testsuite\b[^>]*>/.exec(xml)?.[0] ?? "";
  return { name: attr(tag, "name") ?? "?", tests: Number(attr(tag, "tests") ?? 0), skipped: Number(attr(tag, "skipped") ?? 0) };
}

/** The Failsafe reports under a component, wherever its modules sit. */
export function reportFiles(dir, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && !["node_modules", ".git"].includes(entry.name)) reportFiles(full, found);
    else if (entry.isFile() && /^TEST-.*IT\.xml$/.test(entry.name) && dir.endsWith("failsafe-reports")) found.push(full);
  }
  return found;
}

/**
 * allowed: [{ component, class, formerClass?, reason }] - a justified skip. Returns the totals and the offenders (skipped,
 * not allowed). A class on the allow-list that no longer skips is also reported as stale, so the list only shrinks.
 * formerClass is the class's name before a rename (krizaka-internal#2684): the component's main and its rename PR each
 * read this one list, so it must accept both names for the window. Remove the field when the rename has landed.
 */
const names = (a) => [a.class, a.formerClass].filter(Boolean);
export function judge(component, reports, allowed = [], noIntegrationTests = []) {
  const exempt = noIntegrationTests.find((e) => e.component === component);
  const mine = allowed.filter((a) => a.component === component);
  const offenders = reports.filter((r) => r.skipped > 0 && !mine.some((a) => names(a).includes(r.name)));
  const stale = mine.filter((a) => !reports.some((r) => names(a).includes(r.name) && r.skipped > 0));
  return {
    classes: reports.length,
    tests: reports.reduce((sum, r) => sum + r.tests, 0),
    skipped: reports.reduce((sum, r) => sum + r.skipped, 0),
    offenders,
    stale,
    // A component with no integration test at all is declared (with its reason), never inferred: an empty report
    // directory on any other component still means nothing ran. A declared component that gains one is stale.
    nothingRan: reports.length === 0 && !exempt,
    staleExemption: exempt && reports.length > 0 ? exempt : undefined,
  };
}

export function load(dir) {
  return existsSync(dir) ? reportFiles(dir).map((f) => readReport(readFileSync(f, "utf8"))) : [];
}
