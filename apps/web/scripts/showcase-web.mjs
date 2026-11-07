#!/usr/bin/env node
// The web showcase journeys of the Download page (krizaka/.github#315): short recordings made by the repository's own workflow
// (download-videos.yml), so the videos follow the product. Two journeys on the dev environment, signed in as the demo persona:
//   web-chat    a question asked with a policy pasted under it, answered from the text on screen.
//   web-studio  the Studios catalogue by trade, then one Studio opened. Nothing is run.
// Demo data only (Eric, Lumen Atelier: scripts/tour/eric.mjs). The sign-in is made OFF camera (nothing typed is filmed), any
// e-mail on screen is hidden. A chat that gives no answer fails the run: no blank video is ever published.
//
//   ORAZAKA_USER_EMAIL=… ORAZAKA_USER_PASSWORD=… node scripts/showcase-web.mjs <out-dir> [--base https://www.orazaka.com]
//
// Writes <out-dir>/web-chat.webm and web-studio.webm (no audio), ready for the app-videos action.
import { appendFileSync, mkdirSync, renameSync } from "node:fs";
import path from "node:path";
import { chromium } from "@playwright/test";
import { askRecordedQuestion } from "./tour/chat-journey.mjs";

const args = process.argv.slice(2);
const outDir = path.resolve(args[0] ?? "showcase-web");
const baseAt = args.indexOf("--base");
const base = (baseAt >= 0 && args[baseAt + 1] ? args[baseAt + 1] : "https://www.orazaka.com").replace(/\/$/, "");
const persona = process.env.ORAZAKA_USER_EMAIL;
const secret = process.env.ORAZAKA_USER_PASSWORD;
if (!persona || !secret)
  throw new Error("ORAZAKA_USER_EMAIL and ORAZAKA_USER_PASSWORD are required (the demo persona, never a real account)");
mkdirSync(outDir, { recursive: true });

const VIEWPORT = { width: 1280, height: 800 };
const browser = await chromium.launch();
const pause = (page, ms) => page.waitForTimeout(ms);

/** Init script: hides any element that shows an e-mail address: nothing personal on screen. */
const hideEmail = () => {
  const hide = () => {
    for (const el of document.querySelectorAll("body *")) {
      if (el.childElementCount === 0 && /@[a-z0-9.-]+\.[a-z]{2,}/i.test(el.textContent ?? ""))
        el.style.visibility = "hidden";
    }
  };
  document.addEventListener("DOMContentLoaded", () => {
    hide();
    new MutationObserver(hide).observe(document.body, { childList: true, subtree: true, characterData: true });
  });
};

/** Signs in once, off camera: the journeys start signed in. */
async function session() {
  const ctx = await browser.newContext({ viewport: VIEWPORT, colorScheme: "dark" });
  const page = await ctx.newPage();
  await page.goto(`${base}/login`);
  await page.fill('input[type="email"]', persona);
  await page.fill('input[type="password"]', secret);
  await page.click('button[type="submit"]');
  await page.waitForURL(`${base}/`, { timeout: 60_000 });
  const state = await ctx.storageState();
  await ctx.close();
  return state;
}

/** One journey = one context = one video; the file is renamed <id>.webm in the out directory. */
async function record(id, act, storageState) {
  const ctx = await browser.newContext({
    viewport: VIEWPORT,
    colorScheme: "dark",
    storageState,
    recordVideo: { dir: outDir, size: VIEWPORT },
  });
  await ctx.addInitScript(hideEmail);
  const page = await ctx.newPage();
  await act(page);
  await pause(page, 1500);
  const video = page.video();
  await ctx.close();
  renameSync(await video.path(), path.join(outDir, `${id}.webm`));
  process.stdout.write(`recorded ${id}\n`);
}

const state = await session();

await record(
  "web-chat",
  async (page) => {
    // The proof of a server tool run (orazaka-ai-engine#169): the turn's event stream, kept beside the video (the `tool-call` event).
    page.on("response", async (response) => {
      if (!(response.headers()["content-type"] ?? "").includes("text/event-stream")) return;
      const body = await response.text().catch(() => "");
      if (body) appendFileSync(path.join(outDir, "chat-sse.txt"), `# ${new URL(response.url()).pathname}\n${body}\n`);
    });
    await page.goto(`${base}/chat`, { waitUntil: "load" });
    const box = page.locator("textarea").first();
    await box.waitFor();
    await askRecordedQuestion(page);
    // The answer must grow the page; then the turn is over when the composer is editable again.
    const before = (await page.locator("main").first().innerText()).length;
    await page
      .waitForFunction((n) => (document.querySelector("main")?.innerText.length ?? 0) > n + 80, before, {
        timeout: 180_000,
        polling: 500,
      })
      .catch(() => {
        throw new Error("the chat gave no answer: no video is published");
      });
    await page.waitForFunction(() => document.querySelector("textarea")?.disabled === false, null, {
      timeout: 180_000,
      polling: 250,
    });
    await pause(page, 2200);
  },
  state,
);

await record(
  "web-studio",
  async (page) => {
    await page.goto(`${base}/studios`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle");
    await pause(page, 1600);
    await page.getByRole("radio", { name: /^Ready/ }).click();
    await pause(page, 2400);
    await page.getByRole("radio", { name: /^All/ }).click();
    await pause(page, 1200);
    await page.mouse.wheel(0, 520);
    await pause(page, 1800);
    await page.getByRole("heading", { name: /^Legal/ }).scrollIntoViewIfNeeded();
    await pause(page, 1200);
    await page
      .getByText(/^Document Authenticity$/)
      .first()
      .click();
    await page.waitForURL(/\/studios\/[^/]+$/);
    await page.waitForLoadState("networkidle");
    await pause(page, 2600);
    await page.mouse.wheel(0, 400);
    await pause(page, 1600);
  },
  state,
);

await browser.close();
