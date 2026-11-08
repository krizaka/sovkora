/**
 * @jest-environment node
 */
import fs from "node:fs";
import path from "node:path";

const read = (p: string) => fs.readFileSync(path.join(process.cwd(), p), "utf8");

describe("the landing never hides its content", () => {
  it("without JavaScript every [data-reveal] block is visible", () => {
    expect(read("src/app/layout.tsx")).toMatch(/<noscript>[\s\S]*\[data-reveal\]\{opacity:1!important/);
  });
});
