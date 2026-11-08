// Sonar typescript:S2871 (orazaka-web-client#275): the categories of a provider are sorted by their words, not by
// UTF-16 code units — a capitalised category must not jump ahead of every lower-case one.
import { test } from "node:test";
import assert from "node:assert/strict";
import { providerSummaries } from "../features/catalogue/utils/catalogue.ts";

const model = (modelName: string, category: string, providerName: string | null = "ollama") =>
  ({ modelName, category, providerName, compatible: true, isDefault: false }) as never;

test("a provider's categories read in alphabetical order, whatever their case", () => {
  const [summary] = providerSummaries(
    [model("a", "video"), model("b", "Image"), model("c", "audio"), model("d", "video")],
    ["ollama"],
  );
  assert.deepEqual(summary.categories, ["audio", "Image", "video"]);
});

test("providers are ranked by how many models they carry, then by name; models without one are unassigned", () => {
  const summaries = providerSummaries(
    [model("a", "chat", "b"), model("b", "chat", "a"), model("c", "chat", null)],
    ["a", "b"],
  );
  assert.deepEqual(
    summaries.map((s) => s.provider),
    ["a", "b", "unassigned"],
  );
});
