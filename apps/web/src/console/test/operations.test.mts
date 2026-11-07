// The operations' figures and the registry's summaries, tested on their own (no bundler, no DOM).
import { test } from "node:test";
import assert from "node:assert/strict";
import { dependencyChecks, jobSeconds, queueDepths, recentFailures } from "../features/operations/utils/operations.ts";
import { providerSummaries, withCapability } from "../features/catalogue/utils/catalogue.ts";

const job = (over: object) => ({
  id: "j",
  userId: "u",
  featureKey: "orazaka.core.chat",
  status: "COMPLETED",
  payload: {},
  result: {},
  createdAt: "2026-10-15T10:00:00Z",
  updatedAt: "2026-10-15T10:00:12Z",
  ...over,
});

test("queue: every state is counted, and an empty queue is all zeros", () => {
  assert.deepEqual(queueDepths([]), { PENDING: 0, PROCESSING: 0, COMPLETED: 0, FAILED: 0, CANCELLED: 0, DELETED: 0 });
  assert.deepEqual(
    queueDepths([
      job({}),
      job({ status: "FAILED" }),
      job({ status: "PENDING" }),
      job({ status: "PENDING" }),
      job({ status: "CANCELLED" }),
    ]),
    { PENDING: 2, PROCESSING: 0, COMPLETED: 1, FAILED: 1, CANCELLED: 1, DELETED: 0 },
  );
});

test("failures: only the failed, newest first, capped", () => {
  const rows = [
    job({ id: "a", status: "FAILED", updatedAt: "2026-10-15T09:00:00Z" }),
    job({ id: "b", status: "FAILED", updatedAt: "2026-10-15T11:00:00Z" }),
    job({ id: "c" }),
  ];
  assert.deepEqual(
    recentFailures(rows).map((j) => j.id),
    ["b", "a"],
  );
  assert.equal(recentFailures(rows, 1).length, 1);
});

test("duration: only for a job that ended, never negative", () => {
  assert.equal(jobSeconds(job({})), 12);
  assert.equal(jobSeconds(job({ status: "PROCESSING" })), null);
  assert.equal(jobSeconds(job({ updatedAt: "2026-10-15T09:00:00Z" })), null);
});

test("dependencies: a stopped Ollama is a failure, a non-sovereign default provider only a warning", () => {
  const checks = dependencyChecks({
    status: "Non-Compliant",
    pgVectorConnected: true,
    pgVectorExtensionInstalled: true,
    ollamaConnected: false,
    ollamaModels: [],
    defaultProvider: "openai",
  });
  assert.deepEqual(
    checks.map((c) => [c.id, c.tone]),
    [
      ["database", "success"],
      ["vector", "success"],
      ["ollama", "danger"],
      ["provider", "warning"],
    ],
  );
});

const model = (modelName: string, category: string, providerName: string | null) => ({
  modelName,
  category,
  providerName,
  compatible: true,
});

test("providers: models counted per provider, empty providers kept, unassigned said", () => {
  const out = providerSummaries(
    [model("a", "CHAT", "ollama"), model("b", "IMAGE", "ollama"), model("c", "CHAT", null)],
    ["ollama", "openai"],
  );
  assert.deepEqual(out, [
    { provider: "ollama", models: 2, categories: ["CHAT", "IMAGE"] },
    { provider: "unassigned", models: 1, categories: ["CHAT"] },
    { provider: "openai", models: 0, categories: [] },
  ]);
});

test("capability switch: only the named one changes", () => {
  const list = [
    { featureKey: "x", handlerKey: "h", routingKey: "job.a.b", enabled: true },
    { featureKey: "y", handlerKey: "h", routingKey: "job.a.c", enabled: true },
  ];
  assert.deepEqual(
    withCapability(list, "y", false).map((c) => c.enabled),
    [true, false],
  );
});
