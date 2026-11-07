/**
 * @jest-environment node
 */
import fs from "node:fs";
import path from "node:path";

/** Media live on Bunny (AGENTS.md, krizaka-com §5.2), never in the repository: `public/` holds only the app's own marks (its brand icons, synced from the CDN). */
const ALLOWED = new Set([
  "apple-touch-icon.png",
  "favicon.svg",
  "favicon-32.png",
  "icon-192.png",
  "icon-512.png",
  "manifest.webmanifest",
  "logo.svg",
]);

function files(dir: string): string[] {
  return fs
    .readdirSync(dir, { withFileTypes: true })
    .flatMap((e) => (e.isDirectory() ? files(path.join(dir, e.name)).map((f) => `${e.name}/${f}`) : [e.name]));
}

describe("public/", () => {
  it("holds no media: captures, clips and posters are served from the CDN", () => {
    const stray = files(path.join(process.cwd(), "public")).filter((f) => !ALLOWED.has(f));
    expect(stray).toEqual([]);
  });
});
