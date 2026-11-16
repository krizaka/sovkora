import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { placeholderHits } from "./check-code-names.mjs";

/** krizaka-internal#3935: `<title>Documentation — {brand}</title>` was live on /docs. */
test("a {brand} left in a rendered page is found, a filled one is not", () => {
  const dir = mkdtempSync(join(tmpdir(), "brand-"));
  try {
    const bad = join(dir, "docs.html");
    const good = join(dir, "pricing.html");
    writeFileSync(bad, "<html><head><title>Documentation — {brand}</title></head><body>ok</body></html>");
    writeFileSync(good, "<html><head><title>Pricing — Sovkora</title></head><body>{count} runs</body></html>");
    const hits = placeholderHits([
      [bad, "docs.html"],
      [good, "pricing.html"],
    ]);
    assert.equal(hits.length, 1);
    assert.match(hits[0], /^docs\.html: .*Documentation — \{brand\}/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
