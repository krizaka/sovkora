/**
 * The absolute path of a program the recording scripts spawn (SonarCloud S4036: a program found through PATH can be
 * replaced by whoever controls PATH). Only the system and package-manager folders below are searched, never PATH.
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const folders = () => [
  "/opt/homebrew/bin",
  "/usr/local/bin",
  "/usr/bin",
  "/bin",
  path.join(os.homedir(), ".maestro", "bin"),
];

/** The first executable named `name` in the trusted folders; throws when there is none. */
export function toolPath(name, search = folders()) {
  if (!/^[a-z0-9._-]+$/i.test(name)) throw new Error(`${name} is not a program name`);
  for (const dir of search) {
    const candidate = path.join(dir, name);
    try {
      fs.accessSync(candidate, fs.constants.X_OK);
      if (fs.statSync(candidate).isFile()) return candidate;
    } catch {
      // not in this folder
    }
  }
  throw new Error(`${name} not found in ${search.join(", ")}: install it (brew install ${name}) and retry`);
}

/**
 * The absolute path of one company tool (secret.sh…), in the folder kz-config names (~/.local/bin/kz-config, found like
 * any program above; KRIZAKA_TOOLS overrides its answer). `run` is injectable, so the test spawns nothing.
 */
export function companyTool(
  name,
  { run = (bin, args) => execFileSync(bin, args, { encoding: "utf8" }), kzConfig } = {},
) {
  const folder = String(
    run(kzConfig ?? toolPath("kz-config", [path.join(os.homedir(), ".local", "bin")]), ["tools"]),
  ).trim();
  if (folder === "") {
    throw new Error("kz-config gave no tools folder: set KRIZAKA_TOOLS");
  }
  return path.join(folder, name);
}
