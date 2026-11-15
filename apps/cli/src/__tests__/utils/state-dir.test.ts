/**
 * @file state-dir.test.ts
 * @description The CLI state lives under the product name in the platform's config directory, and what the former
 * home-folder names held is moved in once, never overwriting.
 */

import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { migrateLegacyState, resetStateMigration, stateDir } from "../../utils/state-dir";

const FORMER = ["ora", "zaka"].join("");
let home: string;
let target: string;

beforeEach(() => {
  home = fs.mkdtempSync(path.join(os.tmpdir(), "cli-home-"));
  target = path.join(home, "state");
  resetStateMigration();
});

afterEach(() => {
  fs.rmSync(home, { recursive: true, force: true });
  delete process.env.SOVKORA_CLI_HOME;
});

describe("stateDir", () => {
  test("SOVKORA_CLI_HOME wins", () => {
    process.env.SOVKORA_CLI_HOME = "/somewhere/else";
    expect(stateDir()).toBe("/somewhere/else");
  });

  test("is under the product name, never a dot-folder of the former name", () => {
    delete process.env.SOVKORA_CLI_HOME;
    const dir = stateDir();
    expect(path.basename(dir)).toBe("sovkora-cli");
    expect(dir).not.toContain(FORMER);
  });
});

describe("migrateLegacyState", () => {
  test("moves the former config, threads, queue and version cache", () => {
    fs.writeFileSync(path.join(home, `.${FORMER}-cli.json`), "config-body");
    fs.mkdirSync(path.join(home, `.${FORMER}-threads`));
    fs.writeFileSync(path.join(home, `.${FORMER}-threads`, "a.json"), "[]");
    fs.writeFileSync(path.join(home, `.${FORMER}-tasks.db`), "db");
    fs.mkdirSync(path.join(home, `.${FORMER}-cli`));
    fs.writeFileSync(path.join(home, `.${FORMER}-cli`, "version-cache.json"), "{}");

    const moved = migrateLegacyState(home, target);

    expect(moved.sort()).toEqual(["config.json", "tasks.db", "threads", "version-cache.json"]);
    expect(fs.readFileSync(path.join(target, "config.json"), "utf-8")).toBe("config-body");
    expect(fs.readFileSync(path.join(target, "threads", "a.json"), "utf-8")).toBe("[]");
    expect(fs.existsSync(path.join(home, `.${FORMER}-cli.json`))).toBe(false);
    expect(fs.existsSync(path.join(home, `.${FORMER}-threads`))).toBe(false);
  });

  test("never overwrites state that already exists under the new name", () => {
    fs.mkdirSync(target, { recursive: true });
    fs.writeFileSync(path.join(target, "config.json"), "new");
    fs.writeFileSync(path.join(home, `.${FORMER}-cli.json`), "old");

    expect(migrateLegacyState(home, target)).toEqual([]);
    expect(fs.readFileSync(path.join(target, "config.json"), "utf-8")).toBe("new");
    expect(fs.readFileSync(path.join(home, `.${FORMER}-cli.json`), "utf-8")).toBe("old");
  });

  test("does nothing when there is nothing to move", () => {
    expect(migrateLegacyState(home, target)).toEqual([]);
    expect(fs.existsSync(target)).toBe(false);
  });
});
