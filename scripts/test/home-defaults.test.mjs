// Guard (krizaka-internal#3662): no code reads or writes a hand-made folder of the home directory under a former code name.
// A path comes from configuration (kz-config for dev tooling, an environment variable with no home default for services) or,
// for the product CLI, from the platform's config directory under the product name (apps/cli/src/utils/state-dir.ts).
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const NAME = ["ora", "zaka"].join(""); // two halves, so this file is not its own finding
// A line that names the home directory AND a dot-folder (or dot-file) of the former code name.
const HOME_WORDS = String.raw`(?:~|\$HOME|\$\{HOME\}|homedir|expanduser|user\.home|os\.environ\[?"HOME)`;
export const HOME_DEFAULT = new RegExp(`${HOME_WORDS}[^\\n]*(?<![\\w])\\.${NAME}(?![\\w]*\\.(?:com|io|dev)\\b)`, "i");
const SKIP = /(^|\/)(pnpm-lock\.yaml|package-lock\.json|[^/]*\.lock)$|^scripts\/test\/home-defaults\.test\.mjs$|^docs\/NAMING\.md$|^scripts\/test\/dev-deploy\.test\.mjs$/;
const TEXT = /\.(mjs|js|ts|tsx|py|sh|java|ya?ml|json|md|properties|toml)$/;

export function offenders(files, read) {
  return files
    .filter((f) => TEXT.test(f) && !SKIP.test(f))
    .flatMap((f) => read(f).split("\n").map((line, i) => ({ f, n: i + 1, line })).filter(({ line }) => HOME_DEFAULT.test(line)))
    .map(({ f, n, line }) => `${f}:${n}: ${line.trim().slice(0, 120)}`);
}

test("the guard sees a home default under a former code name", () => {
  const planted = `p = os.path.expanduser("~/.${NAME}/sound")\nconst d = path.join(os.homedir(), ".${NAME}-cli");`;
  assert.equal(offenders(["x.py"], () => planted).length, 2);
  assert.equal(offenders(["x.ts"], () => `url = "https://dev.${NAME}.com"`).length, 0);
});

test("no file of the repository defaults to a former-code-name folder of the home directory", () => {
  const files = execFileSync("git", ["ls-files"], { cwd: ROOT, encoding: "utf8" }).split("\n").filter(Boolean);
  const found = offenders(files, (f) => {
    try {
      return readFileSync(join(ROOT, f), "utf8");
    } catch {
      return "";
    }
  });
  assert.deepEqual(found, []);
});
