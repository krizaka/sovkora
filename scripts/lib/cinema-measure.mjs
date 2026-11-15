// Cinematic shorts, measured (ADR-080): what each `orazaka cinema` production really took and cost, read from its
// production file (every Studio run, with the platform's own start and finish) and from the film itself (ffprobe), and
// what the GPU pod sessions of the batch were billed. Nothing here is typed by hand: produce, measure, regenerate.

/** The Studios whose runs use the GPU pod (keyframes, shots, cut with its upscale, music). */
export const GPU_STUDIOS = new Set(["image-generation", "video-generation", "cinematic-cut", "video-sound"]);

/** The director's step a run belongs to, from its Studio and its place in the production. */
export function stepOf(run, production) {
  if (run.studioKey === "cinematic-script") return "script";
  if (run.studioKey === "image-generation") {
    return Object.values(production.characters ?? {}).some((c) => c.runId === run.runId) ? "character" : "keyframe";
  }
  return { "video-generation": "video", "cinematic-cut": "cut", "speech-synthesis": "voice", "video-sound": "sound" }[run.studioKey] ?? run.studioKey;
}

const median = (values) => {
  if (!values.length) return null;
  const s = [...values].sort((a, b) => a - b);
  const mid = Math.floor(s.length / 2);
  return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
};

const round = (n, d = 2) => Math.round(n * 10 ** d) / 10 ** d;

/** The catalogue's video models, as a person reads them (orazaka-worker-media models.yaml). */
export const VIDEO_LABELS = {
  "wan2.2-i2v-a14b": "Wan 2.2 I2V A14B (4-step distill)",
  "wan2.2-i2v-a14b-hq": "Wan 2.2 I2V A14B (40 steps)",
  "ltx-video-0.9.8-13b-distilled": "LTX-Video 0.9.8 13B distilled",
};

/** The video models a production's shots name (a planned treatment names one per shot), in order of first use. */
function videoModels(script) {
  const named = [...new Set((script?.shots ?? []).map((s) => s.model).filter(Boolean))];
  return named.length ? named.map((m) => VIDEO_LABELS[m] ?? m).join(", ") : VIDEO_LABELS["wan2.2-i2v-a14b"];
}

/**
 * One film's record: production file + the film's probe + the GPU rate. GPU seconds are the platform's run times of the
 * GPU steps (queue wait included: what the pod was held for); the wall time runs from the first run's start to the last
 * run's finish.
 */
export function filmRecord({ id, production, probe, rate, command, gpu, models }) {
  const history = production.history ?? [];
  const script = production.script?.parsed;
  const ok = history.filter((r) => r.status === "SUCCEEDED");
  const gpuSeconds = ok.filter((r) => GPU_STUDIOS.has(r.studioKey)).reduce((sum, r) => sum + (r.seconds ?? 0), 0);
  const starts = history.map((r) => Date.parse(r.startedAt ?? "")).filter(Number.isFinite);
  const ends = history.map((r) => Date.parse(r.finishedAt ?? "")).filter(Number.isFinite);
  const videos = history.filter((r) => r.studioKey === "video-generation");
  return {
    id,
    title: script?.title ?? id,
    media: `products/orazaka/cinema/${id}`,
    aspect: production.brief.aspect,
    width: probe.width,
    height: probe.height,
    duration: round(probe.duration, 2),
    shots: script?.shots.length ?? 0,
    reshoots: Math.max(0, videos.filter((r) => r.status === "SUCCEEDED").length - (script?.shots.length ?? 0)),
    runs: history.length,
    failedRuns: history.length - ok.length,
    scriptRunId: production.script?.runId,
    cutRunId: production.cut?.runId,
    soundRunId: production.sound?.runId,
    gpuSeconds: Math.round(gpuSeconds),
    gpuUsd: round((gpuSeconds / 3600) * rate),
    ...(gpu ? { gpu } : {}),
    rate,
    productionSeconds: starts.length && ends.length ? Math.round((Math.max(...ends) - Math.min(...starts)) / 1000) : null,
    models: {
      script: production.brief.scriptModel ?? "the catalogue's chat default",
      image: "FLUX.2 [klein] 4B",
      video: videoModels(script),
      upscale: "SeedVR2 3B",
      music: "ACE-Step 1.5 XL turbo",
      ...(script?.voiceover ? { voice: `Kokoro-82M (${production.brief.voice ?? "af_heart"})` } : {}),
      // What ran outside the Studios for this film (a batch says it: keyframes on a Mac, a 4K upscale, a narrator).
      ...(models ?? {}),
    },
    command,
  };
}

/** Per step: the median time of one successful run, and its GPU cost at the rate — what the Studio's estimate multiplies. */
export function stepMeasures(productions, rate) {
  const by = {};
  for (const production of productions) {
    for (const run of production.history ?? []) {
      if (run.status !== "SUCCEEDED" || typeof run.seconds !== "number") continue;
      (by[stepOf(run, production)] ??= []).push(run.seconds);
    }
  }
  const studio = { script: "cinematic-script", character: "image-generation", keyframe: "image-generation", video: "video-generation", cut: "cinematic-cut", voice: "speech-synthesis", sound: "video-sound" };
  return Object.fromEntries(
    Object.entries(studio).map(([step, studioKey]) => {
      const seconds = median(by[step] ?? []) ?? 0;
      return [step, { studioKey, seconds: Math.round(seconds), gpuUsd: GPU_STUDIOS.has(studioKey) ? round((seconds / 3600) * rate, 3) : 0, n: (by[step] ?? []).length }];
    }),
  );
}

/** The batch: films, the billed pod sessions, and the cost and time per minute of finished film. */
export function cinemaDataset({ films, sessions, measuredOn, gpu, rate, note }) {
  const billed = round(sessions.reduce((sum, s) => sum + s.hours * s.rate, 0));
  const minutes = films.reduce((sum, f) => sum + f.duration, 0) / 60;
  const wall = films.reduce((sum, f) => sum + (f.productionSeconds ?? 0), 0);
  return {
    measuredOn,
    ...(note ? { note } : {}),
    gpu,
    rate,
    films,
    sessions,
    billedUsd: billed,
    filmMinutes: round(minutes, 2),
    usdPerFilmMinute: minutes ? round(billed / minutes) : null,
    gpuUsdPerFilmMinute: minutes ? round(films.reduce((sum, f) => sum + f.gpuUsd, 0) / minutes) : null,
    wallMinutesPerFilmMinute: minutes ? round(wall / 60 / minutes, 1) : null,
  };
}

const row = (cells) => `| ${cells.join(" | ")} |`;

/** The "Cinematic shorts" section of the Measured runs page. */
export function mdCinema(data) {
  if (!data?.films?.length) return [];
  const lines = ["## Cinematic shorts", ""];
  lines.push(
    `${data.films.length} film(s) made by \`orazaka cinema\` (ADR-080, last measured **${data.measuredOn}**): ` +
      "script or treatment, character references and keyframes (FLUX.2 [klein]), shots, the cut (SeedVR2 3B, speed ramps, LUT grade, " +
      "titles, synthesized foley), the voice-over and the music (ACE-Step). Each film's shots, on its GPU:",
    "",
  );
  for (const f of data.films) lines.push(`- **${f.title}**: ${f.models.video} on ${f.gpu ?? data.gpu}${f.models.upscale ? `, upscaled by ${f.models.upscale}` : ""}`);
  lines.push("", "| Film | Frame | Length | Shots (reshot) | Runs (failed) | GPU time of its runs | At the GPU rate | Wall time |", "| --- | --- | --- | --- | --- | --- | --- | --- |");
  for (const f of data.films) {
    lines.push(
      row([
        `**${f.title}**`,
        `${f.width}×${f.height} (${f.aspect})`,
        `${f.duration.toFixed(1)} s`,
        `${f.shots} (${f.reshoots})`,
        `${f.runs} (${f.failedRuns})`,
        `${Math.round(f.gpuSeconds / 60)} min`,
        `$${f.gpuUsd.toFixed(2)}`,
        f.productionSeconds ? `${Math.round(f.productionSeconds / 60)} min` : "—",
      ]),
    );
  }
  lines.push("", "### What it cost per minute of finished film", "", "| Measure | Value |", "| --- | --- |", row(["Finished film", `${data.filmMinutes} min`]), row(["GPU pod sessions billed (setup, reviews and reshoots included)", `$${data.billedUsd.toFixed(2)}`]), row(["**Billed per minute of finished film**", `**$${data.usdPerFilmMinute?.toFixed(2)}**`]), row(["The runs' own GPU time per minute of film (what the Studio's estimate multiplies)", `$${data.gpuUsdPerFilmMinute?.toFixed(2)}`]), row(["Wall time per minute of film (one pod, runs in sequence)", `${data.wallMinutesPerFilmMinute} min`]));
  if (data.note) lines.push("", `> ${data.note}`);
  lines.push("", "| Pod session | GPU | Hours | Rate | Billed |", "| --- | --- | --- | --- | --- |");
  for (const s of data.sessions) lines.push(row([s.label, s.gpu, s.hours.toFixed(2), `$${s.rate.toFixed(2)}/h`, `$${(s.hours * s.rate).toFixed(2)}`]));
  lines.push("");
  return lines;
}
