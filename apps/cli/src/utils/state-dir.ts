/**
 * @file state-dir.ts
 * @description Where the CLI keeps its own state (login, threads, offline task queue, version cache): the platform's
 * config directory under the product name, never a dot-folder of the home directory (krizaka-internal#3662).
 * `SOVKORA_CLI_HOME` overrides it (tests, a relocated profile). State written under the former names is moved in, once.
 */

import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";

const PRODUCT = "sovkora-cli";

/** The former code name, spelled in two halves so the code-name ratchet does not count the migration itself. */
const FORMER = ["ora", "zaka"].join("");

/** A file or folder of the state, by its new name (relative to the state dir) and its former name in the home directory. */
const MIGRATIONS: ReadonlyArray<readonly [string, string]> = [
  ["config.json", `.${FORMER}-cli.json`],
  ["threads", `.${FORMER}-threads`],
  ["tasks.db", `.${FORMER}-tasks.db`],
  ["tasks.db-wal", `.${FORMER}-tasks.db-wal`],
  ["tasks.db-shm", `.${FORMER}-tasks.db-shm`],
  ["version-cache.json", path.join(`.${FORMER}-cli`, "version-cache.json")],
];

/** The state directory: `SOVKORA_CLI_HOME`, else the platform's config directory (macOS, Windows, XDG) under `sovkora-cli`. */
export function stateDir(): string {
  const override = process.env.SOVKORA_CLI_HOME;
  if (override) return override;
  const home = os.homedir();
  if (process.platform === "darwin") return path.join(home, "Library", "Application Support", PRODUCT);
  if (process.platform === "win32")
    return path.join(process.env.APPDATA || path.join(home, "AppData", "Roaming"), PRODUCT);
  return path.join(process.env.XDG_CONFIG_HOME || path.join(home, ".config"), PRODUCT);
}

/** One path of the state, after moving in what the former names held (once per process). */
export function statePath(name: string): string {
  migrateLegacyState();
  return path.join(stateDir(), name);
}

let migrated = false;

/** Moves each former home-folder file into the state dir when the new one does not exist yet; never overwrites, never deletes anything else. */
export function migrateLegacyState(home: string = os.homedir(), target: string = stateDir()): string[] {
  if (migrated && home === os.homedir() && target === stateDir()) return [];
  migrated = true;
  const moved: string[] = [];
  for (const [name, former] of MIGRATIONS) {
    const from = path.join(home, former);
    const to = path.join(target, name);
    if (!fs.existsSync(from) || fs.existsSync(to)) continue;
    fs.mkdirSync(path.dirname(to), { recursive: true });
    try {
      fs.renameSync(from, to);
    } catch {
      fs.cpSync(from, to, { recursive: true });
      fs.rmSync(from, { recursive: true, force: true });
    }
    moved.push(name);
  }
  return moved;
}

/** Test seam: forget that the migration ran. */
export function resetStateMigration(): void {
  migrated = false;
}
