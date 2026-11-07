import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";
import {
  loginVerdict,
  report,
  rollbackVerdict,
  toolPathVerdict,
  toolVerdict,
  webVerdict,
} from "./videos-preflight.mjs";

const knowledge = {
  name: "searchKnowledge",
  inputSchema: JSON.stringify({ type: "object", properties: { query: { type: "string" } }, required: ["query"] }),
};
const poster = { name: "analyzePoster", inputSchema: "{}" };

describe("the tool the recorded prompt needs", () => {
  it("passes when it is offered with a required string query", () => {
    assert.equal(toolVerdict([poster, knowledge], "searchKnowledge").ok, true);
  });
  it("fails, naming what is offered, when it is not registered (the 2026-10-29 statute lookup)", () => {
    const v = toolVerdict([poster], "lookupStatute");
    assert.equal(v.ok, false);
    assert.match(v.message, /lookupStatute is not offered.*analyzePoster/);
  });
  it("fails on an empty list, a non-array and a tool without the query argument", () => {
    assert.equal(toolVerdict([], "searchKnowledge").ok, false);
    assert.equal(toolVerdict({ error: 1 }, "searchKnowledge").ok, false);
    assert.equal(toolVerdict([{ name: "searchKnowledge", inputSchema: "{}" }], "searchKnowledge").ok, false);
    assert.equal(toolVerdict([{ name: "searchKnowledge", inputSchema: "not json" }], "searchKnowledge").ok, false);
  });
});

describe("the persona sign-ins", () => {
  it("needs HTTP 200 and a token on the edge", () => {
    assert.equal(loginVerdict(200, true).ok, true);
    assert.equal(loginVerdict(500, false).ok, false);
    assert.equal(loginVerdict(200, false).ok, false);
  });
  it("needs a session for the persona on the web (a booting core gives none)", () => {
    assert.equal(webVerdict(200, { user: { email: "Eric@example.com" } }, "eric@example.com").ok, true);
    assert.equal(webVerdict(200, {}, "eric@example.com").ok, false);
    assert.equal(webVerdict(500, null, "eric@example.com").ok, false);
  });
});

describe("the rollback guard", () => {
  const d = (id, created_at, cause, phase) => ({ id, created_at, cause, phase });
  it("passes on a manual ACTIVE deployment", () => {
    assert.equal(rollbackVerdict([d("f6acdeb5-0", "2026-10-29T15:50:09Z", "manual", "ACTIVE")], true).ok, true);
  });
  it("fails on an automated rollback and on a failed deployment after the active one", () => {
    assert.equal(
      rollbackVerdict(
        [d("a", "2026-10-29T15:22:16Z", 'automated rollback after failed deployment of "x"', "ACTIVE")],
        false,
      ).ok,
      false,
    );
    assert.equal(
      rollbackVerdict(
        [d("e", "2026-10-29T16:00:00Z", "manual", "ERROR"), d("a", "2026-10-29T15:50:09Z", "manual", "ACTIVE")],
        false,
      ).ok,
      false,
    );
    assert.equal(rollbackVerdict([d("s", "2026-10-29T15:00:00Z", "manual", "SUPERSEDED")], false).ok, false);
  });
  it("is not verified without DigitalOcean access, and refused when required", () => {
    assert.equal(rollbackVerdict(null, false).ok, true);
    assert.match(rollbackVerdict(null, false).message, /NOT VERIFIED/);
    assert.equal(rollbackVerdict(null, true).ok, false);
  });
});

describe("the report", () => {
  it("is green only when every check is", () => {
    assert.equal(report({ a: { ok: true, message: "x" }, b: { ok: true, message: "y" } }).ok, true);
    const r = report({ a: { ok: true, message: "x" }, b: { ok: false, message: "y" } });
    assert.equal(r.ok, false);
    assert.deepEqual(r.lines, ["ok   a: x", "FAIL b: y"]);
  });
});

describe("the download videos workflow runs the preflight before any paid step", () => {
  const code = readFileSync(new URL("../.github/workflows/download-videos.yml", import.meta.url), "utf8").replace(
    /^\s*#.*$/gm,
    "",
  );
  it("has a preflight job that the recording needs", () => {
    assert.match(code, /preflight:/);
    assert.match(code, /node scripts\/videos-preflight\.mjs/);
    assert.match(code, /needs: \[preflight\]/);
    assert.ok(code.indexOf("preflight:") < code.indexOf("showcase-web:"));
  });
});

describe("the free proof that a streamed turn emits a tool-call event", () => {
  const proof = "2026-10-29T18:00:00Z";
  it("is green when CI on main is green and dev was deployed after the proof", () => {
    assert.equal(
      toolPathVerdict(
        { ciConclusion: "success", proofCommittedAt: proof, coreDeployedAt: "2026-10-29T19:00:00Z" },
        true,
      ).ok,
      true,
    );
  });
  it("is red when the proof is not on main, or main's CI is not green", () => {
    assert.equal(
      toolPathVerdict({ ciConclusion: "success", proofCommittedAt: null, coreDeployedAt: null }, false).ok,
      false,
    );
    assert.equal(
      toolPathVerdict(
        { ciConclusion: "failure", proofCommittedAt: proof, coreDeployedAt: "2026-10-29T19:00:00Z" },
        false,
      ).ok,
      false,
    );
    assert.equal(
      toolPathVerdict({ ciConclusion: null, proofCommittedAt: proof, coreDeployedAt: "2026-10-29T19:00:00Z" }, false)
        .ok,
      false,
    );
  });
  it("is red when dev's core is older than the proof (the paid runs ran on builds made before the fixes)", () => {
    const v = toolPathVerdict(
      { ciConclusion: "success", proofCommittedAt: proof, coreDeployedAt: "2026-10-29T17:00:00Z" },
      false,
    );
    assert.equal(v.ok, false);
    assert.match(v.message, /redeploy first/);
  });
  it("reports an unreadable deploy time, and refuses it with --require-tool-proof", () => {
    const facts = { ciConclusion: "success", proofCommittedAt: proof, coreDeployedAt: null };
    assert.equal(toolPathVerdict(facts, false).ok, true);
    assert.match(toolPathVerdict(facts, false).message, /NOT VERIFIED/);
    assert.equal(toolPathVerdict(facts, true).ok, false);
  });
});
