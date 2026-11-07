#!/usr/bin/env node
/**
 * Publishes Orazaka's documentation on this site (/docs).
 *
 *   node scripts/sync-docs.mjs <orazaka checkout>
 *
 * Reads the markdown of the Orazaka repository (hand-written `docs/*.md`, generated `docs/_generated/*.md`, `SECURITY.md`)
 * listed by `scripts/docs-manifest.mjs`, and writes `content/docs/` (git-tracked, never hand-edited): one markdown file per
 * page, `index.json` (the manifest with its journeys), `model.json` (the slice of `architecture.json` the diagram blocks
 * draw) and `sources.json` (source file → slug, to resolve the links between docs). Fix a wrong or unclear page in the
 * Orazaka repository, then run this again.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { DOCS, SPECIAL } from "./docs-manifest.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/** Front matter off, the `omit`ted sections off. Diagram fences are kept: the site draws them (or reads them as text). */
export function clean(md, omit = []) {
  let out = md.replace(/^---\n[\s\S]*?\n---\n/, "").trimStart();
  if (omit.length) out = omitSections(out, omit);
  return out.replace(/\n{3,}/g, "\n\n");
}

/** Removes every `##` section whose heading starts with one of `prefixes` (numbering and punctuation ignored). */
export function omitSections(md, prefixes) {
  const norm = (s) =>
    s
      .replace(/^#+\s*/, "")
      .replace(/^\d+[.)]?\s*/, "")
      .trim()
      .toLowerCase();
  const wanted = prefixes.map(norm);
  const lines = md.split("\n");
  const out = [];
  let skipping = false;
  let fence = false;
  for (const line of lines) {
    if (/^ {0,3}(```|~~~)/.test(line)) fence = !fence;
    if (!fence && /^## /.test(line)) skipping = wanted.some((w) => norm(line).startsWith(w));
    if (!skipping) out.push(line);
  }
  return out.join("\n");
}

/** The slice of the generated model the diagram blocks read — never the whole 150 kB. */
export function modelSlice(arch) {
  return {
    roles: arch.roles,
    modules: arch.modules.map(({ id, repository, owner, role, layer, runtime, description }) => ({
      id,
      repository,
      owner,
      role,
      layer,
      runtime,
      description,
    })),
    dependencies: arch.dependencies,
    flows: arch.flows,
    pipeline: arch.pipeline,
    coreChain: arch.coreChain,
    cli: { alias: arch.cli.alias, journey: arch.cli.journey },
  };
}

/** The removed public routes, read from src/core/config/redirects.ts (`REMOVED_PUBLIC`): `/gallery`, `/features`, `/models`… */
export function removedPublicRoutes() {
  const ts = fs.readFileSync(path.join(root, "src", "core", "config", "redirects.ts"), "utf8");
  const block = ts.slice(ts.indexOf("REMOVED_PUBLIC"), ts.indexOf("];", ts.indexOf("REMOVED_PUBLIC")));
  return [...new Set([...block.matchAll(/source:\s*"([^"]+)"/g)].map((m) => m[1].replace(/\/:[a-z]+$/, "")))];
}

/** The removed routes a markdown text links (`](/gallery)` or `orazaka.com/gallery/…`), in order, once each. */
export function removedLinks(md) {
  const hits = [];
  for (const route of removedPublicRoutes()) {
    const esc = route.replace(/[.*+?^${}()|[\]\\/]/g, "\\$&");
    if (new RegExp(`(\\]\\(|orazaka\\.com)${esc}(?![\\w-])`).test(md)) hits.push(route);
  }
  return hits.sort((a, b) => md.indexOf(a) - md.indexOf(b));
}

export function sync(srcDir, outDir = path.join(root, "content", "docs")) {
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });
  const index = [];
  for (const d of DOCS) {
    const file = path.join(srcDir, d.source);
    if (!fs.existsSync(file)) throw new Error(`${d.slug}: ${d.source} is not in ${srcDir}`);
    const body = clean(fs.readFileSync(file, "utf8"), d.omit);
    const gone = removedLinks(body);
    if (gone.length)
      throw new Error(
        `${d.slug}: links a removed public route (${gone.join(", ")}) — fix ${d.source} in the Orazaka repository`,
      );
    fs.writeFileSync(path.join(outDir, `${d.slug}.md`), body);
    index.push({
      slug: d.slug,
      journey: d.journey,
      group: d.group ?? null,
      order: d.order,
      title: d.title,
      intro: d.intro,
      audience: d.audience,
      source: d.source,
      generated: Boolean(d.generated),
      clip: d.clip ?? null,
      file: `${d.slug}.md`,
    });
  }
  for (const s of SPECIAL) index.push({ ...s, group: null, source: null, generated: false, clip: null, file: null });
  fs.writeFileSync(path.join(outDir, "index.json"), `${JSON.stringify(index, null, 2)}\n`);
  const arch = JSON.parse(fs.readFileSync(path.join(srcDir, "docs", "_generated", "architecture.json"), "utf8"));
  fs.writeFileSync(path.join(outDir, "model.json"), `${JSON.stringify(modelSlice(arch))}\n`);
  const sources = Object.fromEntries(DOCS.map((d) => [d.source, d.slug]));
  fs.writeFileSync(path.join(outDir, "sources.json"), `${JSON.stringify(sources, null, 2)}\n`);
  return index.length;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [srcDir] = process.argv.slice(2);
  if (!srcDir) {
    console.error("usage: node scripts/sync-docs.mjs <orazaka checkout>");
    process.exit(1);
  }
  process.stdout.write(`${sync(path.resolve(srcDir))} pages published in content/docs\n`);
}
