#!/usr/bin/env node
/**
 * The commercial-name guard (krizaka-internal#2036, #2103): no built page, llms.txt or caption shows a code name
 * where the commercial name is required. Runs `krizaka-code-names` (bin of @krizaka/org) on the prerendered output of
 * `next build`: the HTML pages and the text bodies (`llms.txt`, captions), copied to a scratch folder so the
 * guard reads them as `.html` / `.txt`. `/docs/*` is the generated developer documentation of the engine (written in
 * the Orazaka repository, `scripts/sync-docs.mjs`), it keeps the code names until that repository speaks of Sovkora. `llms*.txt` are markdown whose `[label](https://www.orazaka.com/…)` links the guard does not yet read as addresses (to be fixed in `/org`); their prose is checked by the unit test.
 *
 *   node scripts/check-code-names.mjs [--report]
 */
import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const built = join(root, ".next", "server", "app");

/** Developer pages that keep the code names (the engine documentation generated from its own repository; /open-source lists the repositories with their GitHub names and descriptions). */
export const ALLOWED = ["/docs/", "docs.html", "llms", "open-source"];

/** The scratch name of a built file, or null when the guard does not read it. */
function scratchName(entry, path) {
  if (entry.endsWith(".html")) {
    return relative(built, path);
  }
  const text = entry.endsWith(".body") && !entry.startsWith("opengraph-image");
  return text ? `${relative(built, path)}.txt` : null;
}

/** The prerendered pages and text bodies of the build, as [source, name in the scratch folder]. */
function collect(dir, found = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    const name = statSync(path).isDirectory() ? null : scratchName(entry, path);
    if (name) {
      found.push([path, name]);
    } else if (statSync(path).isDirectory() && !entry.endsWith(".segments")) {
      collect(path, found);
    }
  }
  return found;
}

if (!existsSync(built)) {
  console.error("check-code-names: no build output, run `next build` first.");
  process.exit(2);
}
const scratch = mkdtempSync(join(tmpdir(), "code-names-"));
for (const [from, name] of collect(built)) {
  const to = join(scratch, name);
  mkdirSync(dirname(to), { recursive: true });
  cpSync(from, to);
}
const guard = join(root, "node_modules", "@krizaka", "org", "bin", "krizaka-code-names.mjs");
const args = [guard, ...process.argv.slice(2).filter((a) => a === "--report")];
for (const part of ALLOWED) {
  args.push("--allow", part);
}
args.push(scratch);
const run = spawnSync(process.execPath, args, { cwd: root, stdio: "inherit" });
rmSync(scratch, { recursive: true, force: true });
process.exit(run.status ?? 1);
