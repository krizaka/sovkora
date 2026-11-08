#!/usr/bin/env node
/**
 * Writes `src/core/media/media.generated.json` — the content hashes of the media this site shows from the CDN
 * (the home captures, the mobile recordings and the "Made here" gallery under `sites/orazaka/`).
 *
 *   node scripts/sync-media.mjs <path to a krizaka-com checkout>
 *
 * Read from krizaka.com's `lib/cdn-manifest.json` (where `scripts/cdn-assets.mjs` records an upload), never typed.
 * The media live on Bunny, never here. Never hand-edit the output.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const site = process.argv[2];
if (!site) {
  console.error("usage: node scripts/sync-media.mjs <krizaka-com checkout>");
  process.exit(1);
}
const manifest = JSON.parse(fs.readFileSync(path.join(site, "lib/cdn-manifest.json"), "utf8"));
/** Every product's clips: products/<product>/{gallery-video,video}/<clip>.{mp4,webm}, <clip>-poster.webp. */
const VIDEO = /^sites\/[a-z]+\/(?:gallery-video|video)\/|^sites\/orazaka\/compare\//;
const AREAS = [
  "sites/orazaka/mobile/",
  "sites/orazaka/web/",
  "sites/orazaka/gallery/",
  "sites/orazaka/gallery-video/",
  "sites/orazaka/video/",
  "sites/orazaka/home/",
  "sites/orazaka/showcase/",
  "sites/orazaka/cinema/",
  "sites/orazaka/films/",
];
// The site no longer carries Orazaka media in its manifest once they are mirrored elsewhere: merge over what this
// site already records instead of replacing it, so a sync of one new capture never forgets the others.
const out = path.join(root, "src/core/media/media.generated.json");
const known = fs.existsSync(out) ? JSON.parse(fs.readFileSync(out, "utf8")).files : {};
// The manifest records the logical path (`products/<site>/…`), which lands in layout v2 as `sites/<site>/…` (ADR-014).
const placed = Object.entries(manifest.files).map(([k, hash]) => [k.replace(/^products\//, "sites/"), hash]);
const fresh = Object.fromEntries(placed.filter(([k]) => AREAS.some((a) => k.startsWith(a)) || VIDEO.test(k)));
const files = Object.fromEntries(Object.entries({ ...known, ...fresh }).sort(([a], [b]) => a.localeCompare(b)));
if (!Object.keys(files).length) {
  throw new Error("no Orazaka media in the manifest");
}
fs.writeFileSync(out, `${JSON.stringify({ files }, null, 2)}\n`);
process.stdout.write(`${Object.keys(files).length} media recorded\n`);
