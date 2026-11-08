// The web steps of the home's « Where it runs » tour — the real app on www.orazaka.com, signed in as the QA user (the
// account the phone recordings use), nothing staged and no new generation: the workspace, a Studio's page with its
// estimate before the run, then what was already made. Dark and light, 1120×700, ≤ 7 s each, muted.
//
//   node scripts/record-web-steps.mjs <out dir>       # then: krizaka-com scripts/cdn-assets.mjs upload <out> --to sites/orazaka/web
//
// Credentials come from the local QA store through secret.sh — never printed, never in a file here. A demo shows a
// persona, never a QA name: the account itself carries the cast's name (krizaka/orazaka#473), and a take with « qa » on screen is refused.
import { chromium } from "@playwright/test";
import { execFileSync, execSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readdirSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import sharp from "sharp";
import { qaOnScreen } from "./qa-on-screen.mjs";

const BASE = process.env.APP_BASE_URL ?? "https://www.orazaka.com";
const OUT = path.resolve(process.argv[2] ?? "web-steps");
const V = { width: 1120, height: 700 };
const secret = (k) => execSync(`~/.krizaka-worktrees/tools/secret.sh @qa ${k}`, { shell: "/bin/zsh" }).toString();

/** Each step: what it does once the page has settled; the clip starts there. */
const STEPS = {
  workspace: async (p) => {
    await p.goto(`${BASE}/`, { waitUntil: "networkidle" });
    return async () => {
      await p.waitForTimeout(1600);
      await p.mouse.wheel(0, 360);
      await p.waitForTimeout(2600);
    };
  },
  studio: async (p) => {
    await p.goto(`${BASE}/studios`, { waitUntil: "networkidle" });
    return async () => {
      await p.waitForTimeout(1500);
      await p.locator('a[href="/studios/image-generation"]').first().click();
      await p.waitForLoadState("networkidle");
      await p.waitForTimeout(3200);
    };
  },
  results: async (p) => {
    await p.goto(`${BASE}/dashboard/jobs`, { waitUntil: "networkidle" });
    await p
      .waitForFunction(() => [...document.querySelectorAll("main img")].slice(0, 6).every((i) => i.complete), null, {
        timeout: 20000,
      })
      .catch(() => {});
    return async () => {
      await p.waitForTimeout(1400);
      await p
        .locator("main img")
        .first()
        .hover()
        .catch(() => {});
      await p.waitForTimeout(700);
      await p
        .getByRole("button", { name: /^Result/ })
        .or(p.getByRole("link", { name: /^Result/ }))
        .first()
        .click()
        .catch(() => {});
      await p.waitForTimeout(3400);
    };
  },
};

const b = await chromium.launch();
const login = await b.newContext({ viewport: V });
const p0 = await login.newPage();
await p0.goto(`${BASE}/login`);
await p0.fill('input[type="email"]', secret("ORAZAKA_USER_EMAIL"));
await p0.fill('input[type="password"]', secret("ORAZAKA_USER_PASSWORD"));
await p0.click('button[type="submit"]');
await p0.waitForURL(`${BASE}/`, { timeout: 60000 });
const state = await login.storageState();
await login.close();
mkdirSync(OUT, { recursive: true });

for (const theme of ["dark", "light"]) {
  for (const [id, prepare] of Object.entries(STEPS)) {
    const raw = mkdtempSync(path.join(os.tmpdir(), "websteps-"));
    const ctx = await b.newContext({
      viewport: V,
      colorScheme: theme,
      storageState: state,
      recordVideo: { dir: raw, size: V },
    });
    await ctx.addInitScript((t) => {
      try {
        localStorage.setItem("kz-theme", t);
      } catch {}
    }, theme);
    const page = await ctx.newPage();
    const t0 = Date.now();
    const act = await prepare(page);
    const start = (Date.now() - t0) / 1000 + 0.3;
    await act();
    if (await qaOnScreen(page)) throw new Error(`${id}/${theme}: « qa » is on screen — a demo shows a persona`);
    await ctx.close();
    const src = path.join(
      raw,
      readdirSync(raw).find((f) => f.endsWith(".webm")),
    );
    const name = path.join(OUT, `step-${id}-${theme}`);
    const cut = ["-y", "-loglevel", "error", "-ss", String(start), "-i", src, "-t", "7", "-an"];
    execFileSync("ffmpeg", [...cut, "-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0", "-row-mt", "1", `${name}.webm`]);
    execFileSync("ffmpeg", [
      ...cut,
      "-c:v",
      "libx264",
      "-crf",
      "26",
      "-pix_fmt",
      "yuv420p",
      "-movflags",
      "+faststart",
      `${name}.mp4`,
    ]);
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-i", `${name}.mp4`, "-frames:v", "1", `${name}-poster.png`]);
    await sharp(`${name}-poster.png`).webp({ quality: 82 }).toFile(`${name}-poster.webp`);
    rmSync(`${name}-poster.png`);
    process.stdout.write(`${id} ${theme} ✓\n`);
  }
}
await b.close();
