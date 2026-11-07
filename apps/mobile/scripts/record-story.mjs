#!/usr/bin/env node
/**
 * Records the real Orazaka app on the booted iOS simulator and cuts it into the clips of the site.
 *
 *   node scripts/record-story.mjs --appearance dark|light --out <dir> [--video] [--recut]
 *   (--recut cuts again from the raw recording and marks already in <dir>, without touching the simulator)
 *
 * What it does, nothing more: set the simulator's appearance and status bar, start `simctl io recordVideo`, run
 * `.maestro/story.yaml` (which reports named moments to a local marker server), stop, then cut with ffmpeg — the
 * typing between `type:start`/`type:end` is played 4x, every wait between `wait:start`/`wait:end` is replaced by a
 * short hold of the screen carrying a label that says how long was cut. Frames are the app's, untouched.
 *
 * Credentials come from the QA file through secret.sh (never printed); the app talks to the platform it was built for
 * (EXPO_PUBLIC_EDGE_URL). Hold the heavy lock around it: `heavy-lock.sh acquire oramobile`.
 */
import { spawn, execFileSync } from "node:child_process";
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { toolPath } from "./tool-path.mjs";
import os from "node:os";

const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i === -1 ? fallback : (process.argv[i + 1] ?? fallback);
};
const appearance = arg("appearance", "dark");
const out = path.resolve(arg("out", "recordings"));
const withVideo = process.argv.includes("--video");
// Your own simulator: never "booted" (other sessions may hold booted ones). --device <udid> or ORAZAKA_SIM_UDID.
const DEVICE = arg("device", process.env.ORAZAKA_SIM_UDID);
if (!DEVICE) throw new Error("Pass --device <udid> (or ORAZAKA_SIM_UDID): the simulator this session created.");
const SPEED = 4;
const HOLD = 1.4;
// The persona (Mira by default) from @dev, never the QA account: its address is on screen at sign-in.
const persona = arg("persona", "mira");
const PERSONA_KEYS = { mira: "MIRA", eric: "PERSONA" };
if (!PERSONA_KEYS[persona]) throw new Error(`unknown persona: ${persona}`);
const secret = (key) =>
  execFileSync(
    path.join(os.homedir(), ".krizaka-worktrees/tools/secret.sh"),
    ["@dev", `APP_QA_ORAZAKA_${PERSONA_KEYS[persona]}_${key}`],
    { encoding: "utf8" },
  ).trim();

fs.mkdirSync(out, { recursive: true });
const raw = path.join(out, `raw-${arg("name", appearance)}.mp4`);
const recut = process.argv.includes("--recut");
let marks = [];
let startedAt = 0;

if (recut) {
  ({ startedAt, marks } = JSON.parse(fs.readFileSync(path.join(out, `marks-${arg("name", appearance)}.json`), "utf8")));
} else {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    marks.push({
      name: url.searchParams.get("name") ?? "",
      label: url.searchParams.get("label") ?? "",
      t: Date.now() / 1000,
    });
    res.end("ok");
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const markUrl = `http://127.0.0.1:${server.address().port}`;

  execFileSync(toolPath("xcrun"), ["simctl", "ui", DEVICE, "appearance", appearance]);
  execFileSync(toolPath("xcrun"), [
    "simctl",
    "status_bar",
    DEVICE,
    "override",
    "--time",
    "9:41",
    "--batteryState",
    "charged",
    "--batteryLevel",
    "100",
    "--cellularBars",
    "4",
    "--wifiBars",
    "3",
  ]);

  const recorder = spawn(
    toolPath("xcrun"),
    ["simctl", "io", DEVICE, "recordVideo", "--codec", "h264", "--force", raw],
    {
      stdio: "ignore",
    },
  );
  startedAt = Date.now() / 1000;
  await new Promise((r) => setTimeout(r, 2500));

  const env = {
    ...process.env,
    MARK_URL: markUrl,
    ORAZAKA_EMAIL: secret("EMAIL"),
    ORAZAKA_PASSWORD: secret("PASSWORD"),
  };
  const args = [
    "--device",
    DEVICE,
    "test",
    arg("flow", ".maestro/story.yaml"),
    "-e",
    `MARK_URL=${markUrl}`,
    "-e",
    `ORAZAKA_EMAIL=${env.ORAZAKA_EMAIL}`,
    "-e",
    `ORAZAKA_PASSWORD=${env.ORAZAKA_PASSWORD}`,
    "-e",
    `IMAGE_PROMPT=${arg("image-prompt", "A ceramic table lamp on a walnut desk, warm evening light, product photograph")}`,
    "-e",
    `VIDEO_PROMPT=${arg("video-prompt", "Slow push-in on a ceramic lamp being switched on in a quiet studio, warm light spreading across the wall")}`,
    "-e",
    `IMAGE_MODEL=${arg("image-model", "stable-diffusion-3.5-large")}`,
    "-e",
    `CHAT_PROMPT=${arg("chat-prompt", "In two sentences, thank a customer for her patience: her ceramic lamp arrived two days late. Sign as Mira, Lumen Atelier. No placeholders.")}`,
    "-e",
    `WITH_VIDEO=${withVideo}`,
  ];
  const code = await new Promise((resolve) =>
    spawn(toolPath("maestro"), args, { stdio: "inherit" }).on("exit", resolve),
  );
  await new Promise((r) => setTimeout(r, 1500));
  recorder.kill("SIGINT");
  await new Promise((r) => recorder.on("exit", r));
  server.close();
  execFileSync(toolPath("xcrun"), ["simctl", "status_bar", DEVICE, "clear"]);
  fs.writeFileSync(
    path.join(out, `marks-${arg("name", appearance)}.json`),
    JSON.stringify({ startedAt, marks }, null, 2),
  );
  if (code !== 0) {
    console.error(`maestro exited with ${code}; raw recording kept at ${raw}`);
    process.exit(code ?? 1);
  }
}

// ---- cutting -------------------------------------------------------------------------------------------------------
const offset = (m) => Math.max(0, m.t - startedAt - 0.4); // simctl's first frame lags the process start a little
const probe = JSON.parse(
  execFileSync(
    toolPath("ffprobe"),
    ["-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height,r_frame_rate", "-of", "json", raw],
    { encoding: "utf8" },
  ),
).streams[0];
console.log(`raw recording ${probe.width}x${probe.height} @ ${probe.r_frame_rate}`);

const LABEL_PY = `
import sys
from PIL import Image, ImageDraw, ImageFont
out, text, w, h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", int(h * 0.021))
l, t, r, b = d.textbbox((0, 0), text, font=font)
tw, th, pad = r - l, b - t, int(h * 0.014)
x, y = (w - tw) // 2, int(h * 0.5)
d.rounded_rectangle((x - pad * 2, y - pad, x + tw + pad * 2, y + th + pad * 1.6), radius=pad * 2, fill=(0, 0, 0, 170))
d.text((x, y), text, font=font, fill=(255, 255, 255, 255))
img.save(out)
`;
const clips = arg("clips", "sign-in,studio-run,ask,jobs").split(",");
for (const id of clips) {
  const s = marks.findIndex((m) => m.name === `clip:${id}:start`);
  const e = marks.findIndex((m) => m.name === `clip:${id}:end`);
  if (s === -1 || e === -1) throw new Error(`no marks for ${id}`);
  let t0 = offset(marks[s]);
  // The sign-in opens on a still login screen while the test driver finds the field: begin just before the typing.
  if (id === "sign-in" || id === "ask") {
    const first = marks.find((m, i) => i > s && m.name === "type:start");
    if (first) t0 = Math.max(t0, offset(first) - (id === "ask" ? 2 : 1.2));
  }
  let t1 = offset(marks[e]);
  // After signing in, hold the Studios list a moment, not for the whole settling time.
  if (id === "sign-in") {
    const shown = marks.find((m, i) => i > s && m.name === "shown");
    if (shown) t1 = Math.min(t1, offset(shown) + 3);
  }
  // Pieces between t0 and t1: normal, typing (sped up), wait (cut → hold).
  const pieces = [];
  let cursor = t0;
  let next = s + 1;
  while (next < e) {
    const i = next;
    const m = marks[i];
    next += 1;
    if (m.name === "type:start" || m.name === "wait:start") {
      const endName = m.name === "type:start" ? "type:end" : "wait:end";
      const j = marks.findIndex((x, k) => k > i && x.name === endName);
      if (j === -1 || j > e) continue;
      pieces.push(
        { kind: "normal", a: cursor, b: offset(m) },
        {
          kind: m.name === "type:start" ? "type" : "wait",
          a: offset(m),
          b: offset(marks[j]),
          label: m.label,
        },
      );
      cursor = offset(marks[j]);
      next = j + 1;
    }
  }
  pieces.push({ kind: "normal", a: cursor, b: t1 });
  const parts = [];
  const filters = [];
  const labels = [];
  pieces
    .filter((p) => p.b - p.a > 0.05)
    .forEach((p, n) => {
      const v = `v${n}`;
      if (p.kind === "normal") filters.push(`[0:v]trim=start=${p.a}:end=${p.b},setpts=PTS-STARTPTS,fps=60[${v}]`);
      else if (p.kind === "type")
        filters.push(`[0:v]trim=start=${p.a}:end=${p.b},setpts=(PTS-STARTPTS)/${SPEED},fps=60[${v}]`);
      else {
        // A hold of the screen as it was when the wait began, carrying a label that says how long was cut.
        const cut = Math.round(p.b - p.a);
        const prefix = p.label ? `${p.label} \u00b7 ` : "";
        const text = `${prefix}${cut} s cut`;
        const png = path.join(out, `label-${id}-${n}.png`);
        execFileSync(toolPath("python3"), ["-I", "-c", LABEL_PY, png, text, String(probe.width), String(probe.height)]);
        labels.push(png);
        const input = labels.length; // ffmpeg input 0 is the recording
        filters.push(
          `[0:v]trim=start=${p.a}:end=${p.a + 0.05},setpts=PTS-STARTPTS,loop=loop=${Math.round(HOLD * 60)}:size=1:start=0,fps=60[h${n}];` +
            `[h${n}][${input}:v]overlay=0:0[${v}]`,
        );
      }
      parts.push(`[${v}]`);
    });
  const inputs = ["-i", raw, ...labels.flatMap((l) => ["-i", l])];
  const graph = `${filters.join(";")};${parts.join("")}concat=n=${parts.length}:v=1:a=0,scale=720:1566:flags=lanczos,format=yuv420p[out]`;
  const base = path.join(out, `clip-${id}-${appearance}`);
  execFileSync(toolPath("ffmpeg"), [
    "-y",
    "-v",
    "error",
    ...inputs,
    "-filter_complex",
    graph,
    "-map",
    "[out]",
    "-r",
    "60",
    "-c:v",
    "libx264",
    "-crf",
    "20",
    "-preset",
    "slow",
    "-movflags",
    "+faststart",
    `${base}.mp4`,
  ]);
  execFileSync(toolPath("ffmpeg"), [
    "-y",
    "-v",
    "error",
    "-i",
    `${base}.mp4`,
    "-c:v",
    "libvpx-vp9",
    "-crf",
    "34",
    "-b:v",
    "0",
    "-row-mt",
    "1",
    `${base}.webm`,
  ]);
  // The poster: a frame from the clip (ffmpeg here has no webp encoder, Pillow writes it).
  const len = Number(
    execFileSync(
      toolPath("ffprobe"),
      ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", `${base}.mp4`],
      {
        encoding: "utf8",
      },
    ),
  );
  execFileSync(toolPath("ffmpeg"), [
    "-y",
    "-v",
    "error",
    "-ss",
    arg("poster-at", String(Math.min(3, len / 2))),
    "-i",
    `${base}.mp4`,
    "-frames:v",
    "1",
    `${base}-poster.png`,
  ]);
  execFileSync(toolPath("python3"), [
    "-I",
    "-c",
    "import sys; from PIL import Image; Image.open(sys.argv[1]).save(sys.argv[2], 'WEBP', quality=82)",
    `${base}-poster.png`,
    `${base}-poster.webp`,
  ]);
  fs.rmSync(`${base}-poster.png`);
  for (const l of labels) fs.rmSync(l);
  console.log(`clip-${id}-${appearance}: ${pieces.length} pieces`);
}
