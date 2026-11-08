// node --test scripts/test — where the dev tools read their secrets: an explicit file, or secret.sh @dev (never a hard-coded path).
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { SECRET_TOOL, devSecrets, envFileFromArgs } from "../lib/dev-secrets.mjs";

test("--env-file is taken out of the arguments and wins over ORAZAKA_DEV_ENV_FILE", () => {
  assert.deepEqual(envFileFromArgs(["apply", "sovkora-dev", "--env-file", "/x/dev.env", "--dry-run"], { ORAZAKA_DEV_ENV_FILE: "/y" }), {
    envFile: "/x/dev.env",
    args: ["apply", "sovkora-dev", "--dry-run"],
  });
  assert.deepEqual(envFileFromArgs(["data"], { ORAZAKA_DEV_ENV_FILE: "/y" }), { envFile: "/y", args: ["data"] });
  assert.deepEqual(envFileFromArgs(["data"], {}), { envFile: null, args: ["data"] });
  assert.throws(() => envFileFromArgs(["apply", "--env-file"], {}), /--env-file needs a path/);
});

test("with a file, the secrets are its lines; a missing file is named, never a value", () => {
  const dir = mkdtempSync(join(tmpdir(), "devsec-"));
  const file = join(dir, "dev.env");
  writeFileSync(file, "# c\nDATABASE_HOST=db.example\nB=x=y\n");
  const env = devSecrets({ envFile: file });
  assert.equal(env.DATABASE_HOST, "db.example");
  assert.equal(env.B, "x=y");
  assert.equal(env.NOPE, undefined);
  assert.throws(() => devSecrets({ envFile: join(dir, "absent.env") }), /no secrets file at .*absent\.env/);
});

test("without a file, each key is read through secret.sh @orazaka, once, and an empty answer is undefined", () => {
  const calls = [];
  const run = (tool, args) => {
    calls.push([tool, ...args]);
    return args[1] === "KNOWN" ? "v1\n" : "";
  };
  const env = devSecrets({ envFile: null, tool: "/t/secret.sh", run });
  assert.equal(env.KNOWN, "v1");
  assert.equal(env.KNOWN, "v1");
  assert.equal(env.UNKNOWN, undefined);
  assert.deepEqual(calls, [["/t/secret.sh", "@orazaka", "KNOWN"], ["/t/secret.sh", "@orazaka", "UNKNOWN"]]);
});

test("the settings overrides (CORE_REBUILD) come from the process environment first", () => {
  const env = devSecrets({ envFile: null, run: () => "from-tool", environ: { CORE_REBUILD: "stamp" }, overridable: ["CORE_REBUILD"] });
  assert.equal(env.CORE_REBUILD, "stamp");
  assert.equal(env.OTHER, "from-tool");
});

test("the real secret.sh resolves @orazaka through its chain: the product's own file first, then dev.env, then org.env", { skip: !existsSync(SECRET_TOOL) && "secret.sh is the operator's tool, absent on CI" }, () => {
  const dir = mkdtempSync(join(tmpdir(), "chain-"));
  writeFileSync(join(dir, "orazaka.env"), "MEDIA_STORAGE_ZONE=orazaka-media-dev\n");
  writeFileSync(join(dir, "dev.env"), "MEDIA_STORAGE_ZONE=krizaka-assets\nDATABASE_HOST=db.example\n");
  writeFileSync(join(dir, "org.env"), "DATABASE_HOST=never\nAPPLE_TEAM_ID=TEAM123456\n");
  writeFileSync(join(dir, "secrets.map"), "orazaka orazaka.env dev.env org.env\ndev dev.env org.env\n");
  const environ = { ...process.env, KRIZAKA_SECRETS_MAP: join(dir, "secrets.map") };
  const own = devSecrets({ environ });
  assert.equal(own.MEDIA_STORAGE_ZONE, "orazaka-media-dev", "the product's own file wins over dev.env");
  assert.equal(own.DATABASE_HOST, "db.example", "dev.env next");
  assert.equal(own.APPLE_TEAM_ID, "TEAM123456", "org.env last");
  assert.equal(own.ABSENT, undefined);
});
