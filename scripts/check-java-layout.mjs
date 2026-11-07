#!/usr/bin/env node
/** Fails when a Java module breaks the naming convention of ADR-029 (N1 to N4, scripts/lib/java-layout.mjs), and says which rule. */
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { violations } from "./lib/java-layout.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const tracked = execFileSync("git", ["ls-files"], { cwd: ROOT, encoding: "utf8" }).split("\n").filter(Boolean);
const poms = tracked.filter((f) => f.endsWith("/pom.xml"));
const packagesOf = (dir, kind) =>
  [...new Set(tracked.filter((f) => f.startsWith(`${dir}/src/${kind}/java/`) && f.endsWith(".java"))
    .map((f) => f.slice(`${dir}/src/${kind}/java/`.length).split("/").slice(0, -1).join(".")))];
const modules = poms.map((pom) => {
  const dir = pom.slice(0, -"/pom.xml".length);
  return { dir, pom: readFileSync(resolve(ROOT, pom), "utf8"), main: packagesOf(dir, "main"), test: packagesOf(dir, "test") };
});
if (modules.length < 20) {
  console.error(`::error::only ${modules.length} Maven modules found: the reactor is not where this script looks`);
  process.exit(1);
}
const found = violations(modules);
for (const v of found) console.error(`::error::[${v.rule}] ${v.module}: ${v.message} (ADR-029)`);
if (found.length) process.exit(1);
console.log(`The ${modules.length} Maven modules follow the Java naming convention (ADR-029 N1 to N4).`);
