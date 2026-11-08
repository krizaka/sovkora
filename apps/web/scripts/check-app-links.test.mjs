import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { isNotFoundOnly, nextRoutes } from "./check-app-links.mjs";

const NOT_FOUND = `import { notFound } from "next/navigation";\n\n/** An address under a language that no page matches. */\nexport default function UnmatchedPage() {\n  notFound();\n}\n`;

function tree(files) {
  const root = mkdtempSync(join(tmpdir(), "app-links-"));
  for (const [path, source] of Object.entries(files)) {
    const full = join(root, path);
    mkdirSync(join(full, ".."), { recursive: true });
    writeFileSync(full, source);
  }
  return root;
}

describe("a catch-all that only answers 404", () => {
  it("is recognised, comments and async or not", () => {
    assert.equal(isNotFoundOnly(NOT_FOUND), true);
    assert.equal(
      isNotFoundOnly(`import { notFound } from "next/navigation";\nexport default async function P() { notFound() }`),
      true,
    );
    assert.equal(isNotFoundOnly(`export default function P() { return <p>hi</p>; }`), false);
    assert.equal(
      isNotFoundOnly(`import { notFound } from "next/navigation";\nexport default function P() { log(); notFound(); }`),
      false,
    );
  });

  it("is no route of the app — a real catch-all still is", () => {
    const only = tree({
      "[locale]/watch/[id]/page.tsx": "export default function P() { return null; }",
      "[locale]/[...rest]/page.tsx": NOT_FOUND,
    });
    assert.deepEqual(nextRoutes(only), ["/[locale]/watch/[id]"]);
    const real = tree({ "docs/[...slug]/page.tsx": "export default function P() { return <p>doc</p>; }" });
    assert.deepEqual(nextRoutes(real), ["/docs/[...slug]"]);
  });

  it("is ignored only when it is a catch-all, never a named segment", () => {
    const named = tree({ "gone/[id]/page.tsx": NOT_FOUND });
    assert.deepEqual(nextRoutes(named), ["/gone/[id]"]);
  });
});
