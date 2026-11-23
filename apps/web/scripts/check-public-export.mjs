// The export check of the public build (krizaka-internal#4625): the routes against scripts/public-routes.mjs, then the
// shared guard of @krizaka/config (`krizaka-export-guard`, krizaka-internal#4623) for everything the pages carry.
import { spawnSync } from "node:child_process";
import { readdirSync, statSync } from "node:fs";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { PUBLIC_LOCALES, PUBLIC_ROUTES } from "./public-routes.mjs";

/** HTML that is not a route: the static host's 404 page of each language (Next writes it twice). */
const NOT_ROUTES = new Set(PUBLIC_LOCALES.flatMap((locale) => [`/${locale}/404`, `/${locale}/_not-found`]));

function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

/** `fr/docs/index.html` and `fr/docs.html` are the route `/fr/docs`. */
export function routeOf(dir, file) {
  const rel = relative(dir, file)
    .replaceAll("\\", "/")
    .replace(/\.html$/, "");
  const route = `/${rel.replace(/(^|\/)index$/, "")}`.replace(/\/$/, "");
  return route === "" ? "/" : route;
}

/** The route problems of an export folder (empty = it holds exactly the public routes). */
export function checkRoutes(dir) {
  const found = files(dir)
    .filter((f) => f.endsWith(".html"))
    .map((f) => routeOf(dir, f))
    .filter((r) => !NOT_ROUTES.has(r));
  const problems = [];
  for (const route of found) {
    if (!PUBLIC_ROUTES.includes(route)) {
      problems.push(`route outside the public list: ${route}`);
    }
  }
  for (const route of PUBLIC_ROUTES) {
    if (!found.includes(route)) {
      problems.push(`missing public route: ${route}`);
    }
  }
  return problems;
}

/** The export folder named on the command line, bounded to the app: a path that leaves it is refused, never read. */
export function exportDirOf(arg, appDir) {
  const dir = resolve(appDir, arg ?? "out-public");
  const rel = relative(appDir, dir);
  if (rel.startsWith("..") || isAbsolute(rel)) {
    throw new Error(`export folder outside the app: ${arg}`);
  }
  return dir;
}

/** The shared guard's bin, in the app's dependencies: run by this node, never looked up on the PATH. */
export const GUARD_BIN = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../node_modules/@krizaka/config/bin/krizaka-export-guard.mjs",
);

/**
 * The argv of `krizaka-export-guard` for Sovkora: its status and the retired code names come from @krizaka/org (the bin cannot
 * import that ESM-only package itself). No amount is allowed: the product is in development, nothing is for sale.
 */
export function guardArgs(dir, orgModule) {
  // The folder is a path inside the app, never an option: only path characters, and `./` in front so a name never reads as a flag.
  if (!/^[\w.][\w.-]*(?:\/[\w.-]+)*$/.test(dir)) {
    throw new Error(`export folder is not a plain relative path: ${dir}`);
  }
  const status = orgModule.org.products.find((p) => p.id === "sovkora")?.status;
  if (!status) {
    throw new Error("sovkora has no status in @krizaka/org");
  }
  const codeNames = Object.keys(orgModule.CODE_NAMES).flatMap((name) => ["--code-name", name]);
  return [`./${dir}`, "--product", "sovkora", "--status", status, ...codeNames];
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const appDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const dir = exportDirOf(process.argv[2], appDir);
  const problems = checkRoutes(dir);
  if (problems.length) {
    console.error(problems.map((p) => `✗ ${p}`).join("\n"));
    process.exit(1);
  }
  const org = await import("@krizaka/org");
  // The bin reads a folder inside its working directory: the app.
  const run = spawnSync(process.execPath, [GUARD_BIN, ...guardArgs(relative(appDir, dir), org)], {
    cwd: appDir,
    stdio: "inherit",
  });
  process.exit(run.status ?? 1);
}
