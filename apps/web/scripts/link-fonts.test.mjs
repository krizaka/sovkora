import assert from "node:assert/strict";
import { existsSync, mkdtempSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

import { FACES, linkFonts } from "./link-fonts.mjs";

const fakePackage = (faces) => {
  const dir = mkdtempSync(path.join(tmpdir(), "fonts-"));
  mkdirSync(path.join(dir, "files", "web"), { recursive: true });
  for (const face of faces) writeFileSync(path.join(dir, "files", "web", face), `face:${face}`);
  return dir;
};

test("copies every face next/font/local reads", () => {
  const target = path.join(mkdtempSync(path.join(tmpdir(), "app-")), "_fonts");
  const written = linkFonts(fakePackage(FACES), target);
  assert.equal(written.length, FACES.length);
  for (const face of FACES) assert.equal(readFileSync(path.join(target, face), "utf8"), `face:${face}`);
});

test("says which face is missing instead of building without it", () => {
  const target = path.join(mkdtempSync(path.join(tmpdir(), "app-")), "_fonts");
  assert.throws(
    () => linkFonts(fakePackage(FACES.slice(1)), target),
    /has no atkinson-hyperlegible-next-latin-wght\.woff2/,
  );
  assert.equal(existsSync(path.join(target, FACES[0])), false);
});

test("the layout names the same files the script copies, relative to itself", () => {
  const layout = readFileSync(new URL("../src/app/layout.tsx", import.meta.url), "utf8");
  for (const face of FACES) assert.match(layout, new RegExp(`"\\./_fonts/${face.replace(".", "\\.")}"`));
  assert.doesNotMatch(layout, /node_modules/);
});

test("a product wears Krizaka UI 2027: the text sans and Plus Jakarta Sans for titles, never the notebook's faces (Young Serif, Courier Prime)", () => {
  assert.deepEqual(FACES, ["atkinson-hyperlegible-next-latin-wght.woff2", "plus-jakarta-sans-latin-wght.woff2"]);
  const css = readFileSync(new URL("../src/app/globals.css", import.meta.url), "utf8");
  assert.match(
    css,
    /html\[data-surface="product"\]\s*\{[^}]*--kz-font-product-display:\s*var\(--font-product-display\)/,
  );
  assert.match(
    readFileSync(new URL("../src/app/layout.tsx", import.meta.url), "utf8"),
    /variable: "--font-product-display"/,
  );
  const layout = readFileSync(new URL("../src/app/layout.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(layout, /young-serif|courier-prime/);
  assert.match(layout, /<html[^>]*data-surface="product"/);
});
