import assert from "node:assert/strict";
import { test } from "node:test";
import { KEPT, MODELS, shotOf } from "./film-records.mjs";

const record = {
  id: "s1",
  runId: "r1",
  status: "SUCCEEDED",
  gpuCostUsd: 0.25,
  reported: { model: "m", gpu: "NVIDIA H200", gpuSeconds: 110 },
};

test("a kept shot carries its run id, model, GPU and GPU cost from its record", () => {
  assert.deepEqual(shotOf(record), {
    id: "s1",
    runId: "r1",
    model: "m",
    gpu: "NVIDIA H200",
    gpuSeconds: 110,
    gpuCostUsd: 0.25,
  });
});

test("a record that did not succeed stops the script: a failed run is not a shot of the film", () => {
  assert.throws(() => shotOf({ ...record, status: "FAILED" }), /FAILED/);
});

test("the film keeps seven shots and names three models", () => {
  assert.equal(new Set(KEPT).size, 7);
  assert.deepEqual(
    MODELS.map((m) => m.role),
    ["keyframes", "shots", "music"],
  );
});
