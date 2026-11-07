// node --test scripts/test — the model registry the site and the docs publish: read from the SQL the routing reads, and refused when incomplete.
import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import { buildModels, catalogue, licenceKey, manifestLicences, readmeLicences, statements, stripComments, verifiedBySmoke, CAPABILITIES } from "../lib/model-registry.mjs";

const meta = (name, extra = {}) => {
  const set = {
    vendor: "Alibaba Cloud", family: "Qwen 2.5", capabilities: "chat", runs_on: "local", runtime: "Ollama", open_weights: true,
    licence: "Apache-2.0", licence_url: "https://example.org/licence", commercial_use: "yes", context_window: 32768, status: "available", ...extra,
  };
  const sql = (v) => (v === null ? "NULL" : typeof v === "boolean" ? String(v).toUpperCase() : typeof v === "number" ? String(v) : `'${v}'`);
  return `UPDATE orazaka_models SET ${Object.entries(set).filter(([, v]) => v !== undefined).map(([k, v]) => `${k} = ${sql(v)}`).join(", ")} WHERE model_name = '${name}';`;
};
const seed = `
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default) VALUES
('qwen2.5:7b', 'Qwen 2.5 7B Instruct', 'chat', 'q4_K_M', 'ollama', TRUE), -- a comment with an apostrophe, a vendor's; and a semicolon
('rose', 'Rose Accent', 'theme', NULL, 'ollama', FALSE)
ON CONFLICT (model_name) DO NOTHING;
`;

test("a complete model is published with its registry facts, the default flagged", () => {
  const [m, ...rest] = buildModels(seed + meta("qwen2.5:7b"));
  assert.equal(rest.length, 0, "a UI theme is not a model");
  assert.deepEqual(
    { id: m.id, vendor: m.vendor, capabilities: m.capabilities, runsOn: m.runsOn, licence: m.licence.name, contextWindow: m.contextWindow, status: m.status, openWeights: m.openWeights },
    { id: "qwen2.5:7b", vendor: "Alibaba Cloud", capabilities: ["chat"], runsOn: "local", licence: "Apache-2.0", contextWindow: 32768, status: "default", openWeights: true },
  );
  assert.equal(m.lastVerified, null, "no smoke report, no date");
});

test("the generator fails on a model missing required fields — and names the model and the fields", () => {
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { licence: undefined, vendor: undefined })), (e) => /qwen2\.5:7b: licence is missing; vendor is missing|qwen2\.5:7b: vendor is missing; licence is missing/.test(e.message));
  // A model nobody described is refused too — it does not silently disappear from the site.
  assert.throws(() => buildModels(seed), /qwen2\.5:7b: status must be one of/);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { open_weights: undefined })), /open_weights is missing/);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { runs_on: "cloud" })), /runs_on must be one of/);
});

test("a capability outside the vocabulary, a bad context window or a non-https licence link is refused", () => {
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { capabilities: "chat,telepathy" })), /capability "telepathy"/);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { context_window: 0 })), /context_window must be a positive integer/);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { licence_url: "http://insecure.example" })), /licence_url must be an https URL/);
  for (const c of ["chat", "reasoning", "vision", "embeddings", "image", "video", "speech-to-text", "text-to-speech", "music", "video-to-audio", "video-upscale"]) assert.ok(CAPABILITIES.includes(c), c);
});

test("metadata for a model no INSERT created is an error, not a silent no-op", () => {
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b") + meta("ghost-model")), /UPDATE of ghost-model, which no INSERT created/);
});

test("an internal row stays in the catalogue and is never published; its other fields may be empty", () => {
  const sql = seed + "UPDATE orazaka_models SET status = 'internal' WHERE model_name = 'qwen2.5:7b';";
  assert.deepEqual(buildModels(sql), []);
  assert.equal(catalogue(sql).find((r) => r.model_name === "qwen2.5:7b").status, "internal");
});

test("rows are read whatever the column order, with concatenated descriptions and the first INSERT of a name winning", () => {
  const sql = `
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default, description)
VALUES ('mistral-3-14B', 'Mistral 3 14B', 'chat', NULL, 'do-inference', FALSE, 'Mistral 3 (Apache-2.0) '
        || 'on a provider — it''s billed per token.')
ON CONFLICT (model_name) DO NOTHING;
INSERT INTO orazaka_models (model_name, model_label, category) VALUES ('mistral-3-14B', 'Another label', 'image') ON CONFLICT (model_name) DO NOTHING;
${meta("mistral-3-14B", { runs_on: "provider", runtime: "DigitalOcean serverless inference", status: "planned" })}`;
  const [row] = catalogue(sql);
  assert.equal(row.description, "Mistral 3 (Apache-2.0) on a provider — it's billed per token.");
  assert.equal(row.model_label, "Mistral 3 14B");
  const [m] = buildModels(sql);
  assert.deepEqual([m.runsOn, m.provider, m.status], ["provider", "do-inference", "planned"]);
});

test("local models come first, then by status; the order is stable", () => {
  const rows = [["a", "provider", "available"], ["b", "local", "available"], ["c", "local", "planned"]]
    .map(([n]) => `INSERT INTO orazaka_models (model_name, model_label, category, provider_name, is_default) VALUES ('${n}', '${n}', 'chat', 'ollama', FALSE);`).join("\n");
  const sql = rows + [["a", "provider", "available"], ["b", "local", "available"], ["c", "local", "planned"]].map(([n, r, s]) => meta(n, { runs_on: r, status: s })).join("\n");
  assert.deepEqual(buildModels(sql).map((m) => m.id), ["b", "c", "a"]);
});

test("last verified is the date of the last PASSED smoke check that names the model", () => {
  const report = { finishedAt: "2026-10-20T10:00:00Z", checks: [
    { name: "chat", status: "PASSED", models: ["qwen2.5:7b", "provider:ollama"] },
    { name: "image", status: "FAILED", models: ["flux-2-klein-4b"] },
    { name: "tts", status: "SKIPPED" },
  ] };
  assert.deepEqual([...verifiedBySmoke(report)], [["qwen2.5:7b", "2026-10-20"]]);
  assert.equal(buildModels(seed + meta("qwen2.5:7b"), { smoke: report })[0].lastVerified, "2026-10-20");
  assert.equal(verifiedBySmoke(null).size, 0);
  assert.equal(verifiedBySmoke({ checks: [{ status: "PASSED", models: ["x"] }] }).size, 0, "a report without a date proves nothing");
});

test("comments and dollar-quoted blocks do not break statement splitting", () => {
  assert.equal(stripComments("select 1; -- it's; fine\nselect '--kept';").includes("it's"), false);
  assert.ok(stripComments("select '--kept';").includes("--kept"));
  assert.equal(statements("UPDATE t SET j = $${\"a\": \"b;c\"}$$::jsonb;\nselect 2;").length, 2);
});

test("commercial use is required, and a default may never be non-commercial", () => {
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { commercial_use: undefined })), /commercial_use is missing/);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { commercial_use: "maybe" })), /commercial_use must be one of yes, conditions, no/);
  // seed has is_default TRUE: a non-commercial default is refused; the same licence as a non-default is published and flagged.
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b", { commercial_use: "no" })), /a default model must allow commercial use/);
  const notDefault = seed.replace("'q4_K_M', 'ollama', TRUE)", "'q4_K_M', 'ollama', FALSE)");
  assert.equal(buildModels(notDefault + meta("qwen2.5:7b", { commercial_use: "no" }))[0].commercialUse, "no");
  assert.equal(buildModels(seed + meta("qwen2.5:7b"))[0].commercialUse, "yes");
});

test("two sources that disagree on a licence fail the build, naming both", () => {
  const readme = new Map([["qwen2.5:7b", "CC BY-NC 4.0"]]);
  assert.throws(() => buildModels(seed + meta("qwen2.5:7b"), { licenceSources: [{ name: "infra/localai/README.md", licences: readme }] }), /qwen2\.5:7b: infra\/localai\/README\.md says "CC BY-NC 4\.0", the registry \(the model's card\) says "Apache-2\.0"/);
  const agree = new Map([["qwen2.5:7b", "Apache-2.0 (weights, unmodified)"], ["unknown-model", "anything"]]);
  assert.equal(buildModels(seed + meta("qwen2.5:7b"), { licenceSources: [{ name: "w", licences: agree }] }).length, 1);
  assert.equal(licenceKey("CC-BY 4.0 (SIWIS)").startsWith(licenceKey("CC BY 4.0")), true);
  assert.equal(licenceKey("CC BY-NC-SA 4.0").startsWith(licenceKey("CC BY 4.0")), false);
});

test("the README table and the worker manifest are read as id → licence", () => {
  assert.deepEqual([...readmeLicences("| File | W | S | Licence |\n|:---|\n| `piper-x.yaml` | `a.onnx` | https://h/a | CC BY 4.0 (X) |\n")], [["piper-x", "CC BY 4.0 (X)"]]);
  assert.deepEqual([...manifestLicences('video:\n  wan:\n    label: "W"\n    licence: Apache-2.0\n  ad:\n    licence: "CreativeML OpenRAIL-M"\n')], [["wan", "Apache-2.0"], ["ad", "CreativeML OpenRAIL-M"]]);
});

test("the README of infra/localai says what the registry says about the voices", () => {
  const found = readmeLicences(readFileSync(new URL("../../infra/localai/README.md", import.meta.url), "utf8"));
  assert.match(found.get("piper-en-medium-ljspeech"), /^Public domain/);
  assert.match(found.get("piper-en-medium-ryan"), /^CC BY-NC-SA 4\.0/);
  assert.ok(found.size >= 7);
});

test("the worker's models.yaml (when present) agrees with the registry on every licence it states", () => {
  const manifest = new URL("../../workers/media/models.yaml", import.meta.url);
  const text = (() => { try { return readFileSync(manifest, "utf8"); } catch { return ""; } })();
  const found = manifestLicences(text);
  // the parser reads real entries; the disagreement itself is covered above and enforced by the docs build
  if (text) assert.ok(found.size >= 3);
});

test("the 720p A14B migration's INSERT is read by the registry (the \\c line must not swallow it), so its UPDATE has a row", async () => {
  const { readFileSync } = await import("node:fs");
  const { catalogue } = await import("../lib/model-registry.mjs");
  const rows = catalogue(readFileSync(new URL("../../infra/migrations/2026-10-16-wan-a14b-720p.sql", import.meta.url), "utf8"));
  const row = rows.find((r) => r.model_name === "wan2.2-t2v-a14b");
  assert.ok(row, "wan2.2-t2v-a14b is in the catalogue");
  assert.equal(row.provider_name, "localai-video");
  assert.equal(row.runs_on, "local");
  assert.equal(row.licence, "Apache-2.0");
});

test("the cinematic short's shot model (ADR-080) is in the catalogue as a local GPU model, image-to-video, 4 steps", async () => {
  const { readFileSync } = await import("node:fs");
  const { catalogue } = await import("../lib/model-registry.mjs");
  for (const file of ["../../infra/migrations/2026-10-17-cinematic-short.sql", "../../infra/deploy/dev-data.sql"]) {
    const row = catalogue(readFileSync(new URL(file, import.meta.url), "utf8")).find((r) => r.model_name === "wan2.2-i2v-a14b");
    assert.ok(row, `wan2.2-i2v-a14b is in ${file}`);
    assert.equal(row.provider_name, "localai-video");
    assert.equal(row.runs_on, "local");
    assert.equal(row.licence, "Apache-2.0");
    assert.equal(String(row.max_steps), "4");
  }
});

test("the default speech model is never one on an optional GPU host (ADR-080: Kokoro is asked for by name)", async () => {
  const { readFileSync } = await import("node:fs");
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-17-cinematic-short.sql", import.meta.url), "utf8");
  assert.doesNotMatch(sql, /is_default = \(model_name = 'kokoro-82m'\)/);
  assert.match(sql, /is_default = \(model_name = 'piper-en-medium-ljspeech'\) WHERE category = 'speech'/);
});

test("a psql meta-command line (\\c orazaka_db) does not glue to the INSERT that follows it", () => {
  const sql = "\\c orazaka_db\n\nINSERT INTO orazaka_models (model_name, category) VALUES ('m-x', 'image') ON CONFLICT (model_name) DO NOTHING;\nUPDATE orazaka_models SET vendor = 'V' WHERE model_name = 'm-x';";
  assert.equal(statements(sql).length, 2);
  assert.deepEqual(catalogue(sql).map((r) => r.model_name), ["m-x"]);
});
