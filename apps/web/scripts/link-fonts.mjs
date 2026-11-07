#!/usr/bin/env node
/**
 * Puts the faces of @krizaka/fonts where `next/font/local` can read them: src/app/_fonts/ (git-ignored, never committed).
 * `next/font/local` takes a literal path relative to the file, and this app is built in two layouts — standalone (App Platform:
 * node_modules beside src/) and inside the Orazaka npm workspace (CI: node_modules hoisted two levels up) — so no literal
 * `../…/node_modules/…` is right in both. The package is resolved the Node way and its faces are copied. Runs as
 * `postinstall` and before every build.
 */
import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** The two faces of a product (Krizaka UI 2027, ADR-006): Atkinson Hyperlegible Next for the text and Plus Jakarta Sans for the titles (`--kz-font-product-display`). Young Serif and Courier Prime are the notebook's, for krizaka.com, the blog and ui.krizaka.com. */
export const FACES = ["atkinson-hyperlegible-next-latin-wght.woff2", "plus-jakarta-sans-latin-wght.woff2"];

/** Copies the faces of the package at `packageDir` into `target`; returns the files written. Throws when one is missing. */
export function linkFonts(packageDir, target) {
  mkdirSync(target, { recursive: true });
  return FACES.map((face) => {
    const from = path.join(packageDir, "files", "web", face);
    if (!existsSync(from)) throw new Error(`@krizaka/fonts has no ${face} (looked in ${path.dirname(from)})`);
    const to = path.join(target, face);
    copyFileSync(from, to);
    return to;
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const here = path.dirname(fileURLToPath(import.meta.url));
  const packageDir = path.dirname(createRequire(import.meta.url).resolve("@krizaka/fonts/package.json"));
  const written = linkFonts(packageDir, path.join(here, "..", "src", "app", "_fonts"));
  console.error(`fonts: ${written.length} faces of @krizaka/fonts in src/app/_fonts`);
}
