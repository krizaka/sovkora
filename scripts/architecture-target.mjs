#!/usr/bin/env node
// The executable C2 target of the rebuild (krizaka-internal#2927, docs/architecture/target-modules.md).
// Source-level target rules across the three toolchains, run without a compiling reactor so they report
// today: the CI job `architecture-target` is NOT required until the rebuild lands (#2436), so a violation
// here is "the target is not reached yet", not a build break. The bytecode-level ArchUnit target rules
// (layer rings, sharing tiers) live at engine/sovkora-engine-architecture and run once a slice compiles.
//
// Checks:
//   Java   — every com.krizaka.sovkora.* file sits under a maven module folder, and each module's pom.xml
//            depends only on Sovkora modules listed BEFORE it in modules.json (build order, no forward/cyclic dep).
//   TS     — no app under apps/* imports another app's sources (cross-app boundary).
//   Python — workers/media imports no services/, engine/ or contracts/ code (external process, wire contract only).

import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, dirname, sep } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const MAX_EXAMPLES = 15;
const SKIP_DIRS = new Set(["node_modules", "dist", ".next", "target", ".venv", "__pycache__", ".git", "archunit_store"]);

const modules = JSON.parse(readFileSync(join(ROOT, "modules.json"), "utf8")).modules;
const order = new Map(modules.map((m, i) => [m.name, i]));

/** Walk a directory, yielding files whose name matches `test`. */
function walk(dir, test, out = []) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    if (e.isDirectory()) {
      if (!SKIP_DIRS.has(e.name)) walk(join(dir, e.name), test, out);
    } else if (test(e.name)) {
      out.push(join(dir, e.name));
    }
  }
  return out;
}

const read = (f) => readFileSync(f, "utf8");
const rel = (f) => relative(ROOT, f);

// ---- Java -----------------------------------------------------------------
function checkJava() {
  const violations = [];
  const mavenModules = modules.filter((m) => m.kind === "maven");

  // Map every Sovkora artifactId to the module whose folder owns its pom.xml.
  const artifactToModule = new Map();
  for (const m of mavenModules) {
    for (const pom of walk(join(ROOT, m.path), (n) => n === "pom.xml")) {
      const id = read(pom).match(/<artifactId>(sovkora-[^<]+)<\/artifactId>/);
      if (id) artifactToModule.set(id[1], m.name);
    }
  }

  for (const m of mavenModules) {
    const base = join(ROOT, m.path);

    // Package root: every .java under a maven module declares com.krizaka.sovkora.*
    for (const java of walk(base, (n) => n.endsWith(".java"))) {
      const pkg = read(java).match(/^\s*package\s+([\w.]+)\s*;/m);
      if (pkg && !pkg[1].startsWith("com.krizaka.sovkora")) {
        violations.push(`${rel(java)}: package ${pkg[1]} is not under com.krizaka.sovkora`);
      }
    }

    // Build order: a module's real dependencies (not its BOM) are only on Sovkora modules before it.
    for (const pom of walk(base, (n) => n === "pom.xml")) {
      // Drop <dependencyManagement> (the BOM lists every module) — only actual <dependency> blocks count.
      const xml = read(pom).replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, "");
      for (const dep of xml.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
        const id = dep[1].match(/<artifactId>(sovkora-[^<]+)<\/artifactId>/);
        if (!id) continue;
        const depModule = artifactToModule.get(id[1]);
        if (!depModule || depModule === m.name) continue; // unknown artifact or intra-module (api+client+service)
        if (order.get(depModule) >= order.get(m.name)) {
          violations.push(`${rel(pom)}: module '${m.name}' depends on '${depModule}' which is not before it in modules.json`);
        }
      }
    }
  }
  return violations;
}

// ---- TypeScript -----------------------------------------------------------
const TS = /\.(ts|tsx|js|jsx|mjs|cjs)$/;
const APPS = modules.filter((m) => m.path.startsWith("apps/")).map((m) => m.path.slice("apps/".length));

function checkTs() {
  const violations = [];
  const appsDir = join(ROOT, "apps");
  for (const app of APPS) {
    const otherApps = APPS.filter((a) => a !== app);
    if (otherApps.length === 0) continue;
    const crossApp = new RegExp(`(?:from\\s+|require\\(\\s*|import\\(\\s*)['"][^'"]*apps/(${otherApps.join("|")})/`);
    for (const file of walk(join(appsDir, app), (n) => TS.test(n))) {
      const text = read(file);
      for (const line of text.split("\n")) {
        const hit = line.match(crossApp);
        if (hit) violations.push(`${rel(file)}: app '${app}' imports app '${hit[1]}' (cross-app boundary)`);
      }
    }
  }
  return violations;
}

// ---- Python ---------------------------------------------------------------
function checkPython() {
  const violations = [];
  const media = modules.find((m) => m.name === "worker-media");
  if (!media) return violations;
  const forbidden = /^\s*(?:from|import)\s+(services|engine|contracts)\b/;
  for (const py of walk(join(ROOT, media.path), (n) => n.endsWith(".py"))) {
    const lines = read(py).split("\n");
    lines.forEach((line, i) => {
      if (forbidden.test(line)) {
        violations.push(`${rel(py)}:${i + 1}: worker-media imports ${line.trim()} (must speak only the wire contract)`);
      }
    });
  }
  return violations;
}

// ---- Report ---------------------------------------------------------------
const results = [
  ["Java  (package root + module build order)", checkJava()],
  ["TS    (no cross-app import)", checkTs()],
  ["Python (worker-media wire-contract only)", checkPython()],
];

let total = 0;
for (const [label, violations] of results) {
  total += violations.length;
  if (violations.length === 0) {
    console.log(`✓ ${label}: reached`);
    continue;
  }
  console.log(`✗ ${label}: ${violations.length} not-yet-reached`);
  for (const v of violations.slice(0, MAX_EXAMPLES)) console.log(`    ${v}`);
  if (violations.length > MAX_EXAMPLES) console.log(`    … and ${violations.length - MAX_EXAMPLES} more`);
}

if (total > 0) {
  console.log(`\n${total} source-level target rule(s) not reached. This is the structural floor; it is advisory until the rebuild`);
  console.log(`lands (#2436), then required. The deep bytecode rules (ArchUnit layer rings, sharing tiers) run in the Java reactor job.`);
  process.exit(1);
}
console.log("\nAll source-level target rules reached (the structural floor from sovkora#621). The deep bytecode rules");
console.log("(ArchUnit layer rings, sharing tiers) run in the Java reactor job and turn this check required at the end (#2436).");
