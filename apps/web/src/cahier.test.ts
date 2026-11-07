// Cahier (krizaka-ui ADR-004, #417): this app's own sources carry none of the template tells the family retired — no
// uppercase tracked label, no frosted backdrop, no pill on a control or a label (a dot or an avatar stays round), no card
// corner of 10 px or more in its stylesheets. The identity itself comes from @krizaka/ui ≥ 3.38 and @krizaka/fonts ≥ 0.3.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = __dirname;
const SKIP = ["node_modules", ".next", "fixtures", "__tests__", "_fonts"];
function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (SKIP.includes(name)) return [];
    if (statSync(path).isDirectory()) return files(path);
    return /\.(tsx|css)$/.test(name) && !/\.(test|stories)\./.test(name) ? [path] : [];
  });
}
const code = (s: string) =>
  s
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, "")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/^\s*\/\/.*$/gm, "");
const sources = ["."]
  .flatMap((d) => {
    try {
      return files(join(ROOT, d));
    } catch {
      return [];
    }
  })
  .map((f) => [f.slice(ROOT.length + 1), code(readFileSync(f, "utf8"))] as const);
const offenders = (re: RegExp) => sources.filter(([, s]) => re.test(s)).map(([f]) => f);
const ROUND = /\b(?:h-([\d.]+) w-\1|size-[\w.[\]-]+|w-([\d.]+) h-\2|h-1\.5 w-1\.5)\b/;

describe("Cahier: no template tell in this app's own sources", () => {
  it("no uppercase tracked label", () => {
    expect(offenders(/["'`\s]uppercase["'`\s]|text-transform:\s*uppercase|textTransform: "uppercase"/)).toEqual([]);
    expect(offenders(/tracking-(wider|widest)|letter-spacing:\s*0?\.(0[4-9]|[1-9])/)).toEqual([]);
  });
  it("no frosted backdrop", () => {
    expect(offenders(/backdrop-blur|backdrop-filter|backdropFilter/)).toEqual([]);
  });
  it("no pill on a control or a label, no round card in the stylesheets", () => {
    const pills = sources.flatMap(([f, s]) =>
      (s.match(/"[^"\n]*rounded-full[^"\n]*"/g) ?? []).filter((c) => !ROUND.test(c)).map((c) => `${f}: ${c}`),
    );
    expect(pills).toEqual([]);
    const corners = sources
      .filter(([f]) => f.endsWith(".css"))
      .flatMap(([f, s]) =>
        [...s.matchAll(/border-radius:\s*(\d+)px/g)]
          .filter((m) => Number(m[1]) >= 10 && Number(m[1]) < 999)
          .map((m) => `${f}: ${m[1]}px`),
      );
    expect(corners).toEqual([]);
  });
});
