#!/usr/bin/env node
/**
 * The commercial-name guard (krizaka-internal#2036, #2103): the app shows Sovkora, never its code name. Runs
 * `krizaka-code-names` (bin of @krizaka/org) on what a customer reads: the message catalogues (`src/i18n`) and the store
 * and system texts of `app.json` (the name under the icon, the permission prompts). The slug, scheme, bundle id and
 * hosts of `app.json` are identifiers and stay as they are, so only the displayed fields are handed to the guard.
 *
 *   node scripts/check-code-names.mjs [--report]
 */
import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, parse } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

/** The fields of `app.json` a customer reads: the display name and the system prompts of the plugins. */
export function displayedTexts(config) {
  const expo = config.expo ?? {};
  const prompts = (expo.plugins ?? [])
    .filter((p) => Array.isArray(p) && p[1] && typeof p[1] === "object")
    .flatMap(([, options]) =>
      Object.entries(options)
        .filter(([key, v]) => key.endsWith("Permission") && typeof v === "string")
        .map(([, v]) => v),
    );
  const infoPlist = Object.values(expo.ios?.infoPlist ?? {}).filter((v) => typeof v === "string");
  return { name: expo.name, prompts, infoPlist };
}

/**
 * The guard bin of @krizaka/org, found like Node resolves a package: from `start` up through every `node_modules`.
 * In the workspace CI the app sits in a hoisted install, so the package is not under the app's own `node_modules`.
 */
export function guardPath(start) {
  const bin = join("node_modules", "@krizaka", "org", "bin", "krizaka-code-names.mjs");
  for (let dir = start; ; dir = dirname(dir)) {
    if (existsSync(join(dir, bin))) return join(dir, bin);
    if (dir === parse(dir).root) return join(start, bin);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const scratch = mkdtempSync(join(tmpdir(), "code-names-"));
  mkdirSync(join(scratch, "i18n"));
  cpSync(join(root, "src", "i18n"), join(scratch, "i18n"), { recursive: true });
  const config = JSON.parse(readFileSync(join(root, "app.json"), "utf8"));
  writeFileSync(join(scratch, "store.json"), JSON.stringify(displayedTexts(config)));
  const guard = guardPath(root);
  const report = process.argv.includes("--report") ? ["--report"] : [];
  const run = spawnSync(process.execPath, [guard, ...report, scratch], { cwd: root, stdio: "inherit" });
  rmSync(scratch, { recursive: true, force: true });
  process.exit(run.status ?? 1);
}
