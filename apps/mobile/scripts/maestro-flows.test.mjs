// node --test scripts/maestro-flows.test.mjs — the Maestro flows CI runs (mobile-e2e.yml) are the ones tagged `ci`, and
// they need no backend and no account; every other flow is tagged `manual`, never left untagged (it would run in CI and
// fail on a missing secret).
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";

const dir = new URL("../.maestro/", import.meta.url);
const flows = readdirSync(dir)
  .filter((name) => name.endsWith(".yaml"))
  .map((name) => ({ name, text: readFileSync(new URL(name, dir), "utf8") }));
const tags = (text) => {
  const header = text.split(/^---$/m)[0];
  const block = header.match(/^tags:\n((?:\s+- .+\n?)+)/m);
  return block
    ? block[1]
        .split("\n")
        .map((l) => l.trim().replace(/^- /, ""))
        .filter(Boolean)
    : [];
};

test("every flow is tagged ci or manual", () => {
  for (const { name, text } of flows) {
    const t = tags(text);
    assert.ok(
      t.includes("ci") !== t.includes("manual"),
      `${name}: exactly one of ci / manual (${t.join(", ") || "none"})`,
    );
  }
});

test("a ci flow needs no account and no platform: no variable, no Studio, no Jobs", () => {
  const ci = flows.filter(({ text }) => tags(text).includes("ci"));
  assert.ok(ci.length > 0, "at least one flow runs in CI");
  for (const { name, text } of ci) {
    assert.doesNotMatch(text, /\$\{[A-Z_]+\}/, `${name} reads a variable (an account): tag it manual`);
    assert.doesNotMatch(text, /studio-|"Jobs"|"Chat"/, `${name} reaches past sign-in: tag it manual`);
  }
});

test("the workflow calls the org's reusable mobile-e2e at a pinned commit, for this app", () => {
  const wf = readFileSync(new URL("../.github/workflows/mobile-e2e.yml", import.meta.url), "utf8");
  assert.match(wf, /uses: krizaka\/\.github\/\.github\/workflows\/mobile-e2e\.yml@[0-9a-f]{40}/);
  assert.match(wf, /app-id: com\.krizaka\.orazaka/);
  assert.match(wf, /^\s+mobile-e2e:$/m, "the job is named mobile-e2e, so the check is `mobile-e2e / gate`");
});
