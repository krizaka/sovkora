/** @jest-environment node */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { llmsFullText, llmsText } from "@/app/_seo/seo";

// NAV-INDEP: once deployed, Sovkora is an independent product — no link to, nor mention of, another product's domain.
// Krizaka is its publisher and appears in the footer ("A Krizaka product" -> www.krizaka.com), nothing else.
const OTHER_PRODUCTS = /(?:[a-z0-9-]+\.)*(?:yomizaka|akinaza)\.com/i;

function* walk(dir: string): Generator<string> {
  if (!existsSync(dir)) return;
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      if (name === "node_modules" || name === "__tests__" || name === "e2e") continue;
      yield* walk(path);
    } else if (!/\.(test|spec)\.[tj]sx?$/.test(name) && /\.(tsx?|jsx?|mjs|json|md|mdx|txt|html|css)$/.test(name))
      yield path;
  }
}

describe("Sovkora is independent of the other products", () => {
  it("no source, content, public file nor built page names another product's domain", () => {
    const roots = ["src", "content", "public", ".next/server/app"].map((r) => join(process.cwd(), r));
    const offenders: string[] = [];
    for (const root of roots) {
      for (const file of walk(root)) {
        const m = readFileSync(file, "utf8").match(OTHER_PRODUCTS);
        if (m) offenders.push(`${file.replace(process.cwd() + "/", "")}: ${m[0]}`);
      }
    }
    expect(offenders).toEqual([]);
  });

  it("llms.txt and llms-full.txt name the publisher only, no other product", () => {
    for (const text of [llmsText(), llmsFullText()]) {
      expect(text).not.toMatch(OTHER_PRODUCTS);
      expect(text).toContain("https://www.krizaka.com");
    }
  });
});
