/** @jest-environment node */
import fs from "node:fs";
import path from "node:path";

/**
 * Search engines and AI assistants repeat what the public pages say. Sovkora is local by design, but no Loi 25 / GDPR
 * pack exists and no legal conformity is certified: no public text may say « compliant », « conforme » or « Conformité
 * validée » next to a law. Read from the sources of the pages (landing, desktop page), so the guard is the source itself.
 */
const SRC = path.resolve(__dirname, "..");
const PUBLIC_SOURCES = [
  "features/landing",
  "core/context/translations.desktop.ts",
  "core/context/translations.pricing.ts",
  "app/_seo/seo.ts",
  "app/layout.tsx",
];

function files(entry: string): string[] {
  const full = path.join(SRC, entry);
  if (fs.statSync(full).isFile()) return [full];
  return fs
    .readdirSync(full, { recursive: true, encoding: "utf8" })
    .filter((f) => /\.(tsx?|json)$/.test(f) && !f.includes("__tests__"))
    .map((f) => path.join(full, f));
}

const LAW = "(loi\\s*25|law\\s*25|rgpd|gdpr|hipaa|pipeda)";
const CLAIM = new RegExp(
  `${LAW}[^\\n]{0,160}(compliant|conforme|conformit[eé]|certified|certifi[eé])|(compliant|conforme|conformit[eé]\\s+valid[eé]e)[^\\n]{0,160}${LAW}`,
  "i",
);

describe("public texts claim no legal conformity", () => {
  it.each(PUBLIC_SOURCES.flatMap(files))("%s", (file) => {
    const offending = fs
      .readFileSync(file, "utf8")
      .split("\n")
      .filter((line) => CLAIM.test(line));
    expect(offending).toEqual([]);
  });

  it("never calls the product decentralised", () => {
    const all = PUBLIC_SOURCES.flatMap(files)
      .map((f) => fs.readFileSync(f, "utf8"))
      .join("\n");
    expect(all).not.toMatch(/d[ée]centralis|decentraliz/i);
  });
});
