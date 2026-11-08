#!/usr/bin/env node
// MANUAL TOOL AND FAN-OUT STEP (ADR-026): krizaka/.github `brand-icons-fanout.mjs` runs this exact path when the brand changes (do not move or rename it). Owner: sovkora-web lane.
// Re-downloads the brand icons of sovkora from the Krizaka CDN, byte for byte (never redrawn).
// They are generated from @krizaka/ui by krizaka-ui `tools/export-brand.mjs`; `brand/manifest.json` lists each file's SHA-256.
//   node scripts/sync-brand-icons.mjs          # download and write
//   node scripts/sync-brand-icons.mjs --check  # only compare with the CDN manifest (exit 1 on drift)
// Dependency-free. Guarded by the brand-icons test (committed icon sha256 = CDN manifest entry).
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const CDN = "https://krizaka-assets.b-cdn.net";
export const BRAND = "sovkora";
/** CDN file (under brand/<brand>/) -> committed path, relative to the repository root. */
export const ICONS = {
  "favicon.svg": "public/favicon.svg",
  "favicon-32.png": "public/favicon-32.png",
  "apple-touch-icon.png": "public/apple-touch-icon.png",
  "icon-192.png": "public/icon-192.png",
  "icon-512.png": "public/icon-512.png",
};
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export const sha256 = (buf) => createHash("sha256").update(buf).digest("hex");

export async function fetchRetry(url, tries = 5) {
  let last;
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url);
      if (res.ok) return Buffer.from(await res.arrayBuffer());
      last = new Error(`${url}: HTTP ${res.status}`);
    } catch (e) {
      last = e;
    }
    await new Promise((r) => setTimeout(r, 500 * 2 ** i));
  }
  throw last;
}

/** { "brand/<brand>/<file>": sha256 } from the CDN manifest. */
export async function cdnHashes() {
  const manifest = JSON.parse((await fetchRetry(`${CDN}/brand/manifest.json`)).toString("utf8"));
  return Object.fromEntries(manifest.files.map((f) => [f.path, f.sha256]));
}

export async function main(check = process.argv.includes("--check")) {
  const hashes = await cdnHashes();
  let drift = 0;
  for (const [file, dest] of Object.entries(ICONS)) {
    const key = `brand/${BRAND}/${file}`;
    if (!hashes[key]) throw new Error(`${key} is not in the CDN manifest`);
    const target = path.join(ROOT, dest);
    if (check) {
      const have = await readFile(target).then(sha256, () => "missing");
      if (have !== hashes[key]) {
        drift++;
        console.error(`drift: ${dest}`);
      }
      continue;
    }
    const buf = await fetchRetry(`${CDN}/${key}`);
    if (sha256(buf) !== hashes[key]) throw new Error(`${key}: downloaded bytes do not match the manifest`);
    await mkdir(path.dirname(target), { recursive: true });
    await writeFile(target, buf);
    process.stdout.write(`${dest}  <-  ${key}\n`);
  }
  if (drift) process.exit(1);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
