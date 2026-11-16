import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { appendFileSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { callHandlers, coverageTable, discoverPacks, judge, orphanFixtures } from "./conformance/suite.mjs";

const root = fileURLToPath(new URL("..", import.meta.url));
const ratchet = JSON.parse(readFileSync(new URL("../conformance-ratchet.json", import.meta.url), "utf8"));
const exceptions = new Map(Object.entries(ratchet.exceptions));

/** The exceptions recorded at a commit, or null when it has none. */
function exceptionsAt(base) {
  const files = execFileSync("git", ["ls-tree", "--name-only", base, "--", "conformance-ratchet.json"], {
    cwd: root,
    encoding: "utf8",
  });
  if (!files.trim()) return null;
  return Object.keys(
    JSON.parse(execFileSync("git", ["show", `${base}:./conformance-ratchet.json`], { cwd: root, encoding: "utf8" }))
      .exceptions,
  );
}

const packs = discoverPacks(root);

// One handler process per pack, for the capabilities that ship a fixture.
const answers = new Map();
for (const { pack, capabilities } of packs) {
  const withFixture = capabilities.filter((c) => c.fixture);
  if (withFixture.length === 0) continue;
  answers.set(
    pack,
    callHandlers(
      root,
      pack,
      withFixture.map((c) => ({ routingKey: c.routingKey, input: c.fixture.input })),
    ),
  );
}

const rows = packs.map(({ pack, capabilities, studios }) => {
  const withFixture = capabilities.filter((c) => c.fixture);
  const proven = [];
  const missing = [];
  for (const c of capabilities) {
    const answer = c.fixture ? answers.get(pack)[withFixture.indexOf(c)] : null;
    const reason = judge(c, answer);
    (reason ? missing : proven).push({ key: c.key, reason });
  }
  return { pack, declared: capabilities.length, proven, missing, studios: studios.length };
});

const table = coverageTable(rows);
console.log(`\nPack conformance — capabilities declared vs proven on their fixtures\n\n${table}\n`);
if (process.env.GITHUB_STEP_SUMMARY)
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Pack conformance\n\n${table}\n`);

for (const { pack, capabilities } of packs) {
  for (const c of capabilities) {
    const key = `${pack}/${c.key}`;
    test(`${key} runs on its fixture, or is a recorded exception`, () => {
      const row = rows.find((r) => r.pack === pack);
      const failure = row.missing.find((m) => m.key === c.key);
      if (exceptions.has(key)) {
        assert.ok(failure, `${key}: it passes now — remove the exception from conformance-ratchet.json`);
        assert.match(exceptions.get(key), /krizaka-internal#\d+/, `${key}: an exception names its follow-up ticket`);
        return;
      }
      assert.equal(failure, undefined, `${key}: ${failure?.reason}`);
    });
  }
}

test("every manifest's declared capability is the only source of an entry (no orphan fixture)", () => {
  assert.deepEqual(orphanFixtures(root, packs), []);
});

test("an exception names a declared capability", () => {
  const declared = new Set(packs.flatMap((p) => p.capabilities.map((c) => `${p.pack}/${c.key}`)));
  for (const key of exceptions.keys()) assert.ok(declared.has(key), `stale conformance exception ${key}`);
});

test("conformance exceptions only decrease against the PR base", () => {
  const base = process.env.GOLDEN_BASE_SHA;
  if (base) assert.match(base, /^[0-9a-f]{40}$/);
  const previous = base ? exceptionsAt(base) : null;
  if (!previous) return; // the base has no ratchet yet: this change introduces it
  for (const key of exceptions.keys())
    assert.ok(previous.includes(key), `conformance exceptions may only shrink: ${key}`);
});
