/** @jest-environment node */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { findCodeNames } from "@krizaka/org";

/**
 * The Sovkora CLI is `sovkora`, no alias left (krizaka-internal#3935, slice of #2684). The retired code name may not
 * name the package, the command, what the CLI prints, or what the public pages and READMEs teach to type. Internal
 * identifiers (a Java package, a database, a docker project, a model id) are glued to other characters and are not
 * the name of the product: only the bare word counts, found by `findCodeNames` of @krizaka/org.
 */
const ROOT = join(__dirname, "../../../..");

/** The bare word: not glued to an identifier on either side (`com.krizaka.orazaka`, `orazaka_db`, `orazaka-network`). */
const BARE = /(?<![\w@#$/.-])orazaka(?![\w#$/_-]|\.\w)/gi;

/** What the CLI keeps on purpose, each with its reason: the docker project of the local stack, and generator literals. */
const KEPT: { file: RegExp; line: RegExp; why: string }[] = [
  {
    file: /^apps\/cli\/src\//,
    line: /-p orazaka\b/,
    why: "the docker compose project of an existing local stack: renaming it would orphan its volumes",
  },
  {
    file: /^apps\/cli\/src\/services\/templates\.ts$/,
    line: /./,
    why: "the Java package and property prefix the code generator writes (com.krizaka.orazaka, orazaka.*)",
  },
  {
    file: /^apps\/cli\/src\/commands\/generate\.command\.ts$/,
    line: /^\s*"orazaka",$/,
    why: "a path segment of the generated Java package",
  },
  {
    file: /^apps\/cli\/src\/services\/pack\.bundle\.ts$/,
    line: /publisherId/,
    why: "the publisher id recorded in existing packs",
  },
];

function walk(dir: string, keep: (path: string) => boolean): string[] {
  if (!existsSync(dir)) {
    return [];
  }
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (name === "node_modules" || name === "dist" || name === "__tests__") {
      return [];
    }
    return statSync(path).isDirectory() ? walk(path, keep) : keep(path) ? [path] : [];
  });
}

function hitsOf(files: string[]): string[] {
  return files.flatMap((file) => {
    const rel = relative(ROOT, file);
    return readFileSync(file, "utf8")
      .split("\n")
      .flatMap((line, i) => {
        const kept = KEPT.some((k) => k.file.test(rel) && k.line.test(line));
        const words = kept ? [] : (line.match(BARE) ?? []);
        return words.flatMap((w) => findCodeNames(w).map((h) => `${rel}:${i + 1}: ${h.word} -> ${h.use}`));
      });
  });
}

const TEXT = /\.(ts|tsx|mjs|js|json|md|yml|yaml)$/;

describe("the Sovkora CLI is `sovkora`, no alias left", () => {
  it("names its package, its bin and its links without the code name", () => {
    const pkg = JSON.parse(readFileSync(join(ROOT, "apps/cli/package.json"), "utf8")) as {
      name: string;
      bin: Record<string, string>;
    };
    expect(pkg.name).toBe("sovkora");
    expect(Object.keys(pkg.bin)).toEqual(["sovkora"]);
    expect(findCodeNames(JSON.stringify(pkg))).toEqual([]);
  });

  it("has no alias package", () => {
    expect(existsSync(join(ROOT, "apps/cli/alias"))).toBe(false);
  });

  it("prints, runs and documents `sovkora` only", () => {
    const files = [
      ...walk(join(ROOT, "apps/cli"), (p) => TEXT.test(p) && !/pnpm-lock/.test(p)),
      ...walk(join(ROOT, "apps/web/content/docs"), (p) => /\.md$/.test(p)),
      ...["apps/web/README.md", "README.md", "SECURITY.md"].map((p) => join(ROOT, p)),
      join(ROOT, "apps/web/src/core/context/translations.desktop.ts"),
      join(ROOT, "apps/web/src/app/docs/page.tsx"),
    ];
    expect(hitsOf(files)).toEqual([]);
  });
});
