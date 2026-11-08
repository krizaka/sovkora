// Product tour of the real application, recorded for krizaka.com (public/assets/orazaka/tour/<clip>.{webm,mp4,jpg}).
//
//   orazaka start && orazaka dev           # the stack, with the image engine (sd-server, :8086)
//   orazaka demo seed                      # Eric, the demo persona (orazaka-cli) — never "admin"
//   npm run build && npm run start         # a production build: no dev overlay in the frames
//   npm run record:tour -- prepare         # Eric holds three conversations first (kept for the recordings)
//   npm run record:tour                    # every clip; `-- chat create` re-records some. ORAZAKA_URL, OUT (tour/)
//
// Clips (1280×800, the krizaka.com tour, in this order): creations (what a generation looks like the moment it lands:
// a product photo appearing, then the gallery of what was made on this machine, the voice-over playing), studios (the
// catalogue and a Studio's page), documents (a lease checked against the law, then a supplier invoice examined),
// voice (a voice-over generated, then played with its waveform), notifications (the bell, then a result), chat (a
// question answered from the policy pasted under it), packs. And showcase: the krizaka.com home card (960×600,
// ≤ 20 s, the "wow" first: a generation landing, the gallery, the waveform playing — the context after).
// Every screen is filmed once it has settled (settle(): network idle, images decoded, videos playable, no spinner).
// Waits for a generation are cut and the cut is declared on the site. Prerequisite: `orazaka demo seed --runs`
// (Eric's real work: the voice-over and the document checks the clips open). No video: a Studio's video output cannot
// be opened yet (the worker's job has no row the asset route can authorise), so the tour shows none rather than fake one.
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { setHours, setMinutes, startOfDay } from "date-fns";
import {
  ERIC,
  PREPARED,
  RECORDED_DOCUMENT,
  RECORDED_VIDEO,
  RECORDED_VISUAL,
  RECORDED_VOICE,
  SHOWCASE_VISUAL,
} from "./tour/eric.mjs";
import { askRecordedQuestion } from "./tour/chat-journey.mjs";
import { DEMO_LEASE } from "./tour/documents.mjs";

const BASE = (process.env.ORAZAKA_URL || "http://localhost:3000").replace(/\/$/, "");
const OUT = path.resolve(process.env.OUT || "tour");
const LOGIN = process.env.TOUR_LOGIN || ERIC.login;
const PASSWORD = process.env.TOUR_PASSWORD || process.env.DEMO_PASSWORD || ERIC.password;
const ARGS = process.argv.slice(2);
const PREPARE = ARGS.includes("prepare");
const ONLY = ARGS.filter((a) => a !== "prepare");
// A 16:10 window small enough that the interface reads in a clip (no micro-text), drawn at 2× for sharpness.
const VIEWPORT = { width: 1120, height: 700 };
// The home-page card plays at a third of a screen: its clip is filmed in a smaller window, so the same interface
// fills the frame at a size that reads there (no micro-text). Same 16:10, upscaled to 1280×800 like the others.
const CLOSE_UP = { width: 960, height: 600 };
// The browser's clock reads 18:40 today (TOUR_TIME=HH:MM): the greeting and the timestamps of an evening's work
// look the same whenever the tour is re-recorded. Time still flows; only its starting point is set.
const [TOUR_H, TOUR_M] = (process.env.TOUR_TIME || "18:40").split(":").map(Number);
// Eric's browser between runs: the web client keeps transcripts where they were held (useMessageHistory).
const STATE = path.join(os.tmpdir(), `orazaka-tour-${new URL(BASE).port || "80"}.json`);

let chromium;
try {
  ({ chromium } = await import("@playwright/test"));
} catch {
  console.error("✗ Playwright is missing: npm install, then npx playwright install chromium.");
  process.exit(2);
}
if (
  !(await fetch(`${BASE}/login`).then(
    (r) => r.ok,
    () => false,
  ))
) {
  console.error(`✗ Nothing answers at ${BASE}: start the web client (npm run start) or set ORAZAKA_URL.`);
  process.exit(2);
}

const browser = await chromium.launch();
await mkdir(OUT, { recursive: true });
const raw = await mkdtemp(path.join(os.tmpdir(), "orazaka-tour-"));
const pause = (page, ms) => page.waitForTimeout(ms);

/** A browser context in dark English, signed in when `state` is given. */
async function context(state, record, viewport = VIEWPORT) {
  const ctx = await browser.newContext({
    viewport,
    // Retina: the page is drawn at 2× and the screencast is scaled to the window (Playwright records at the window's size, 25 fps).
    deviceScaleFactor: 2,
    colorScheme: process.env.TOUR_THEME === "light" ? "light" : "dark",
    storageState: state,
    recordVideo: record ? { dir: raw, size: viewport } : undefined,
  });
  await ctx.clock.setSystemTime(setMinutes(setHours(startOfDay(new Date()), TOUR_H), TOUR_M));
  await ctx.addInitScript(() => {
    try {
      if (!sessionStorage.getItem("tour-init")) {
        localStorage.setItem("kz-theme", window.__TOUR_THEME || "dark");
        localStorage.setItem("orazaka_language", "en");
        sessionStorage.setItem("tour-init", "1");
      }
    } catch {}
  });
  return ctx;
}

/** Signs Eric in through the real form; keeps what an earlier `prepare` left in his browser. */
async function signIn() {
  const kept = await readFile(STATE, "utf8").then(JSON.parse, () => undefined);
  const ctx = await context(kept ? { origins: kept.origins, cookies: [] } : undefined, false);
  const page = await ctx.newPage();
  await page.goto(`${BASE}/login`);
  await page.fill('input[type="email"]', LOGIN);
  await page.fill('input[type="password"]', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL(`${BASE}/`, { timeout: 60_000 }).catch(() => {
    throw new Error(
      `Sign-in failed for ${LOGIN}: run \`orazaka demo seed\` first (or set TOUR_LOGIN / TOUR_PASSWORD).`,
    );
  });
  return { ctx, page };
}

/** The turn is over when the composer is editable again (ERR-126), then when the streamed text settles. */
async function awaitAnswer(page, minLength) {
  const composer = (locked) =>
    page
      .waitForFunction((l) => document.querySelector("textarea")?.disabled === l, locked, {
        timeout: locked ? 5_000 : 180_000,
        polling: 250,
      })
      .catch(() => {});
  await composer(true);
  await composer(false);
  let last = "";
  for (let stable = 0, i = 0; stable < 4 && i < 120; i++) {
    await pause(page, 500);
    const now = await page.locator("main").first().innerText();
    stable = now === last && now.length > minLength ? stable + 1 : 0;
    last = now;
  }
}

/** Starts a new conversation from the empty composer with `text` (pasted, not typed). */
async function ask(page, text) {
  await page.goto(`${BASE}/chat`, { waitUntil: "load" });
  const box = page.locator("textarea").first();
  await box.waitFor();
  await box.fill(text);
  await page.getByRole("button", { name: /^Send/ }).click();
  await page.waitForURL(/conversationId=/, { timeout: 30_000 });
  await awaitAnswer(page, text.length / 2);
}

/** Scrolls the page smoothly by `dy` pixels: the element that actually scrolls (the app's main pane), else the window. */
async function glide(page, dy, ms = 1400) {
  await page.evaluate((dy) => {
    const pane = [...document.querySelectorAll("main, section, div")].find(
      (el) => el.scrollHeight > el.clientHeight + 10 && ["auto", "scroll"].includes(getComputedStyle(el).overflowY),
    );
    (pane ?? window).scrollBy({ top: dy, behavior: "smooth" });
  }, dy);
  await pause(page, ms);
}

/** Waits until the screen is really there: network idle, fonts, every image decoded, every video playable, no spinner. */
async function settle(page, extra = 600) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.evaluate(() => document.fonts?.ready);
  await page
    .waitForFunction(
      () =>
        [...document.images].every((img) => img.complete && img.naturalWidth > 0) &&
        [...document.querySelectorAll("video")].every((v) => v.readyState >= 3) &&
        !document.querySelector('[role="status"][aria-label] svg.animate-spin, [aria-busy="true"]') &&
        // Cards that rise into view (data-reveal) are filmed once they have arrived, never mid-blur.
        [...document.querySelectorAll("[data-reveal]")].every((el) => {
          const r = el.getBoundingClientRect();
          return el.classList.contains("kz-in") || r.top > innerHeight || r.bottom < 0;
        }),
      null,
      { timeout: 30_000, polling: 250 },
    )
    .catch(() => {});
  await pause(page, Math.max(extra, 900)); // the reveal transition (0.8 s) ends before the frame counts
}

/** Calls the edge as Eric (the session's bearer is not readable from the page): the API the run form posts to. */
async function api(method, path, body) {
  const edge = (process.env.ROUTER_URL || "http://localhost:8088").replace(/\/$/, "");
  const login = await fetch(`${edge}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: LOGIN, password: PASSWORD }),
  }).then((r) => r.json());
  const res = await fetch(`${edge}${path}`, {
    method,
    headers: { Authorization: `Bearer ${login.token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${path}: HTTP ${res.status} ${await res.text()}`);
  return res.json();
}

/** The latest successful run of a Studio (Eric's seeded work), or a clear error telling what to seed. */
async function latestRun(studioKey) {
  const runs = await api("GET", "/api/v1/studios/runs?limit=200");
  const run = runs.find((r) => r.studioKey === studioKey && r.status === "SUCCEEDED");
  if (!run) throw new Error(`No finished ${studioKey} run for Eric: \`orazaka demo seed --runs --with-video\` first.`);
  return run;
}

/** A run screen until its result is on screen; the wait is cut. */
async function awaitRun(page, cut) {
  cut.from();
  await page
    .getByText(/Run finished|Exécution terminée/)
    .first()
    .waitFor({ timeout: 600_000 });
  await settle(page, 300);
  cut.to();
}

/** The gallery of the jobs page, with the voice-over playing (its waveform fills as it plays); its arrival is cut. */
async function gallery(page, cut) {
  cut.from();
  await page.goto(`${BASE}/dashboard/jobs`, { waitUntil: "load" });
  await settle(page, 400);
  cut.to();
  const play = page.getByRole("button", { name: /^Play/ }).first();
  if (await play.count()) {
    await play.scrollIntoViewIfNeeded();
    await play.click();
  }
}

/** Each clip opens its page, then returns what to play once the page has settled; `cut` drops a wait. */
const CLIPS = {
  // The home card of krizaka.com: the wow first — a photo landing within two seconds, then the gallery and the
  // waveform of a voice-over generated on this machine — and the context (the prompts, "nothing left the machine") after.
  async showcase(page, cut) {
    const run = await api("POST", "/api/v1/studios/image-generation/runs", { inputs: { prompt: SHOWCASE_VISUAL } });
    await page.goto(`${BASE}/studios/runs/${run.id}`, { waitUntil: "load" });
    await settle(page, 200);
    return async () => {
      await pause(page, 900);
      await awaitRun(page, cut);
      await pause(page, 3200);
      await gallery(page, cut);
      await pause(page, 6500);
      await glide(page, 260, 1400);
      await pause(page, 2600);
    };
  },

  // The photo, as Eric makes it: the prompt typed into the Studio (sped up), the run, the wait cut, the image landing.
  async creations(page, cut) {
    await page.goto(`${BASE}/studios/image-generation`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 900);
      const field = page.locator("#studio-run-prompt");
      await field.scrollIntoViewIfNeeded();
      await field.click();
      await field.pressSequentially(RECORDED_VISUAL, { delay: 6 });
      await pause(page, 500);
      await page.getByRole("button", { name: /^Run$/ }).click();
      await page.waitForURL(/\/studios\/runs\//, { timeout: 30_000 });
      await settle(page, 500);
      await awaitRun(page, cut);
      await pause(page, 4200);
      await glide(page, 420, 1600);
      await pause(page, 2200);
    };
  },

  // The clip: the same lamp for five seconds, by a model in the cloud that Orazaka routes to; the wait is cut.
  async video(page, cut) {
    await page.goto(`${BASE}/studios/video-generation`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 900);
      const field = page.locator("#studio-run-prompt");
      await field.scrollIntoViewIfNeeded();
      await field.click();
      await field.pressSequentially(RECORDED_VIDEO, { delay: 5 });
      await page
        .locator("#studio-run-model")
        .selectOption("wan2-2-t2v-a14b")
        .catch(() => {});
      await page
        .locator("#studio-run-aspect")
        .selectOption("16:9")
        .catch(() => {});
      await pause(page, 700);
      await page.getByRole("button", { name: /^Run$/ }).click();
      await page.waitForURL(/\/studios\/runs\//, { timeout: 30_000 });
      await settle(page, 500);
      await awaitRun(page, cut);
      await pause(page, 600);
      const play = page.getByRole("button", { name: /^Play/ }).first();
      if (await play.count()) await play.click().catch(() => {});
      await pause(page, 7000);
    };
  },

  async studios(page) {
    await page.goto(`${BASE}/studios`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 1600);
      await page.getByRole("radio", { name: /^Legal$/ }).click();
      await settle(page, 2600);
      await page.getByRole("radio", { name: /^All trades$/ }).click();
      await settle(page, 1200);
      await glide(page, 520, 1800);
      await page
        .getByText(/^Compliance Check$/)
        .first()
        .hover();
      await pause(page, 1200);
      await page
        .getByText(/^Compliance Check$/)
        .first()
        .click();
      await page.waitForURL(/\/studios\/compliance-check/);
      await settle(page, 2600);
      await glide(page, 400, 1600);
      await pause(page, 1600);
    };
  },

  async documents(page, cut) {
    await page.goto(`${BASE}/studios/compliance-check`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 1200);
      const field = page.locator("#studio-run-document");
      await field.scrollIntoViewIfNeeded();
      await field.fill(DEMO_LEASE);
      await page.locator("#studio-run-jurisdiction").selectOption("FR");
      await pause(page, 900);
      await page.getByRole("button", { name: /^Run$/ }).click();
      await page.waitForURL(/\/studios\/runs\//, { timeout: 30_000 });
      await settle(page, 700);
      await awaitRun(page, cut);
      await pause(page, 2400);
      await glide(page, 520, 2000);
      await pause(page, 2600);
      const invoice = await latestRun("document-authenticity");
      cut.from();
      await page.goto(`${BASE}/studios/runs/${invoice.id}`, { waitUntil: "load" });
      await settle(page, 300);
      cut.to();
      await pause(page, 2200);
      await glide(page, 420, 1800);
      await pause(page, 2000);
    };
  },

  async voice(page, cut) {
    await page.goto(`${BASE}/studios/speech-synthesis`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 1200);
      const field = page.locator("#studio-run-text");
      await field.scrollIntoViewIfNeeded();
      await field.click();
      await field.pressSequentially(RECORDED_VOICE, { delay: 7 });
      // The speech engine of this Mac (Piper through LocalAI): its voice, named, so the run does not depend on a catalogue default.
      await page.locator("#studio-run-model").fill("tts-1");
      await pause(page, 600);
      await page.getByRole("button", { name: /^Run$/ }).click();
      await page.waitForURL(/\/studios\/runs\//, { timeout: 30_000 });
      await settle(page, 600);
      await awaitRun(page, cut);
      await pause(page, 1200);
      await page.getByRole("button", { name: /^Play/ }).first().click();
      await pause(page, 7000);
    };
  },

  async notifications(page, cut) {
    await page.goto(`${BASE}/`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 1400);
      await page.getByRole("button", { name: /^Notifications$/ }).click();
      await pause(page, 3400); // the bell: what ran, with a thumbnail of what it made
      await page.getByRole("link", { name: /View all task history/ }).click();
      cut.from();
      await page.waitForURL(/\/dashboard\/jobs/);
      await settle(page, 300);
      cut.to();
      await pause(page, 1800);
      await page
        .getByRole("button", { name: /^Result/ })
        .first()
        .click();
      cut.from();
      await page.getByRole("dialog").waitFor({ timeout: 15_000 });
      await settle(page, 300);
      cut.to();
      await pause(page, 3400);
      await page.keyboard.press("Escape");
      await pause(page, 900);
    };
  },

  async chat(page) {
    await page.goto(`${BASE}/chat`, { waitUntil: "load" });
    await page.locator("textarea").first().waitFor();
    await settle(page);
    return async () => {
      await askRecordedQuestion(page);
      await awaitAnswer(page, RECORDED_DOCUMENT.length);
      await pause(page, 2200);
    };
  },

  async packs(page) {
    await page.goto(`${BASE}/packs`, { waitUntil: "load" });
    await settle(page);
    return async () => {
      await pause(page, 2400);
      await glide(page, 520, 1800);
      await pause(page, 1800);
      await glide(page, 520, 1800);
      await pause(page, 2200);
    };
  },
};

/** Records one clip, then encodes it next to the others (dropping the wait the clip declared). */
async function record(id, state) {
  const ctx = await context(state, true, id === "showcase" ? CLOSE_UP : VIEWPORT);
  const page = await ctx.newPage();
  const opened = Date.now();
  const at = () => (Date.now() - opened) / 1000;
  // Every wait the clip declares (a generation, a page arriving) is dropped from the final cut.
  const cuts = [];
  const cut = { from: () => cuts.push({ from: at() }), to: () => (cuts.at(-1).to = at()) };
  const play = await CLIPS[id](page, cut);
  await settle(page, 800); // fonts, data, media, first paint settle before the clip starts
  const start = at();
  await play();
  const video = page.video();
  await ctx.close();
  const source = await video.path();

  const target = path.join(OUT, id);
  const spans = cuts
    .filter((c) => c.to)
    .map((c) => `between(t,${(c.from - start).toFixed(2)},${(c.to - start).toFixed(2)})`);
  const drop = spans.length ? `select='not(${spans.join("+")})',setpts=N/FRAME_RATE/TB,` : "";
  const trim = [
    "-y",
    "-loglevel",
    "error",
    "-ss",
    start.toFixed(2),
    "-i",
    source,
    "-vf",
    `fps=25,${drop}scale=1440:900:flags=lanczos`,
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
    `${target}.webm`,
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
    `${target}.mp4`,
  ]);
  const duration = Number(
    execFileSync("ffprobe", [
      "-v",
      "error",
      "-show_entries",
      "format=duration",
      "-of",
      "default=nw=1:nk=1",
      `${target}.mp4`,
    ]).toString(),
  );
  // The poster is the moment worth stopping on: the generation that just landed for the openers, else late in the clip.
  const posterAt = { showcase: 3.2, creations: 4.2 }[id] ?? duration * 0.7;
  execFileSync("ffmpeg", [
    "-y",
    "-loglevel",
    "error",
    "-ss",
    Math.min(posterAt, duration - 0.2).toFixed(2),
    "-i",
    `${target}.mp4`,
    "-frames:v",
    "1",
    "-q:v",
    "4",
    `${target}.jpg`,
  ]);
  const cutSeconds = cuts.filter((c) => c.to).reduce((sum, c) => sum + c.to - c.from, 0);
  const cutNote = drop ? ` (${cutSeconds.toFixed(0)} s of waiting cut)` : "";
  process.stdout.write(
    `✓ ${id}  ${duration.toFixed(1)} s${cutNote} → ${path.relative(process.cwd(), target)}.{webm,mp4,jpg}` + "\n",
  );
}

try {
  const { ctx, page } = await signIn();
  // Warm the local model up outside the recording: the first answer of a cold Ollama loads its weights.
  const ollama = (process.env.OLLAMA_BASE_URL || "http://localhost:11434").replace(/\/$/, "");
  const model = process.env.OLLAMA_MODEL || "llama3.2:3b";
  await fetch(`${ollama}/api/generate`, {
    method: "POST",
    body: JSON.stringify({ model, prompt: "ok", stream: false, keep_alive: "15m" }),
  }).catch(() => console.warn(`⚠ Ollama did not answer at ${ollama}: the chat clip will wait for a cold model.`));
  if (PREPARE) {
    for (const [i, { prompt }] of PREPARED.entries()) {
      await ask(page, prompt);
      process.stdout.write(`✓ prepared conversation ${i + 1}/${PREPARED.length}\n`);
    }
  }
  const state = await ctx.storageState();
  await writeFile(STATE, JSON.stringify(state));
  await ctx.close();
  const clips = Object.keys(CLIPS).filter((id) => ONLY.length === 0 || ONLY.includes(id));
  if (!(PREPARE && ONLY.length === 0 && ARGS.length === 1)) for (const id of clips) await record(id, state);
} finally {
  await browser.close();
  await rm(raw, { recursive: true, force: true });
}
