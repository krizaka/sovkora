#!/usr/bin/env node
/**
 * Puts the raw clips of record-story.mjs in a device frame, on a neutral background, and writes what the sites load:
 *
 *   node scripts/frame-clips.mjs --dir <out> --appearance dark|light [--clips sign-in,studio-run,ask,jobs]
 *
 * For each clip: <id>-<appearance>-916.{mp4,webm} (1080x1920), <id>-<appearance>-169.{mp4,webm} (1920x1080), a poster
 * of each (.webp), and the captions <id>.en.vtt / <id>.fr.vtt. No sound. The frame is drawn here (a plain bezel, no
 * brand of phone); the screen is the app's own, untouched.
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { toolPath } from "./tool-path.mjs";

const arg = (n, d) => {
  const i = process.argv.indexOf(`--${n}`);
  return i === -1 ? d : (process.argv[i + 1] ?? d);
};
const dir = path.resolve(arg("dir", "recordings"));
const appearance = arg("appearance", "dark");
const clips = arg("clips", "sign-in,studio-run,ask,jobs").split(",");
const BG = appearance === "dark" ? "0x0b0f17" : "0xe9edf2";
const BEZEL = appearance === "dark" ? "(30,34,42,255)" : "(24,26,31,255)";
const SRC_W = 720,
  SRC_H = 1566;

/** Caption cues as fractions of the clip: [start, end, en, fr]. What is said is only what the screen shows. */
export const CAPTIONS = {
  "sign-in": [
    [0.02, 0.5, "Sign in from your phone.", "Connectez-vous depuis votre téléphone."],
    [0.52, 0.98, "Your studios, one tap away.", "Vos studios, à portée de main."],
  ],
  "studio-run": [
    [0.02, 0.3, "Describe what you need.", "Décrivez ce qu'il vous faut."],
    [0.32, 0.65, "Follow the run live.", "Suivez l'exécution en direct."],
    [0.67, 0.98, "The result, ready to use.", "Le résultat, prêt à l'emploi."],
  ],
  ask: [
    [0.02, 0.45, "Ask Orazaka anything.", "Posez votre question à Orazaka."],
    [0.47, 0.98, "The answer streams in.", "La réponse arrive en continu."],
  ],
  jobs: [
    [0.02, 0.5, "Every run, in one list.", "Toutes vos exécutions, dans une liste."],
    [0.52, 0.98, "The same account as on the web.", "Le même compte que sur le web."],
  ],
};

const ts = (t) => {
  const h = Math.floor(t / 3600),
    m = Math.floor((t % 3600) / 60),
    s = t % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${s.toFixed(3).padStart(6, "0")}`;
};
const cueText = ([a, b, en, fr], i, duration, lang) =>
  [`${i + 1}`, `${ts(a * duration)} --> ${ts(b * duration)}`, lang === "fr" ? fr : en, ""].join("\n");
export const vtt = (cues, duration, lang) =>
  "WEBVTT\n\n" + cues.map((cue, i) => cueText(cue, i, duration, lang)).join("\n");

const FRAME_PY = `
import sys
from PIL import Image, ImageDraw
out, sw, sh, pad, color = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), eval(sys.argv[5])
w, h = sw + 2 * pad, sh + 2 * pad
S = 4  # supersample the corners
img = Image.new("RGBA", (w * S, h * S), color)
hole = Image.new("L", (w * S, h * S), 0)
ImageDraw.Draw(hole).rounded_rectangle((pad * S, pad * S, (pad + sw) * S - 1, (pad + sh) * S - 1), radius=int(sw * 0.115) * S, fill=255)
img.putalpha(Image.eval(hole, lambda v: 255 - v))
mask = Image.new("L", (w * S, h * S), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * S - 1, h * S - 1), radius=int((sw + 2 * pad) * 0.13) * S, fill=255)
a = img.getchannel("A"); a = Image.composite(a, Image.new("L", a.size, 0), mask); img.putalpha(a)
img.resize((w, h), Image.LANCZOS).save(out)
`;

function compose(src, out, W, H, screenH) {
  const pad = Math.round(screenH * 0.018);
  const sw = Math.round((screenH * SRC_W) / SRC_H / 2) * 2;
  const frame = `${out}.frame.png`;
  execFileSync(toolPath("python3"), ["-I", "-c", FRAME_PY, frame, String(sw), String(screenH), String(pad), BEZEL]);
  const x = Math.round((W - sw) / 2),
    y = Math.round((H - screenH) / 2);
  const g = `color=c=${BG}:s=${W}x${H}:r=60[bg];[0:v]scale=${sw}:${screenH}:flags=lanczos[s];[bg][s]overlay=${x}:${y}:shortest=1[a];[a][1:v]overlay=${x - pad}:${y - pad},format=yuv420p[out]`;
  execFileSync(toolPath("ffmpeg"), [
    "-y",
    "-v",
    "error",
    "-i",
    src,
    "-i",
    frame,
    "-filter_complex",
    g,
    "-map",
    "[out]",
    "-an",
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
    `${out}.mp4`,
  ]);
  execFileSync(toolPath("ffmpeg"), [
    "-y",
    "-v",
    "error",
    "-i",
    `${out}.mp4`,
    "-an",
    "-c:v",
    "libvpx-vp9",
    "-crf",
    "34",
    "-b:v",
    "0",
    "-row-mt",
    "1",
    `${out}.webm`,
  ]);
  const len = Number(
    execFileSync(
      toolPath("ffprobe"),
      ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", `${out}.mp4`],
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
    String(Math.min(3, len / 2)),
    "-i",
    `${out}.mp4`,
    "-frames:v",
    "1",
    `${out}.poster.png`,
  ]);
  execFileSync(toolPath("python3"), [
    "-I",
    "-c",
    "import sys; from PIL import Image; Image.open(sys.argv[1]).save(sys.argv[2], 'WEBP', quality=82)",
    `${out}.poster.png`,
    `${out}-poster.webp`,
  ]);
  fs.rmSync(`${out}.poster.png`);
  fs.rmSync(frame);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  for (const id of clips) {
    const src = path.join(dir, `clip-${id}-${appearance}.mp4`);
    const duration = Number(
      execFileSync(toolPath("ffprobe"), ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", src], {
        encoding: "utf8",
      }),
    );
    compose(src, path.join(dir, `${id}-${appearance}-916`), 1080, 1920, 1700);
    compose(src, path.join(dir, `${id}-${appearance}-169`), 1920, 1080, 1000);
    for (const lang of ["en", "fr"])
      fs.writeFileSync(path.join(dir, `${id}.${lang}.vtt`), vtt(CAPTIONS[id], duration, lang));
    console.log(`${id}-${appearance}: ${duration.toFixed(1)} s framed 9:16 and 16:9`);
  }
}
