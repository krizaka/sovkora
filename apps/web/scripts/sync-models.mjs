#!/usr/bin/env node
/**
 * Writes `src/features/landing/utils/models.generated.json` — the models Orazaka supports, for the job screens and the docs
 * page — and refreshes `content/docs/models.md` (the catalogue page of the documentation).
 *
 *   node scripts/sync-models.mjs <path to the krizaka/orazaka workspace>
 *
 * Everything is read, never typed: the `models` section of the workspace's generated `docs/_generated/architecture.json`
 * (krizaka/orazaka scripts/generate-docs.mjs, from the registry the routing reads) and its `MODELS.md`. Never hand-edit
 * the output; change the registry, regenerate, run this again.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const workspace = process.argv[2];
if (!workspace) {
  console.error("usage: node scripts/sync-models.mjs <krizaka/orazaka workspace>");
  process.exit(1);
}
const generated = path.join(workspace, "docs", "_generated");
const arch = JSON.parse(fs.readFileSync(path.join(generated, "architecture.json"), "utf8"));
if (!Array.isArray(arch.models) || arch.models.length === 0) {
  console.error(
    "architecture.json has no `models` section — regenerate the workspace docs (node scripts/generate-docs.mjs)",
  );
  process.exit(1);
}

const models = arch.models.map((m) => ({
  id: m.id,
  name: m.name,
  vendor: m.vendor,
  family: m.family,
  capabilities: m.capabilities,
  runsOn: m.runsOn,
  runtime: m.runtime,
  openWeights: m.openWeights,
  licence: m.licence,
  commercialUse: m.commercialUse,
  contextWindow: m.contextWindow,
  status: m.status,
  lastVerified: m.lastVerified,
}));
const out = path.join(root, "src", "features", "landing", "utils", "models.generated.json");
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, `${JSON.stringify({ models }, null, 2)}\n`);

// The public page of the catalogue says what the public list says: the sections about the models kept out of it
// (the unfiltered ones, which an operator may serve) stay in the workspace docs.
const KEPT_OUT = ["Unfiltered models, your rules", "In the catalogue, not published"];
// A sentence of a kept-out section that the public page still needs (the link to the Measured runs page) stays.
const STAYS = [/^How fast and at what price[^\n]*$/m];
const withoutKeptOut = (text) => {
  const blocks = text.split(/^(?=## )/m);
  const kept = blocks.filter((block) => !KEPT_OUT.some((title) => block.startsWith(`## ${title}`)));
  const dropped = blocks.filter((block) => KEPT_OUT.some((title) => block.startsWith(`## ${title}`)));
  const stays = dropped.flatMap((block) => STAYS.map((re) => block.match(re)?.[0]).filter(Boolean));
  return kept.join("") + (stays.length ? `\n${stays.join("\n\n")}\n` : "");
};
const md = withoutKeptOut(
  fs
    .readFileSync(path.join(generated, "MODELS.md"), "utf8")
    .replace(/^---\n[\s\S]*?\n---\n/, "")
    .trimStart(),
);
fs.writeFileSync(path.join(root, "content", "docs", "models.md"), md);
process.stdout.write(
  `${models.length} models → src/features/landing/utils/models.generated.json, content/docs/models.md\n`,
);
