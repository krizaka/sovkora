// pnpm build:public — the public static export of Sovkora (krizaka-internal#4625).
// It stages a Next project per language (apps/web/.public-build/<locale>) whose `app/` links the REAL page files of the public routes (never copies),
// builds each with `output: "export"` under its base path and leaves the results in apps/web/out-public/<locale>. The dev app and its build are untouched.
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  PUBLIC_LOCALES,
  PUBLIC_SHARED_ENTRIES,
  STAGED_FILES,
  publicDocsIndex,
  publicDocsSources,
} from "./public-routes.mjs";
import { checkRoutes } from "./check-public-export.mjs";

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const STAGES = join(WEB, ".public-build");
const OUT = join(WEB, "out-public");

/** A relative link: the staged folder follows the repository wherever it is checked out. */
function link(target, at) {
  mkdirSync(dirname(at), { recursive: true });
  symlinkSync(relative(dirname(at), target), at);
}

/**
 * `content/`: every file linked, but the docs index and the map of sources, which list the published pages only (the bar, the hub, the
 * search, the previous/next links and the links between pages all read them). The pages' files stay: the home counts its figures from the measured runs.
 */
function stageContent(stage) {
  const docs = join(WEB, "content", "docs");
  for (const entry of readdirSync(join(WEB, "content"))) {
    if (entry !== "docs") {
      link(join(WEB, "content", entry), join(stage, "content", entry));
    }
  }
  for (const entry of readdirSync(docs)) {
    if (entry !== "index.json" && entry !== "sources.json") {
      link(join(docs, entry), join(stage, "content", "docs", entry));
    }
  }
  const index = publicDocsIndex(JSON.parse(readFileSync(join(docs, "index.json"), "utf8")));
  writeFileSync(join(stage, "content", "docs", "index.json"), `${JSON.stringify(index, null, 2)}\n`);
  const sources = publicDocsSources(JSON.parse(readFileSync(join(docs, "sources.json"), "utf8")));
  writeFileSync(join(stage, "content", "docs", "sources.json"), `${JSON.stringify(sources, null, 2)}\n`);
}

function stage(dir) {
  mkdirSync(dir, { recursive: true });
  // Everything but Next's own cache (`.next`) is rebuilt: the links follow the list of routes, and the cache keeps the next build quick.
  for (const entry of readdirSync(dir)) {
    if (entry !== ".next") {
      rmSync(join(dir, entry), { recursive: true, force: true });
    }
  }
  for (const { at, from } of STAGED_FILES) {
    link(join(WEB, from), join(dir, "app", at));
  }
  for (const entry of PUBLIC_SHARED_ENTRIES) {
    link(join(WEB, entry), join(dir, entry));
  }
  stageContent(dir);
  link(join(WEB, "next.config.public.mjs"), join(dir, "next.config.mjs"));
  link(join(WEB, "node_modules"), join(dir, "node_modules"));
  // The app's own tsconfig, narrowed to the staged pages (its `@/*` paths still resolve in apps/web): the type check reads what is exported.
  writeFileSync(
    join(dir, "tsconfig.json"),
    JSON.stringify(
      {
        extends: "../../tsconfig.json",
        include: ["next-env.d.ts", "app/**/*.ts", "app/**/*.tsx"],
        exclude: ["node_modules"],
      },
      null,
      2,
    ),
  );
}

function build(dir, locale) {
  const next = join(WEB, "node_modules", ".bin", "next");
  execFileSync(next, ["build"], {
    cwd: dir,
    stdio: "inherit",
    env: { ...process.env, NODE_ENV: "production", NEXT_TELEMETRY_DISABLED: "1", NEXT_PUBLIC_LOCALE: locale },
  });
}

rmSync(OUT, { recursive: true, force: true });
for (const locale of PUBLIC_LOCALES) {
  const dir = join(STAGES, locale);
  stage(dir);
  build(dir, locale);
  if (!existsSync(join(dir, "out"))) {
    throw new Error(`the ${locale} export produced no out/ folder`);
  }
  cpSync(join(dir, "out"), join(OUT, locale), { recursive: true });
}
// The icons, the manifest and the logo are addressed from the root (`/favicon.svg`): one copy there, shared by both languages.
cpSync(join(WEB, "public"), OUT, { recursive: true });
const problems = checkRoutes(OUT);
if (problems.length) {
  console.error(problems.map((p) => `✗ ${p}`).join("\n"));
  process.exit(1);
}
process.stdout.write(`public export ready: ${relative(process.cwd(), OUT) || OUT}\n`);
