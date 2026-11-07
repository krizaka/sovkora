#!/usr/bin/env node
/* check-app-links — universal links / Android App Links are a CI rule, not a good intention.
   One dependency-free file, copied UNCHANGED into every repository that has a web app and/or a native app
   (krizaka/.github scripts/check-app-links.mjs is the source; keep the copies identical).

   Config (JSON, `app-links.json` at the repository root):
     { "host": "dev.yomizaka.com", "iosBundleId": "com.krizaka.yomizaka", "androidPackage": "com.krizaka.yomizaka",
       "paths": ["/@*", "/watch/*", "/me", "/me/*"],            // what the AASA and the Android intent filters claim
       "web": { "appDir": "apps/web/app", "extra": ["/@[username]"], "excluded": ["/auth/*", "/api/*", "/[locale]/[...rest]"],
                "aasaRoute": "apps/web/app/.well-known/apple-app-site-association/route.ts",
                "assetlinksRoute": "apps/web/app/.well-known/assetlinks.json/route.ts" },
       "native": { "appJson": "app.json", "appDir": "src/app" } }

   Modes
     web    (repo has "web")    node scripts/check-app-links.mjs [--config app-links.json] [--write-routes routes.json]
            1 every shareable web route is covered by a claimed path, and every claimed path is a real route;
            2 the AASA and assetlinks routes exist; routes.json (committed) is up to date.
     native (repo has "native") node scripts/check-app-links.mjs --routes <routes.json|url>
            3 app.json: iOS associatedDomains applinks:<host>; an Android intent filter with autoVerify and the host,
              covering every claimed path; 4 every claimed path has a screen in the Expo Router tree.
     live   node scripts/check-app-links.mjs --live https://dev.yomizaka.com [--no-cdn]
            5 both files: 200, application/json, no redirect, valid, bundle id and package right, Apple's CDN
              and Google's Digital Asset Links API agree.
   Exit 1 with one line per gap. Needs Node 20+. Prints no secret. */
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

const PAGE = /^page\.(tsx|ts|jsx|js|mdx)$/;

/** A pattern (`/watch/*`, `/@*`) as a regular expression. `*` matches any characters, also across segments. */
export function globToRegExp(glob) {
  return new RegExp("^" + glob.replace(/[.+?^${}()|\\]/g, "\\$&").replace(/\*/g, ".*") + "$");
}

/** `/watch/[id]` → `/watch/*`, `/docs/[...slug]` → `/docs/*`. */
export function normalizeRoute(route) {
  return route.replace(/\[\[?\.\.\.[^\]]+\]?\]/g, "*").replace(/\[[^\]]+\]/g, "*") || "/";
}

/**
 * A catch-all page that does nothing but answer 404 (`app/[locale]/[...rest]/page.tsx` → `notFound()`), there so that an
 * unmatched address gets the app's own not-found page inside its layout. It is no shareable route.
 */
export function isNotFoundOnly(source) {
  const code = source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
  return /^\s*(import\s[^;]+;\s*)*export\s+default\s+(async\s+)?function\s+\w*\s*\([^)]*\)\s*\{\s*notFound\(\)\s*;?\s*\}\s*$/.test(
    code,
  );
}

/** Next.js `app/` tree → routes, groups `(x)` and slots `@x` removed, only folders holding a page (a not-found-only catch-all is no route). */
export function nextRoutes(appDir) {
  const out = [];
  const walk = (dir, segs) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        if (name.startsWith("_") || name === "node_modules") continue;
        const hidden = /^\(.*\)$/.test(name) || name.startsWith("@");
        walk(full, hidden ? segs : [...segs, name]);
      } else if (PAGE.test(name)) {
        if (/^\[\[?\.\.\./.test(segs.at(-1) ?? "") && isNotFoundOnly(readFileSync(full, "utf8"))) continue;
        out.push("/" + segs.join("/"));
      }
    }
  };
  if (existsSync(appDir)) walk(appDir, []);
  return [...new Set(out)].sort();
}

/** Expo Router tree → route patterns (`index` and `_layout` folded, groups removed, `+native-intent`/`+not-found` ignored). */
export function expoRoutes(appDir) {
  const out = [];
  const walk = (dir, segs) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) walk(full, /^\(.*\)$/.test(name) ? segs : [...segs, name]);
      else {
        const m = /^(.*)\.(tsx|ts|jsx|js)$/.exec(name);
        if (!m || m[1].startsWith("_") || m[1].startsWith("+") || /\.(test|spec)$/.test(m[1])) continue;
        out.push("/" + [...segs, ...(m[1] === "index" ? [] : [m[1]])].join("/"));
      }
    }
  };
  if (existsSync(appDir)) walk(appDir, []);
  return [...new Set(out.map((r) => r.replace(/\/$/, "") || "/"))].sort();
}

/** Does an Expo route (`/watch/[id]`, `/me/[...rest]`) open for this concrete path? */
export function expoMatches(expoRoute, path) {
  const re = expoRoute
    .split("/")
    .map((s) =>
      /^\[\.\.\.[^\]]+\]$/.test(s) ? ".+" : /^\[[^\]]+\]$/.test(s) ? "[^/]+" : s.replace(/[.+?^${}()|\\*]/g, "\\$&"),
    )
    .join("/");
  return new RegExp("^" + re + "$").test(path);
}

/** A claimed path, made concrete so it can be tested against screens (`/watch/*` → `/watch/x`). */
const concrete = (p) => p.replace(/\*/g, "x");

/** Rule 1 — web routes against claimed paths. Returns the gaps. */
export function checkWebRoutes({ routes, paths, excluded = [], extra = [] }) {
  const gaps = [];
  // An exclusion written with brackets (`/[locale]/[...rest]`) is that route itself, compared before normalising: as a
  // glob, its normalised form (`/*/*`) would match every route.
  const literal = new Set(excluded.filter((e) => e.includes("[")));
  const all = [...new Set([...routes, ...extra].filter((r) => !literal.has(r)).map(normalizeRoute))];
  const skip = excluded.filter((e) => !e.includes("[")).map(globToRegExp);
  const claims = paths.map(globToRegExp);
  const shareable = all.filter((r) => !skip.some((re) => re.test(r)));
  for (const r of shareable) {
    const probe = concrete(r);
    if (!claims.some((re) => re.test(probe)))
      gaps.push(
        `web route ${r} is shareable but no AASA / intent-filter path claims it (add a path, or exclude the route with a reason)`,
      );
  }
  for (const p of paths) {
    const re = globToRegExp(p);
    if (!shareable.some((r) => re.test(concrete(r)))) gaps.push(`claimed path ${p} matches no shareable web route`);
  }
  return { gaps, shareable };
}

const asArray = (v) => (Array.isArray(v) ? v : v ? [v] : []);

/** Rule 3 — app.json. */
export function checkAppJson(app, { host, iosBundleId, androidPackage, paths }) {
  const gaps = [];
  const expo = app?.expo ?? app ?? {};
  if (expo.ios?.bundleIdentifier !== iosBundleId) gaps.push(`app.json ios.bundleIdentifier is not ${iosBundleId}`);
  if (expo.android?.package !== androidPackage) gaps.push(`app.json android.package is not ${androidPackage}`);
  if (!asArray(expo.ios?.associatedDomains).includes(`applinks:${host}`))
    gaps.push(`app.json ios.associatedDomains lacks applinks:${host}`);
  const filters = asArray(expo.android?.intentFilters).filter(
    (f) => f.autoVerify === true && asArray(f.action).includes("VIEW"),
  );
  const data = filters.flatMap((f) => asArray(f.data)).filter((d) => d.scheme === "https" && d.host === host);
  if (!filters.length) gaps.push("app.json android.intentFilters has no VIEW filter with autoVerify: true");
  else if (!data.length) gaps.push(`app.json android.intentFilters: no https data with host ${host}`);
  for (const p of paths) {
    const stem = p.replace(/\*$/, "");
    const ok = data.some(
      (d) =>
        d.path === p ||
        (d.pathPrefix && stem.startsWith(d.pathPrefix)) ||
        (d.pathPattern && globToRegExp(d.pathPattern.replace(/\.\*/g, "*")).test(concrete(p))),
    );
    if (!ok) gaps.push(`app.json Android intent filters do not cover ${p}`);
  }
  return gaps;
}

/** Rule 4 — every claimed path opens a screen. `/@…` needs `+native-intent` (Expo cannot route a segment starting with @). */
export function checkNativeScreens({ screens, paths, nativeIntent }) {
  const gaps = [];
  for (const p of paths) {
    if (p.startsWith("/@")) {
      if (!nativeIntent) gaps.push(`${p}: no +native-intent file to rewrite /@handle to a screen`);
      continue;
    }
    if (!screens.some((s) => expoMatches(s, concrete(p))))
      gaps.push(`claimed path ${p} has no screen in the Expo Router tree`);
  }
  return gaps;
}

/** Rule 5 — the live files. `fetchImpl` is injectable for the tests. */
export async function checkLive(base, config, { fetchImpl = fetch, cdn = true } = {}) {
  const gaps = [];
  const host = new URL(base).host;
  const get = (url) => fetchImpl(url, { redirect: "manual", signal: AbortSignal.timeout(20000) });
  const file = async (name) => {
    const url = `${base}/.well-known/${name}`;
    let res;
    try {
      res = await get(url);
    } catch (e) {
      gaps.push(`${url} unreachable (${e.message})`);
      return null;
    }
    if (res.status !== 200) {
      gaps.push(
        `${url} answers ${res.status}${res.status >= 300 && res.status < 400 ? " (a redirect: Apple and Google refuse it)" : ""}, expected 200`,
      );
      return null;
    }
    if (!/^application\/json/i.test(res.headers.get("content-type") ?? ""))
      gaps.push(`${url} content-type is "${res.headers.get("content-type")}", expected application/json`);
    try {
      return await res.json();
    } catch {
      gaps.push(`${url} is not valid JSON`);
      return null;
    }
  };
  const aasa = await file("apple-app-site-association");
  if (aasa) {
    const details = aasa.applinks?.details ?? [];
    const ids = details.flatMap((d) => asArray(d.appIDs ?? d.appID));
    if (!ids.some((id) => /^[A-Z0-9]{10}\./.test(id) && id.endsWith(`.${config.iosBundleId}`)))
      gaps.push(`AASA has no appID <TeamID>.${config.iosBundleId}`);
    const claimed = details
      .flatMap((d) => [...asArray(d.paths), ...asArray(d.components).map((c) => c["/"])])
      .filter(Boolean);
    for (const p of config.paths) if (!claimed.includes(p)) gaps.push(`AASA does not claim ${p}`);
  }
  const links = await file("assetlinks.json");
  let fingerprints = [];
  if (links) {
    const entry = (Array.isArray(links) ? links : []).find((e) => e.target?.package_name === config.androidPackage);
    fingerprints = entry?.target?.sha256_cert_fingerprints ?? [];
    if (!entry) gaps.push(`assetlinks.json has no target for ${config.androidPackage}`);
    else if (!fingerprints.length || !fingerprints.every((f) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(f)))
      gaps.push("assetlinks.json: sha256_cert_fingerprints missing or malformed");
  }
  if (cdn && aasa) {
    try {
      const res = await fetchImpl(`https://app-site-association.cdn-apple.com/a/v1/${host}`, {
        signal: AbortSignal.timeout(20000),
      });
      if (res.status !== 200)
        gaps.push(`Apple's CDN has no file for ${host} (status ${res.status}); redeploy, then retry in a few minutes`);
    } catch (e) {
      gaps.push(`Apple's CDN unreachable (${e.message})`);
    }
  }
  if (cdn && fingerprints.length) {
    const q = new URLSearchParams({
      "source.web.site": `https://${host}`,
      relation: "delegate_permission/common.handle_all_urls",
      "target.androidApp.packageName": config.androidPackage,
      "target.androidApp.certificate.sha256Fingerprint": fingerprints[0],
    });
    try {
      const res = await fetchImpl(`https://digitalassetlinks.googleapis.com/v1/assetlinks:check?${q}`, {
        signal: AbortSignal.timeout(20000),
      });
      const body = res.ok ? await res.json() : {};
      if (body.linked !== true)
        gaps.push("Google's Digital Asset Links API does not report the site linked to the app");
    } catch (e) {
      gaps.push(`Google's Digital Asset Links API unreachable (${e.message})`);
    }
  }
  return gaps;
}

const readJson = (p) => JSON.parse(readFileSync(p, "utf8"));

async function loadRoutes(src) {
  if (/^https?:\/\//.test(src)) {
    const token = process.env.GITHUB_TOKEN;
    const res = await fetch(src, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    if (!res.ok) throw new Error(`cannot fetch ${src}: ${res.status}`);
    return res.json();
  }
  return readJson(src);
}

export async function main(argv) {
  const arg = (n) => (argv.includes(n) ? argv[argv.indexOf(n) + 1] : undefined);
  const root = resolve(arg("--root") ?? ".");
  const config = readJson(resolve(root, arg("--config") ?? "app-links.json"));
  const gaps = [];
  if (arg("--live")) {
    gaps.push(...(await checkLive(arg("--live").replace(/\/$/, ""), config, { cdn: !argv.includes("--no-cdn") })));
  } else {
    if (config.web) {
      const routes = nextRoutes(resolve(root, config.web.appDir));
      const { gaps: g, shareable } = checkWebRoutes({
        routes,
        paths: config.paths,
        excluded: config.web.excluded,
        extra: config.web.extra,
      });
      gaps.push(...g);
      for (const r of ["aasaRoute", "assetlinksRoute"])
        if (!existsSync(resolve(root, config.web[r])))
          gaps.push(`${config.web[r]} does not exist: the file is not served`);
      const manifest = {
        host: config.host,
        iosBundleId: config.iosBundleId,
        androidPackage: config.androidPackage,
        paths: config.paths,
        shareable,
      };
      const out = resolve(root, arg("--write-routes") ?? config.web.routesFile ?? "routes.json");
      if (argv.includes("--write-routes")) writeFileSync(out, JSON.stringify(manifest, null, 2) + "\n");
      else if (!existsSync(out) || JSON.stringify(readJson(out)) !== JSON.stringify(manifest))
        gaps.push(
          `${out.replace(root + "/", "")} is missing or out of date: run with --write-routes ${out.replace(root + "/", "")} and commit it`,
        );
    }
    if (config.native) {
      const web = await loadRoutes(arg("--routes") ?? config.native.routes);
      const paths = web.paths;
      const appDir = resolve(root, config.native.appDir);
      gaps.push(
        ...checkAppJson(readJson(resolve(root, config.native.appJson)), {
          host: web.host,
          iosBundleId: web.iosBundleId,
          androidPackage: web.androidPackage,
          paths,
        }),
      );
      const nativeIntent = ["ts", "tsx", "js"].some((e) => existsSync(join(appDir, `+native-intent.${e}`)));
      gaps.push(...checkNativeScreens({ screens: expoRoutes(appDir), paths, nativeIntent }));
    }
  }
  if (gaps.length) {
    for (const g of gaps) console.error(`GAP ${g}`);
    console.error(`check-app-links: ${gaps.length} gap(s)`);
    return 1;
  }
  console.log("check-app-links: ok");
  return 0;
}

if (import.meta.url === `file://${process.argv[1]}`) process.exit(await main(process.argv.slice(2)));
