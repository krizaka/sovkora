import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { discoverPacks } from "./conformance/suite.mjs";

// The conformance suite walks the packs in alphabetical order, not in UTF-16 code-unit order: a pack whose folder
// starts with a capital or an accent is not sent to the front or the back of the report (krizaka-internal#4361).
test("discoverPacks lists the packs alphabetically, whatever their case or accents", () => {
  const root = mkdtempSync(join(tmpdir(), "packs-order-"));
  try {
    for (const name of ["zeta", "Beta", "élan", "alpha", "not-a-pack"]) {
      mkdirSync(join(root, name));
      if (name !== "not-a-pack") writeFileSync(join(root, name, "pack.yaml"), `key: ${name}\n`);
    }

    assert.deepEqual(
      discoverPacks(root).map((p) => p.pack),
      ["alpha", "Beta", "élan", "zeta"],
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
