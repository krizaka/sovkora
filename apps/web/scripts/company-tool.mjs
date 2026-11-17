// The absolute path of one company tool (secret.sh…): in the folder `~/.local/bin/kz-config tools` names (KRIZAKA_TOOLS
// overrides it, kz-config reads it). Never a path of the HQ layout written here, never an import from outside this app.
import { execFileSync } from "node:child_process";
import os from "node:os";
import path from "node:path";

/** `exec` and `home` are injectable, so the test makes no real call. */
export function companyTool(name, { exec = execFileSync, home = os.homedir() } = {}) {
  const dir = String(exec(path.join(home, ".local", "bin", "kz-config"), ["tools"], { encoding: "utf8" })).trim();
  if (!dir) {
    throw new Error("`kz-config tools` answered nothing: set KRIZAKA_TOOLS");
  }
  return path.join(dir, name);
}
