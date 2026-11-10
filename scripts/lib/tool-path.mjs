// The absolute path of a program the workspace scripts spawn (docker, git, psql, ffmpeg, npm…). A program found through PATH can
// be swapped by whoever controls PATH, so PATH is never read: only the running node's own folder (npm and npx sit beside it, on a
// Mac with nvm as on a CI runner) and the system and Homebrew folders below are searched, in that order.
import { accessSync, constants, statSync } from "node:fs";
import path from "node:path";

export const TOOL_FOLDERS = Object.freeze([
  path.dirname(process.execPath),
  "/opt/homebrew/bin",
  "/opt/homebrew/opt/libpq/bin",
  "/usr/local/bin",
  "/usr/bin",
  "/bin",
  "/usr/sbin",
]);

/** The first executable file named `name` in `folders`, or null. */
export function toolPathOrNull(name, folders = TOOL_FOLDERS) {
  if (!/^[A-Za-z0-9._-]+$/.test(name) || name === "." || name === "..") throw new Error(`${name} is not a program name`);
  for (const dir of folders) {
    const candidate = path.join(dir, name);
    try {
      accessSync(candidate, constants.X_OK);
      if (statSync(candidate).isFile()) return candidate;
    } catch {
      // not in this folder
    }
  }
  return null;
}

/** The first executable file named `name` in `folders`; throws, naming the folders, when there is none. */
export function toolPath(name, folders = TOOL_FOLDERS) {
  const found = toolPathOrNull(name, folders);
  if (!found) throw new Error(`${name} not found in ${folders.join(", ")}: install it and retry`);
  return found;
}
