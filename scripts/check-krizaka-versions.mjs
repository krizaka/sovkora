#!/usr/bin/env node
/** Fails when sovkora-parent inherits or imports a com.krizaka version that Maven Central does not carry (lib/krizaka-versions.mjs). */
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { centralPom, claims, explain, properties, unprovided } from "./lib/krizaka-versions.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export async function check() {
  const parent = readFileSync(join(ROOT, "build/sovkora-parent/pom.xml"), "utf8");
  const version = properties(parent)["krizaka.version"];
  const bom = version ? await centralPom({ group: "com.krizaka", artifactId: "krizaka-bom", version }) : null;
  return unprovided(claims({ bom, parent }));
}

const missing = await check();
for (const c of missing) console.error(`::error::${explain(c)}`);
if (missing.length) process.exit(1);
console.log("Every com.krizaka version Sovkora inherits or imports is on Maven Central.");
