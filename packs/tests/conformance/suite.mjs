// The pack conformance suite, generated from the manifests: one entry per capability a pack declares
// (`requires.capabilities`), never a typed list of packs. A capability is *covered* when its pack ships
// `tests/conformance/<capability key>.json` (a synthetic input and deterministic assertions on the answer) and the
// pack's own worker handler, called on that input, answers something that satisfies the capability's `outputSchema`
// and those assertions. No broker, no model, no network: the handler is the production code, called as the loop calls it.
import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import Ajv from "ajv";
import YAML from "yaml";
import { assertOutput } from "../golden/harness.mjs";

const ajv = new Ajv({ strict: false, validateFormats: false, allErrors: true });
const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));

/** Every pack folder, with what its manifest declares. */
export function discoverPacks(root) {
  return readdirSync(root)
    .sort()
    .filter((name) => existsSync(join(root, name, "pack.yaml")))
    .map((pack) => {
      const manifest = YAML.parse(readFileSync(join(root, pack, "pack.yaml"), "utf8"));
      const capabilities = (manifest.requires?.capabilities ?? []).map((c) => {
        const fixtureFile = join(root, pack, "tests", "conformance", `${c.key}.json`);
        return { ...c, fixtureFile, fixture: existsSync(fixtureFile) ? readJson(fixtureFile) : null };
      });
      return { pack, manifest, capabilities, studios: (manifest.studios ?? []).map((s) => s.key) };
    });
}

/** The conformance fixtures no manifest declares: a renamed or removed capability leaves one behind. */
export function orphanFixtures(root, packs) {
  const orphans = [];
  for (const { pack, capabilities } of packs) {
    const dir = join(root, pack, "tests", "conformance");
    if (!existsSync(dir)) continue;
    const declared = new Set(capabilities.map((c) => `${c.key}.json`));
    for (const file of readdirSync(dir)) if (!declared.has(file)) orphans.push(`${pack}/${file}`);
  }
  return orphans;
}

/** The handlers' answers for a pack's fixtures, one process per pack. Throws when no interpreter can run them. */
export function callHandlers(root, pack, requests) {
  const runner = join(root, "tests", "conformance", "run_handlers.py");
  const python = process.env.PACK_PYTHON;
  const [cmd, args] = python
    ? [python, [runner, join(root, pack)]]
    : [
        "uv",
        [
          "run",
          "-q",
          "--with",
          "pika",
          "--with",
          "pyyaml",
          "--with",
          "pypdf",
          "--with",
          "pikepdf",
          "--with",
          "reportlab",
          "--with",
          "Pillow",
          "python",
          runner,
          join(root, pack),
        ],
      ];
  const run = spawnSync(cmd, args, { input: JSON.stringify(requests), encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (run.status !== 0) throw new Error(`${pack}: the handler runner failed: ${run.error ?? run.stderr.slice(-800)}`);
  return JSON.parse(run.stdout);
}

/** What one capability's fixture proves: `null` when it holds, else the reason it does not. */
export function judge(capability, answer) {
  const { fixture } = capability;
  if (!fixture) return "no fixture";
  if (fixture.capability !== capability.key) return "the fixture names another capability";
  if (!Array.isArray(fixture.assertions) || fixture.assertions.length === 0) return "no assertion on the answer";
  const input = ajv.compile(capability.inputSchema ?? {});
  if (!input(fixture.input)) return `input breaks the inputSchema: ${ajv.errorsText(input.errors)}`;
  if (answer?.error) return `the handler failed: ${answer.error}`;
  const output = ajv.compile(capability.outputSchema ?? {});
  if (!output(answer?.result)) return `answer breaks the outputSchema: ${ajv.errorsText(output.errors)}`;
  try {
    assertOutput(answer.result, fixture.assertions);
  } catch (failure) {
    return `assertion failed: ${failure.message.split("\n")[0]}`;
  }
  return null;
}

/** The coverage table: capabilities declared against capabilities proven, plus the Studios each pack's goldens hold. */
export function coverageTable(rows) {
  const lines = ["| pack | capabilities declared | proven | not proven (reason) | studios |", "|---|---|---|---|---|"];
  let declared = 0;
  let proven = 0;
  for (const r of rows) {
    declared += r.declared;
    proven += r.proven.length;
    const missing = r.missing.map((m) => `${m.key.split(".").slice(1).join(".")}: ${m.reason}`).join("; ") || "-";
    lines.push(`| ${r.pack} | ${r.declared} | ${r.proven.length} | ${missing} | ${r.studios} |`);
  }
  lines.push(`| **total** | **${declared}** | **${proven}** | | |`);
  return lines.join("\n");
}
