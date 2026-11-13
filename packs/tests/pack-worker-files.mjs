// Shared by the pack worker sweeps: the non-test Python files of every `<pack>/worker/` folder, absolute paths.
import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

export const PACKS = new URL("..", import.meta.url).pathname;

export function packWorkerFiles(skip = new Set()) {
  const files = [];
  for (const entry of readdirSync(PACKS, { withFileTypes: true })) {
    const dir = join(PACKS, entry.name, "worker");
    if (!entry.isDirectory() || skip.has(entry.name) || !existsSync(dir)) continue;
    for (const f of readdirSync(dir)) if (f.endsWith(".py") && !f.startsWith("test_")) files.push(join(dir, f));
  }
  return files;
}
