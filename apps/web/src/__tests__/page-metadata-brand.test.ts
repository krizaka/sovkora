/** @jest-environment node */
import { readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";

/**
 * krizaka-internal#3935: `<title>Documentation — {brand}</title>` was live: a page took its metadata from a raw
 * dictionary instead of `translations` (which passes the messages through `withBrand`). Every page and layout of the
 * app is read here, so a new one cannot bring the placeholder back; the built text files are checked by
 * `scripts/check-code-names.mjs`.
 */
/** Markdown is ESM-only and draws nothing the metadata reads. */
jest.mock("react-markdown", () => ({ __esModule: true, default: () => null }));
jest.mock("remark-gfm", () => ({ __esModule: true, default: () => undefined }));

const APP = join(__dirname, "..", "app");

function modules(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return name === "__tests__" ? [] : modules(path);
    }
    return /^(page|layout)\.tsx$/.test(name) ? [path] : [];
  });
}

describe("no {brand} placeholder in a page's metadata", () => {
  for (const file of modules(APP)) {
    const name = relative(APP, file);
    it(name, async () => {
      const mod: { metadata?: unknown } = await import(file);
      expect(JSON.stringify(mod.metadata ?? {})).not.toContain("{brand}");
    });
  }
});
