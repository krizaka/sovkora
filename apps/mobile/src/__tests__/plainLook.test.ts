import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * The product look is the plain Krizaka UI (krizaka-ui#492): sans headings, the system monospace, the brand accent.
 * The notebook identity (« Cahier »: Young Serif, Courier Prime, Caveat, paper, margin ink) belongs to Krizaka's own sites,
 * never to a product app. The native theme carries none of it; this keeps it that way.
 */
const FORBIDDEN = /cahier|young ?serif|courier ?prime|caveat|data-paper|margin ?ink|folio/i;

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (name === "__tests__" || name === "__fixtures__" || name === "node_modules") return [];
    return statSync(path).isDirectory() ? sources(path) : /\.(tsx?|json|js)$/.test(name) ? [path] : [];
  });
}

describe("the plain product look", () => {
  it("no source of the app names the notebook identity", () => {
    const hits = sources(join(__dirname, "..")).filter((file) => FORBIDDEN.test(readFileSync(file, "utf8")));
    expect(hits).toEqual([]);
  });
});
