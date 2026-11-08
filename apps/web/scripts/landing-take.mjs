#!/usr/bin/env node
// The home's hero take — the real web client answering a real question, as ONE crisp loop (760×640 window at 1.5×, ≥ 25 fps):
// the question typed, the pipeline running (« Routed »), the answer. Recorded on www.orazaka.com as the QA user, in each
// theme and language, nothing staged; the clips go to Bunny, never into the repository.
//
//   ORAZAKA_USER_EMAIL=… ORAZAKA_USER_PASSWORD=… node scripts/landing-take.mjs <out-dir>
//
// Writes <out-dir>/take-<locale>-<theme>.{webm,mp4}, -poster.webp and evidence.json (seconds, fps, the waiting that was sped
// up). Playwright's recorder films in CSS pixels at ~12 fps: the protocol's screencast hands over a device-pixel frame (760×640
// at 1.5× = 1140×960) at every compositor change with its time, assembled afterwards; a take under 25 fps while typing is refused.
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import { qaOnScreen } from "./qa-on-screen.mjs";

const require = createRequire(import.meta.url);
const { chromium } = require("@playwright/test");

const BASE = process.env.APP_BASE_URL ?? "https://www.orazaka.com";
const OUT = path.resolve(process.argv[2] ?? "take");
const email = process.env.ORAZAKA_USER_EMAIL;
const password = process.env.ORAZAKA_USER_PASSWORD;
if (!email || !password) throw new Error("ORAZAKA_USER_EMAIL and ORAZAKA_USER_PASSWORD are required");

export const VIEWPORT = { width: 760, height: 640 };
const PERSONA = "Eric";
const SCALE = 1.5; // 760×640 CSS px × 1.5 = the 1140×960 frames
const MIN_FPS = 25;
/** The waiting for the model is sped up to this many seconds at most (never edited otherwise). */
const MAX_WAIT = 2.5;
const QUESTION = {
  en: "In two sentences: what should a small firm check before sending client files to an AI tool?",
  fr: "En deux phrases : que doit vérifier un petit cabinet avant d'envoyer des dossiers clients à un outil d'IA ?",
};

mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch();

const auth = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const signIn = await auth.newPage();
await signIn.goto(`${BASE}/login`);
await signIn.fill('input[type="email"]', email);
await signIn.fill('input[type="password"]', password);
await signIn.click('button[type="submit"]');
await signIn.waitForURL(`${BASE}/`, { timeout: 60_000 });
const storageState = await auth.storageState();
await auth.close();

const evidence = {};
for (const locale of ["en", "fr"]) {
  for (const theme of ["dark", "light"]) {
    const ctx = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: SCALE,
      colorScheme: theme,
      storageState,
      locale,
    });
    await ctx.addInitScript(
      ([t, l]) => {
        try {
          localStorage.setItem("kz-theme", t);
          localStorage.setItem("orazaka_language", l);
        } catch {}
      },
      [theme, locale],
    );
    // The take is in the language asked, whatever the QA profile says: the profile's answer is rewritten on the way in (nothing is saved).
    await ctx.route("**/api/v1/profile", async (route) => {
      if (route.request().method() !== "GET") return route.continue();
      const res = await route.fetch();
      const json = await res.json().catch(() => ({}));
      await route.fulfill({ response: res, json: { ...json, preferences: { ...json.preferences, language: locale } } });
    });
    // A demo shows a persona, never a QA name: the session's display name is « Eric » on the way in (nothing is saved).
    await ctx.route("**/api/auth/session", async (route) => {
      const res = await route.fetch();
      const json = await res.json().catch(() => ({}));
      await route.fulfill({
        response: res,
        json: json?.user ? { ...json, user: { ...json.user, name: PERSONA } } : json,
      });
    });
    const page = await ctx.newPage();
    // Refuses the take if « qa » shows anywhere on screen (the visible text, the labels and the titles).
    await page.goto(`${BASE}/chat`, { waitUntil: "networkidle" });
    await page.waitForTimeout(800);
    if (await qaOnScreen(page))
      throw new Error(`${locale}/${theme}: « qa » is on screen — a demo shows a persona, never a QA name`);

    const cdp = await ctx.newCDPSession(page);
    const frames = [];
    cdp.on("Page.screencastFrame", (f) => {
      frames.push({ at: f.metadata.timestamp ?? Date.now() / 1000, data: f.data });
      void cdp.send("Page.screencastFrameAck", { sessionId: f.sessionId }).catch(() => {});
    });
    await cdp.send("Page.startScreencast", {
      format: "jpeg",
      quality: 85,
      maxWidth: 1140,
      maxHeight: 960,
      everyNthFrame: 1,
    });
    await page.waitForTimeout(900);
    const box = page.getByRole("textbox").last();
    await box.click();
    const typingFrom = Date.now() / 1000;
    await page.keyboard.type(QUESTION[locale], { delay: 30 });
    const typingTo = Date.now() / 1000;
    await page.waitForTimeout(500);
    const sent = Date.now() / 1000;
    await page.keyboard.press("Enter");
    // The answer is in once the « Answering… » state is gone and a second message bubble holds text.
    await page
      .waitForFunction(
        () =>
          !/Answering|En train de répondre|Réponse en cours/i.test(document.body.innerText) &&
          document.querySelectorAll('[class*="prose"], [data-role="assistant"], [data-testid*="assistant"]').length > 0,
        null,
        { timeout: 150_000 },
      )
      .catch(() => {});
    const answered = Date.now() / 1000;
    await page.waitForTimeout(2600);
    if (await qaOnScreen(page)) throw new Error(`${locale}/${theme}: « qa » is on screen at the end of the take`);
    await cdp.send("Page.stopScreencast");
    await ctx.close();

    const first = frames[0].at;
    const secs = frames.at(-1).at - first;
    // The compositor hands over a frame whenever pixels change; a still moment holds the last one. The smoothness that matters is
    // the typing and the answer arriving: frames per second over the typing must reach MIN_FPS.
    const typed = frames.filter((f) => f.at >= typingFrom && f.at <= typingTo).length;
    const fps = typed / (typingTo - typingFrom);
    if (fps < MIN_FPS) throw new Error(`${locale}/${theme}: ${fps.toFixed(1)} fps while typing < ${MIN_FPS}`);
    // The wait: from one second after the send to one second before the answer.
    const waitFrom = sent + 1.2;
    const waitTo = answered - 0.8;
    const L = Math.max(0, waitTo - waitFrom);
    const factor = L > MAX_WAIT ? L / MAX_WAIT : 1;
    const dir = mkdtempSync(path.join(os.tmpdir(), "oz-take-"));
    const list = [];
    frames.forEach((f, i) => {
      const file = path.join(dir, `f${String(i).padStart(5, "0")}.jpg`);
      writeFileSync(file, Buffer.from(f.data, "base64"));
      const next = frames[i + 1]?.at ?? f.at + 0.04;
      const d = (next - f.at) / (f.at > waitFrom && f.at < waitTo ? factor : 1);
      list.push(`file '${file}'`, `duration ${d.toFixed(4)}`);
    });
    list.push(`file '${path.join(dir, `f${String(frames.length - 1).padStart(5, "0")}.jpg`)}'`);
    const listFile = path.join(dir, "frames.txt");
    writeFileSync(listFile, list.join("\n"));
    const name = path.join(OUT, `take-${locale}-${theme}`);
    const common = [
      "-y",
      "-loglevel",
      "error",
      "-f",
      "concat",
      "-safe",
      "0",
      "-i",
      listFile,
      "-r",
      "30",
      "-vf",
      "scale=1140:960:flags=lanczos",
      "-an",
    ];
    execFileSync("ffmpeg", [
      ...common,
      "-c:v",
      "libvpx-vp9",
      "-crf",
      "30",
      "-b:v",
      "0",
      "-row-mt",
      "1",
      `${name}.webm`,
    ]);
    execFileSync("ffmpeg", [
      ...common,
      "-c:v",
      "libx264",
      "-crf",
      "22",
      "-preset",
      "slow",
      "-pix_fmt",
      "yuv420p",
      "-movflags",
      "+faststart",
      `${name}.mp4`,
    ]);
    // The poster is the finished answer: what a visitor without video sees is the real moment, not an empty page.
    execFileSync("ffmpeg", [
      "-y",
      "-loglevel",
      "error",
      "-sseof",
      "-0.3",
      "-i",
      `${name}.mp4`,
      "-frames:v",
      "1",
      `${name}-poster.png`,
    ]);
    const sharp = require("sharp");
    await sharp(`${name}-poster.png`).webp({ quality: 84 }).toFile(`${name}-poster.webp`);
    evidence[`${locale}-${theme}`] = {
      seconds: +(secs - (L - L / factor)).toFixed(1),
      typingFps: +fps.toFixed(1),
      speedUp: +factor.toFixed(2),
    };
    process.stdout.write(`${locale}/${theme} ✓ ${fps.toFixed(0)} fps, ${evidence[`${locale}-${theme}`].seconds} s\n`);
  }
}
writeFileSync(path.join(OUT, "evidence.json"), `${JSON.stringify(evidence, null, 2)}\n`);
await browser.close();
