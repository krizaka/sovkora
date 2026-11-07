// MANUAL TOOL (ADR-026), run by a person; owner: sovkora-web lane; listed in README.md « Manual tools ».
// Why: a short clip of one Studio run's page on dev, signed in as the demo persona (credentials read through secret.sh, never printed). How: `node scripts/record-dev-clip.mjs <run-id>`.
import { chromium } from "@playwright/test";
import { execFileSync, execSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import os from "node:os";
import path from "node:path";
const sh = (k) =>
  execSync(`~/.krizaka-worktrees/tools/secret.sh ~/Documents/krizaka/qa.env ${k}`, { shell: "/bin/zsh" }).toString();
const RUN = process.argv[2];
const raw = mkdtempSync(path.join(os.tmpdir(), "devclip-"));
const b = await chromium.launch();
const V = { width: 1120, height: 700 };
const signed = await b.newContext({ viewport: V, deviceScaleFactor: 2, colorScheme: "dark" });
const p0 = await signed.newPage();
await p0.goto("https://www.orazaka.com/login");
await p0.fill('input[type="email"]', sh("ORAZAKA_USER_EMAIL"));
await p0.fill('input[type="password"]', sh("ORAZAKA_USER_PASSWORD"));
await p0.click('button[type="submit"]');
await p0.waitForURL("https://www.orazaka.com/", { timeout: 60000 });
const state = await signed.storageState();
await signed.close();
const ctx = await b.newContext({
  viewport: V,
  deviceScaleFactor: 2,
  colorScheme: "dark",
  storageState: state,
  recordVideo: { dir: raw, size: V },
});
const page = await ctx.newPage();
const t0 = Date.now();
await page.goto(`https://www.orazaka.com/studios/runs/${RUN}`, { waitUntil: "networkidle" });
await page
  .waitForFunction(() => [...document.querySelectorAll("video")].every((v) => v.readyState >= 3), null, {
    timeout: 30000,
  })
  .catch(() => {});
await page.waitForTimeout(1200);
const start = (Date.now() - t0) / 1000;
await page.waitForTimeout(1500);
const play = page.getByRole("button", { name: /^Play/ }).first();
if (await play.count()) await play.click().catch(() => {});
else
  await page
    .locator("video")
    .first()
    .evaluate((v) => v.play())
    .catch(() => {});
await page.waitForTimeout(7500);
const video = page.video();
await ctx.close();
const src = await video.path();
const out = process.argv[3];
const trim = [
  "-y",
  "-loglevel",
  "error",
  "-ss",
  start.toFixed(2),
  "-i",
  src,
  "-vf",
  "fps=25,scale=1440:900:flags=lanczos",
  "-an",
];
execFileSync("ffmpeg", [
  ...trim,
  "-c:v",
  "libvpx-vp9",
  "-b:v",
  "0",
  "-crf",
  "42",
  "-row-mt",
  "1",
  "-deadline",
  "good",
  "-cpu-used",
  "2",
  `${out}.webm`,
]);
execFileSync("ffmpeg", [
  ...trim,
  "-c:v",
  "libx264",
  "-crf",
  "28",
  "-preset",
  "slow",
  "-pix_fmt",
  "yuv420p",
  "-movflags",
  "+faststart",
  `${out}.mp4`,
]);
execFileSync("ffmpeg", [
  "-y",
  "-loglevel",
  "error",
  "-ss",
  "5",
  "-i",
  `${out}.mp4`,
  "-frames:v",
  "1",
  "-q:v",
  "4",
  `${out}.jpg`,
]);
await b.close();
process.stdout.write("ok\n");
