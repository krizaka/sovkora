/** @jest-environment node */
import { readdirSync } from "node:fs";
import path from "node:path";
import { APP_SEGMENTS, PUBLIC_PREFIXES } from "@/core/config/app-routes";

const APP = path.join(process.cwd(), "src/app");

/** The first segment of every route folder: `src/app/<x>` and `src/app/(protected)/<x>` (groups are transparent). */
function routeSegments(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .flatMap((e) => (e.name.startsWith("(") ? routeSegments(path.join(dir, e.name)) : [e.name]))
    .filter((name) => !name.startsWith("_") && name !== "console" && name !== "__tests__");
}

describe("the proxy knows every route folder (krizaka/orazaka#443)", () => {
  it("lists each folder of src/app as public or as a page of the app — a new page is added to app-routes.ts", () => {
    const unknown = routeSegments(APP).filter((name) => {
      const address = `/${name}`;
      const isPublic = PUBLIC_PREFIXES.some(
        (prefix) => address === prefix || address.startsWith(prefix) || `${address}/` === prefix,
      );
      return !isPublic && !(APP_SEGMENTS as readonly string[]).includes(name);
    });
    expect(unknown).toEqual([]);
  });

  it("lists no page of the app that has no folder", () => {
    const folders = routeSegments(APP);
    expect(APP_SEGMENTS.filter((s) => !folders.includes(s))).toEqual([]);
  });
});
