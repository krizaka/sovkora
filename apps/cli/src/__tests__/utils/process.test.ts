/**
 * @file process.test.ts
 * @description Verifies the pure output-noise filter shared by the prefixed
 * spawner (utils/process). The spawning itself is covered by the command flows.
 */

import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { spawn } from "node:child_process";
import { isNoiseLine, spawnDetachedToLog, stopMatching } from "../../utils/process";

describe("isNoiseLine", () => {
  test("flags npm script echo lines as noise", () => {
    expect(isNoiseLine("> orazaka-web-client@1.0.0 dev")).toBe(true);
    expect(isNoiseLine("> next dev --port 3000")).toBe(true);
  });

  test("flags blank/whitespace lines as noise", () => {
    expect(isNoiseLine("")).toBe(true);
    expect(isNoiseLine("    ")).toBe(true);
  });

  test("keeps real log content", () => {
    expect(isNoiseLine("Compiled successfully in 1.2s")).toBe(false);
    expect(isNoiseLine("Started RouterApplication in 4.3s")).toBe(false);
  });
});

describe("spawnDetachedToLog", () => {
  // Regression guard: passing a freshly-created fs.createWriteStream to spawn's
  // stdio throws "stdio is invalid (fd null)" because its fd opens async — the
  // workers never started. We must open a real fd first.
  test("spawns without throwing and routes output to the log file", async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "orazaka-spawn-"));
    const logFile = path.join(dir, "worker.log");

    const child = spawnDetachedToLog(process.execPath, ["-e", "process.stdout.write('hello-spawn')"], { logFile });
    expect(child).toBeDefined();

    await new Promise((r) => setTimeout(r, 700));
    expect(fs.readFileSync(logFile, "utf-8")).toContain("hello-spawn");
  });
});

describe("stopMatching", () => {
  test("stops the process whose command line matches, through pkill from a fixed folder", async () => {
    const marker = `orazaka-stop-matching-${String(process.pid)}`;
    const child = spawn(process.execPath, ["-e", "setInterval(() => {}, 1000)", marker], { stdio: "ignore" });
    const exited = new Promise<NodeJS.Signals | null>((resolve) =>
      child.on("exit", (_code, signal) => resolve(signal)),
    );
    await new Promise((r) => setTimeout(r, 300));

    stopMatching(marker);

    await expect(exited).resolves.toBe("SIGTERM");
  });

  test("never throws when nothing matches, as `pkill … || true` did", () => {
    expect(() => stopMatching(`orazaka-nothing-runs-like-this-${String(Date.now())}`)).not.toThrow();
  });
});
