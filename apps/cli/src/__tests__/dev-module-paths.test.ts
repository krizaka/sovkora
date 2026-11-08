/**
 * @file dev-module-paths.test.ts
 * @description The Krizaka services `orazaka dev` runs are modules of krizaka-java, read from its checkout at the
 * release sovkora-parent imports (`.krizaka-java`, ADR-029): a path outside it is not a module and the service never starts.
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { KRIZAKA_BUILD_MODULES } from "../commands/dev.command";

describe("the krizaka-java modules of `orazaka dev`", () => {
  test("each one is under the krizaka-java checkout", () => {
    for (const module of Object.values(KRIZAKA_BUILD_MODULES)) {
      expect(module.startsWith(".krizaka-java/")).toBe(true);
    }
  });

  test("no -pl names a split repository folder", () => {
    const source = readFileSync(path.join(__dirname, "../commands/dev.command.ts"), "utf8");
    expect(source).not.toMatch(/"krizaka\/krizaka-(users|billing|platform-kit)\//);
  });
});
