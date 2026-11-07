/**
 * @jest-environment node
 */
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import path from "node:path";

// Anti-drift guard: every committed icon is the CDN file of the brand, byte for byte
// (`node scripts/sync-brand-icons.mjs` re-downloads them; the map below is the script's ICONS).
const CDN = "https://krizaka-assets.b-cdn.net";
const BRAND = "sovkora";
const ICONS: Record<string, string> = {
  "favicon.svg": "public/favicon.svg",
  "favicon-32.png": "public/favicon-32.png",
  "apple-touch-icon.png": "public/apple-touch-icon.png",
  "icon-192.png": "public/icon-192.png",
  "icon-512.png": "public/icon-512.png",
};

async function fetchRetry(url: string, tries = 5): Promise<Buffer> {
  let last: unknown;
  for (let i = 0; i < tries; i++) {
    try {
      const res = await fetch(url);
      if (res.ok) return Buffer.from(await res.arrayBuffer());
      last = new Error(`${url}: HTTP ${res.status}`);
    } catch (e) {
      last = e;
    }
    await new Promise((r) => setTimeout(r, 500 * 2 ** i));
  }
  throw last;
}

describe(`brand icons (${BRAND})`, () => {
  it("lists the same files as scripts/sync-brand-icons.mjs", () => {
    const script = readFileSync(path.join(process.cwd(), "scripts/sync-brand-icons.mjs"), "utf8");
    for (const [file, dest] of Object.entries(ICONS)) expect(script).toContain(`"${file}": "${dest}"`);
  });

  it("each committed icon matches the CDN brand manifest", async () => {
    const manifest = JSON.parse((await fetchRetry(`${CDN}/brand/manifest.json`)).toString("utf8")) as {
      files: { path: string; sha256: string }[];
    };
    const hashes = Object.fromEntries(manifest.files.map((f) => [f.path, f.sha256]));
    for (const [file, dest] of Object.entries(ICONS)) {
      const expected = hashes[`brand/${BRAND}/${file}`];
      expect(expected).toBeTruthy();
      const have = createHash("sha256")
        .update(readFileSync(path.join(process.cwd(), dest)))
        .digest("hex");
      expect({ dest, have }).toEqual({ dest, have: expected });
    }
  }, 60_000);
});
