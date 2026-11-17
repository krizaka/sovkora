// Where the company tools (secret.sh, env-set.sh, pb.py, gh-app-token.py, seed-dev.sh…) live on this machine.
// Never a path written in a script: `KRIZAKA_TOOLS` when set, else what `~/.local/bin/kz-config tools` answers.
import { execFileSync } from "node:child_process";
import { homedir } from "node:os";
import { join } from "node:path";

/** The tools folder, or an error that says what to set. `exec` and `environ` are injectable (tests make no real call). */
export function toolsDir({ environ = process.env, exec = execFileSync, home = homedir() } = {}) {
  const fromEnv = environ.KRIZAKA_TOOLS?.trim();
  if (fromEnv) return fromEnv;
  let answer = "";
  try {
    answer = String(exec(join(home, ".local/bin/kz-config"), ["tools"], { encoding: "utf8" })).trim();
  } catch (cause) {
    throw new Error("the company tools folder is unknown: set KRIZAKA_TOOLS, or install ~/.local/bin/kz-config (`kz-config tools`)", { cause });
  }
  if (!answer) throw new Error("the company tools folder is unknown: `kz-config tools` answered nothing; set KRIZAKA_TOOLS");
  return answer;
}

/** The absolute path of one company tool, e.g. `toolPathOf("secret.sh")`. */
export function toolPathOf(name, options) {
  return join(toolsDir(options), name);
}
