#!/usr/bin/env node
/**
 * What npm users get, tested before it is published: packs `sovkora`, installs the tarball
 * in an empty project outside any Sovkora workspace, and runs the `sovkora` bin from there — the same
 * path `npx sovkora …` takes (one package, no alias).
 *
 *   node scripts/pack-smoke.mjs            (npm run test:pack; CI runs it on every pull request)
 */

import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const cli = JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8"));
const isWindows = process.platform === "win32";

let failures = 0;
function check(label, ok, detail = "") {
  console.log(`${ok ? "✔" : "✖"} ${label}${ok || !detail ? "" : "\n" + detail}`);
  if (!ok) failures += 1;
}

function run(cmd, args, cwd, extraEnv = {}) {
  const result = spawnSync(cmd, args, {
    cwd,
    encoding: "utf8",
    shell: isWindows,
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, ORAZAKA_SKIP_VERSION_CHECK: "true", NO_COLOR: "1", FORCE_COLOR: "0", ...extraEnv },
  });
  return { status: result.status, out: `${result.stdout ?? ""}${result.stderr ?? ""}` };
}

function pack(dir, dest) {
  // Built just before (prepack would print into the JSON this reads).
  const result = run("npm", ["pack", "--json", "--ignore-scripts", "--pack-destination", dest], dir);
  if (result.status !== 0) {
    console.error(result.out);
    process.exit(1);
  }
  const json = JSON.parse(result.out.slice(result.out.indexOf("[")));
  return { file: join(dest, json[0].filename), files: json[0].files.map((f) => f.path) };
}

const work = mkdtempSync(join(tmpdir(), "sovkora-pack-"));
try {
  // ─── The tarballs ─────────────────────────────────────────────────────────
  const build = run("npm", ["run", "build"], ROOT);
  if (build.status !== 0) {
    console.error(build.out);
    process.exit(1);
  }
  const cliPack = pack(ROOT, work);

  check(
    "the CLI tarball holds dist, README, LICENSE, NOTICE and package.json only",
    cliPack.files.every((f) => /^(dist\/|README\.md$|LICENSE$|NOTICE$|package\.json$)/.test(f)),
    cliPack.files.filter((f) => !/^(dist\/|README\.md$|LICENSE$|NOTICE$|package\.json$)/.test(f)).join("\n"),
  );
  check("the CLI tarball ships no test", !cliPack.files.some((f) => f.includes("__tests__")));
  check("the CLI tarball has its bin", cliPack.files.includes(cli.bin.sovkora));

  // ─── An empty project, outside any workspace ──────────────────────────────
  const project = join(work, "project");
  mkdirSync(project);
  writeFileSync(join(project, "package.json"), JSON.stringify({ private: true }, null, 2));
  const install = run("npm", ["install", "--no-audit", "--no-fund", cliPack.file], project);
  check("the CLI installs in an empty project", install.status === 0, install.out);

  const bin = join(project, "node_modules", ".bin", isWindows ? "sovkora.cmd" : "sovkora");
  const elsewhere = join(work, "elsewhere");
  mkdirSync(elsewhere);

  const version = run(bin, ["--version"], elsewhere);
  check(
    `sovkora --version prints ${cli.version}`,
    version.status === 0 && version.out.trim() === cli.version,
    version.out,
  );

  const cliBin = join(project, "node_modules", "sovkora", cli.bin.sovkora);
  const canonical = run(process.execPath, [cliBin, "--version"], elsewhere);
  check("the package's own bin runs", canonical.status === 0 && canonical.out.trim() === cli.version, canonical.out);

  const unknown = run(bin, ["no-such-command"], elsewhere);
  check("an unknown command exits non-zero", unknown.status !== 0, unknown.out);

  const help = run(bin, ["--help"], elsewhere);
  check(
    "sovkora --help lists install, start and dev",
    help.status === 0 && ["install", "start", "dev", "demo"].every((c) => help.out.includes(`\n  ${c}`)),
    help.out,
  );

  const doctorHelp = run(bin, ["doctor", "--help"], elsewhere);
  check(
    "sovkora doctor --help",
    doctorHelp.status === 0 && doctorHelp.out.includes("Usage: sovkora doctor"),
    doctorHelp.out,
  );

  const logsHelp = run(bin, ["logs", "--help"], elsewhere);
  check("sovkora logs --service is offered", logsHelp.status === 0 && logsHelp.out.includes("--service"), logsHelp.out);

  const seedHelp = run(bin, ["demo", "seed", "--help"], elsewhere);
  check("sovkora demo seed --help", seedHelp.status === 0 && seedHelp.out.includes("--fresh"), seedHelp.out);

  const checkOnly = run(bin, ["install", "--check-only"], elsewhere);
  check(
    "sovkora install --check-only reports the tools",
    checkOnly.status === 0 && checkOnly.out.includes("Tool"),
    checkOnly.out,
  );

  for (const command of ["start", "dev"]) {
    const outside = run(bin, [command], elsewhere);
    check(
      `sovkora ${command} outside a workspace explains how to get one`,
      outside.status === 1 && outside.out.includes("Sovkora workspace") && outside.out.includes("npx sovkora install"),
      outside.out,
    );
  }

  // The release manifest check runs from the published package alone (its schema ships in dist/).
  const releaseDir = join(work, "release");
  mkdirSync(releaseDir);
  const manifestFile = join(releaseDir, "orazaka-release.json");
  writeFileSync(manifestFile, JSON.stringify({ schema: "orazaka.dev/release/v1", version: "1.0.0" }));
  const release = run(bin, ["release", "verify", manifestFile, "--json"], elsewhere);
  check(
    "sovkora release verify refuses an incomplete manifest with exit 1",
    release.status === 1 && release.out.includes('"ok": false') && release.out.includes("upgradeFrom"),
    release.out,
  );

  const noTty = run(bin, ["install"], elsewhere);
  check(
    "sovkora install without a terminal and without --yes clones nothing",
    noTty.status === 1 && noTty.out.includes("--yes") && readdirSync(elsewhere).length === 0,
    `${noTty.out}\n${readdirSync(elsewhere).join(", ")}`,
  );
} finally {
  rmSync(work, { recursive: true, force: true });
}

if (failures > 0) {
  console.error(`\n${failures} check(s) failed.`);
  process.exit(1);
}
console.log("\nThe packed CLI works from npm.");
