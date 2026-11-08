#!/usr/bin/env node
/**
 * The Java plan of the CI (krizaka-internal#2024, ADR-018 budgets; ADR-029: one repository, one reactor).
 *
 *   node scripts/ci-plan.mjs --event pull_request [--files a,b,c | --diff HEAD^1]
 *
 * A pull request needs the reactor compiled and the tests of what it touches: the changed Maven modules and their
 * dependents (`-pl <changed> -amd`). The full reactor (every unit test and Testcontainers IT) runs on merge_group, main,
 * the nightly and dispatch, and whenever the pull request touches what every module shares (the root pom, the parent,
 * .mvn, infra, the module catalogue, this tooling, the workflow). It fails open: unknown means full.
 *
 * Prints `mode=full|changed` and `pl=<comma list>` (also appended to $GITHUB_OUTPUT).
 */
import { spawnSync } from "node:child_process";
import { appendFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { toolPath } from "./lib/tool-path.mjs";

/** What every module shares: a change here can break any of them. */
export const SHARED =
  /^(pom\.xml|build\/(pom\.xml|sovkora-parent\/)|\.mvn\/|mvnw(\.cmd)?$|infra\/|modules\.json$|scripts\/ci-plan\.mjs$|\.github\/workflows\/ci\.yml$)/;

/** The Maven module folders of the tree (every folder with a pom.xml, the root aside), longest first. */
export function modulesOf(pomPaths) {
  const dirs = pomPaths.filter((p) => p.endsWith("/pom.xml")).map((p) => p.slice(0, -"/pom.xml".length));
  return [...new Set(dirs)].sort((a, b) => b.length - a.length);
}

export function plan({ event, files, modules }) {
  if (event !== "pull_request") return { mode: "full", pl: "", reason: `event ${event}` };
  if (!files || files.length === 0) return { mode: "full", pl: "", reason: "no changed files known" };
  const touched = new Set();
  for (const f of files) {
    if (SHARED.test(f)) return { mode: "full", pl: "", reason: `shared file ${f}` };
    const m = modules.find((d) => f === d || f.startsWith(`${d}/`));
    if (m) touched.add(m);
  }
  return { mode: "changed", pl: [...touched].sort((a, b) => a.localeCompare(b)).join(","), reason: "changed modules and their dependents" };
}

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const event = arg("--event") ?? process.env.GITHUB_EVENT_NAME ?? "push";
  let files = arg("--files")?.split(",").filter(Boolean) ?? null;
  if (!files && arg("--diff")) {
    const r = spawnSync(toolPath("git"), ["diff", "--name-only", arg("--diff"), "HEAD"], { cwd: root, encoding: "utf8" });
    files = r.status === 0 ? r.stdout.split("\n").filter(Boolean) : null;
  }
  const poms = spawnSync(toolPath("git"), ["ls-files", "*pom.xml"], { cwd: root, encoding: "utf8" }).stdout.split("\n").filter(Boolean);
  const result = plan({ event, files, modules: modulesOf(poms) });
  console.log(`mode=${result.mode}\npl=${result.pl}\n# ${result.reason}`);
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `mode=${result.mode}\npl=${result.pl}\n`);
}
