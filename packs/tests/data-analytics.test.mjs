// The Data & Analytics pack (ADR-078, orazaka discussion #174): the recommender served by the knowledge service is
// bought as a pack, its API scopes are declared in the manifest, and its Studios are data over existing capabilities.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;
const dir = join(root, "data-analytics");
const pack = () => readFileSync(join(dir, "pack.yaml"), "utf8");
const blueprint = (key) => JSON.parse(readFileSync(join(dir, "studios", key, "blueprint.json"), "utf8"));
const schema = JSON.parse(readFileSync(join(root, "pack.schema.json"), "utf8"));

const STUDIOS = ["data-driven-ui", "video-understanding", "creator-suggestions"];
// The edge's ApiKeyScopes (orazaka-edge): exact method + path, fail closed. Kept identical here.
const SCOPES = {
  "reco:sync": [
    "POST /api/v1/recommendations/events",
    "POST /api/v1/recommendations/items",
    "POST /api/v1/recommendations/erasures",
  ],
  "reco:read": [
    "GET /api/v1/recommendations/for-you",
    "GET /api/v1/recommendations/similar",
    "GET /api/v1/recommendations/insights",
  ],
  "analytics:sync": ["POST /api/v1/analytics/events", "POST /api/v1/analytics/erasures"],
  "analytics:read": ["GET /api/v1/analytics/insights", "GET /api/v1/analytics/funnel"],
};

/** The routes a scope lists under `requires.api.scopes` of a pack.yaml — read line by line. */
export function scopeRoutes(yaml) {
  const out = {};
  let current = null;
  for (const line of yaml.split("\n")) {
    const key = line.match(/^\s+- key: ([a-z]+:[a-z]+)\s*$/);
    if (key) {
      current = key[1];
      out[current] = [];
      continue;
    }
    const route = line.match(/^\s+- "?(GET|POST|PUT|PATCH|DELETE) (\/[^"\s]+)"?\s*$/);
    if (route && current) out[current].push(`${route[1]} ${route[2]}`);
  }
  return out;
}

test("the pack stands on the Data & Analytics shelf, sold, and published", () => {
  assert.ok(existsSync(join(dir, "pack.yaml")));
  const yaml = pack();
  assert.match(yaml, /^key: data-analytics$/m);
  assert.match(yaml, /^  categoryKey: data-analytics$/m);
  assert.match(yaml, /^  status: PUBLISHED$/m);
  assert.match(yaml, /^  priceCents: \d+/m);
  assert.match(yaml, /^  includedCredits: \d+/m);
});

test("the schema lets a pack declare the platform API scopes it needs", () => {
  const api = schema.properties.requires.properties.api;
  assert.ok(api, "requires.api is part of the manifest grammar");
  assert.equal(api.properties.scopes.items.additionalProperties, false);
  assert.match("reco:sync", new RegExp(api.properties.scopes.items.properties.key.pattern));
  assert.doesNotMatch("Reco Sync", new RegExp(api.properties.scopes.items.properties.key.pattern));
});

test("the declared scopes are exactly the edge's reco:* and analytics:*", () => {
  assert.deepEqual(scopeRoutes(pack()), SCOPES);
});

test("each Studio is a published blueprint over existing capabilities, labelled in EN and FR", () => {
  const yaml = pack();
  for (const key of STUDIOS) {
    assert.match(yaml, new RegExp(`key: ${key}\\n[\\s\\S]*?entitlementKey: studio\\.${key}`));
    const b = blueprint(key);
    assert.equal(b.definition.studioKey, key);
    assert.equal(b.status, "PUBLISHED");
    // The engine's capabilities, and the pack's own worker (data-driven-ui adds up and draws, krizaka-internal#3628).
    for (const step of b.definition.steps)
      assert.match(step.featureKey, /^orazaka\.(core|data\.table)\./, `${key}: ${step.id}`);
    for (const lang of ["en", "fr"]) {
      const i18n = readFileSync(join(dir, "i18n", `${lang}.yaml`), "utf8");
      assert.match(i18n, new RegExp(`  ${key}:\\n    label:`), `${key} label in ${lang}`);
      assert.match(i18n, /^category:\n  label: /m, `category label in ${lang}`);
    }
  }
});

test("closed choices are chosen, never typed (no free-text model, languages are an enum)", () => {
  for (const key of STUDIOS) {
    const props = blueprint(key).inputSchema.properties;
    if (props.model)
      assert.deepEqual(
        props.model["x-options"],
        { source: "models", category: "chat" },
        `${key}: a model is picked from the catalogue, never typed`,
      );
    assert.deepEqual(props.language.enum, ["en", "fr"]);
  }
});

test("video understanding looks at a keyframe, then answers in JSON with safety labels — never a takedown", () => {
  const [see, read] = blueprint("video-understanding").definition.steps;
  assert.equal(see.featureKey, "orazaka.core.media.vision");
  assert.equal(blueprint("video-understanding").inputSchema.properties.assetId.contentMediaType, "image/*");
  assert.equal(read.featureKey, "orazaka.core.chat.completion");
  assert.deepEqual(read.dependsOn, [see.id]);
  for (const word of ["JSON", "topics", "tags", "safetyLabels", "never decide"])
    assert.ok(read.inputs.prompt.includes(word), word);
});

test("creator suggestions are proposals the creator approves, from the creator's own text", () => {
  const [step] = blueprint("creator-suggestions").definition.steps;
  assert.equal(step.featureKey, "orazaka.core.chat.completion");
  for (const word of ["JSON", "titles", "tags", "creator approves", "{{inputs.title}}"])
    assert.ok(step.inputs.prompt.includes(word), word);
});

test("the pack never handles a raw identity: its docs state the pseudonym rule (Loi 25)", () => {
  const readme = readFileSync(join(dir, "README.md"), "utf8");
  for (const words of ["HMAC-SHA256", "pseudonym", "erasures", "retention"]) assert.ok(readme.includes(words), words);
});

test("data-driven UI (krizaka-internal#3628): the worker computes the figures, the model only picks and explains", () => {
  const b = blueprint("data-driven-ui");
  const steps = Object.fromEntries(b.definition.steps.map((s) => [s.id, s]));
  assert.deepEqual(Object.keys(steps), ["facts", "answer", "check", "retry", "dashboard"]);
  // The figures come first, from the table, before any model writes.
  assert.equal(steps.facts.featureKey, "orazaka.data.table.facts");
  assert.deepEqual(steps.facts.dependsOn, []);
  assert.equal(
    steps.facts.inputs.data,
    "{{inputs.data:}}",
    "the data block may be empty (the table is in the question)",
  );
  // The model reads the facts, never the raw table, and names them by id.
  const prompt = steps.answer.inputs.prompt;
  assert.ok(prompt.includes("{{steps.computed.facts}}"));
  assert.ok(!prompt.includes("{{inputs.data"), "the model is never handed the table to add up");
  for (const words of [
    "ONE JSON object",
    "{F12}",
    "Never write a number of your own",
    "tone warning",
    "complete, valid example",
  ])
    assert.ok(prompt.includes(words), words);
  // Checked, asked again once with the reasons, then drawn — or a typed failure.
  assert.equal(steps.check.featureKey, "orazaka.data.table.dashboard");
  assert.equal(steps.check.inputs.final, "false");
  assert.equal(steps.retry.condition, "{{steps.check.valid}} == false");
  assert.ok(steps.retry.inputs.prompt.startsWith(prompt), "the retry asks the same question");
  assert.ok(steps.retry.inputs.prompt.includes("{{steps.check.problems}}"));
  assert.equal(steps.dashboard.inputs.final, "true");
  assert.equal(steps.dashboard.inputs.retry, "{{steps.retried.content:}}");
  assert.equal(steps.dashboard.onError, "FAIL");
  assert.equal(b.definition.outputs[0].from, "{{steps.drawn.answer}}");
  for (const chat of [steps.answer, steps.retry]) assert.deepEqual(chat.onErrorByCause, { GUARD_REFUSAL: "FAIL" });
  const props = b.inputSchema.properties;
  assert.equal(props.question.maxLength, 20000);
  // Launchable from the chat bar: one required prose input — the message, data pasted in it or not.
  assert.deepEqual(b.inputSchema.required, ["question"]);
  assert.equal(props.question.format, "prose");
  assert.equal(props.data.format, undefined, "a single prose input, so the chat bar knows where the message goes");
});

test("the data worker is the pack's, declared with what it bills (nothing) and run by its worker.yaml", () => {
  const yaml = pack();
  assert.match(yaml, /^tier: WORKER$/m);
  for (const [key, routing] of [
    ["orazaka.data.table.facts", "job.data.facts"],
    ["orazaka.data.table.dashboard", "job.data.dashboard"],
  ])
    assert.match(
      yaml,
      new RegExp(
        `- key: ${key.replaceAll(".", "\\.")}\\n\\s+routingKey: ${routing.replaceAll(".", "\\.")}\\n[\\s\\S]*?billableCapability: NONE`,
      ),
    );
  const worker = readFileSync(join(dir, "worker", "worker.yaml"), "utf8");
  assert.match(worker, /^family: data$/m);
  assert.match(worker, /- "job\.data\.\*"/);
});
