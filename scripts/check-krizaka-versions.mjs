#!/usr/bin/env node
/** Fails when sovkora-parent inherits or imports a com.krizaka version that no publishing repository carries (lib/krizaka-versions.mjs). */
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { claims, explain, onRepository, properties, publishedPom, repositories, unprovided } from "./lib/krizaka-versions.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

export async function check(repos = repositories()) {
  const parent = readFileSync(join(ROOT, "build/sovkora-parent/pom.xml"), "utf8");
  const version = properties(parent)["krizaka.version"];
  const bom = version ? await publishedPom({ group: "com.krizaka", artifactId: "krizaka-bom", version }, repos) : null;
  return unprovided(claims({ bom, parent }), (c) => onRepository(c, repos));
}

const missing = await check();
for (const c of missing) console.error(`::error::${explain(c, repositories())}`);
if (missing.length) process.exit(1);
console.log("Every com.krizaka version Sovkora inherits or imports is on a repository krizaka-java publishes to.");
