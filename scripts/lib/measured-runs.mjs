// The body of the "Measured runs" page (the front matter is the generator's), generated from docs/measurements/runs.json — the Orazaka run records of a measurement session (start and finish of
// each run, size, seed, outcome) joined with the engine logs. Nothing on the page is typed by hand: re-measure, export, regenerate.

const median = (values) => {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : Math.round(((sorted[mid - 1] + sorted[mid]) / 2) * 10) / 10;
};

/** Per model: how many runs, how many produced their output, the time of those that did (the ones left out of the statistics are named), what they cost. */
export function summarise(dataset) {
  const out = {};
  for (const [id, model] of Object.entries(dataset.models)) {
    const mine = dataset.runs.filter((run) => run.model === id);
    const succeeded = mine.filter((run) => run.outcome === "succeeded");
    const timed = succeeded.filter((run) => !run.excludedFromStats && typeof run.seconds === "number").map((run) => run.seconds);
    const engine = dataset.engineSeconds?.[id] ?? [];
    out[id] = {
      id,
      model,
      runs: mine.length,
      succeeded: succeeded.length,
      placeholders: mine.filter((run) => run.outcome === "placeholder").length,
      failed: mine.filter((run) => run.outcome === "failed").length,
      excluded: succeeded.filter((run) => run.excludedFromStats).length,
      seconds: timed.length ? { n: timed.length, min: Math.min(...timed), median: median(timed), max: Math.max(...timed) } : null,
      engine: engine.length ? { n: engine.length, min: Math.min(...engine), median: median(engine), max: Math.max(...engine) } : null,
      // A provider's price is per output; a GPU droplet is billed by the hour, so a run's cost is its own GPU time (`costUsd` on the run).
      costUsd: model.gpuUsdPerHour
        ? Math.round(succeeded.reduce((sum, run) => sum + (run.costUsd ?? 0), 0) * 100) / 100
        : model.costUsd == null
          ? null
          : Math.round(succeeded.length * model.costUsd * 100) / 100,
    };
  }
  return out;
}

const where = { mac: "One Mac (Metal), local", digitalocean: "DigitalOcean serverless inference", gpu: "A rented GPU, by the hour (through the relay; the provider of each session is in « GPU sessions »)" };
const fmt = (stats, digits = 0) => (stats ? `${stats.median.toFixed(digits)} s (${stats.min.toFixed(digits)}–${stats.max.toFixed(digits)}, n=${stats.n})` : "—");
const price = (model) =>
  model.gpuUsdPerHour ? `$${model.gpuUsdPerHour.toFixed(2)} per GPU hour` : model.costUsd == null ? "none (your own machine)" : `$${model.costUsd.toFixed(2)} ${model.priceUnit ?? "per output"}`;
const row = (cells) => `| ${cells.join(" | ")} |`;

export function mdMeasuredRuns(dataset) {
  const summary = summarise(dataset);
  const lines = [];
  lines.push(
    `What the video and image models Orazaka routes to really did, measured on **${dataset.measuredOn}**: each figure below is read from the **run records** of the platform (start and finish of every Studio run, its size and seed, its outcome) and from the engine's own log — none is typed by hand. ` +
      `Every number is for **this setup** and is measured again when the setup changes (a new model, engine version, provider or machine).`,
    "",
  );
  lines.push("## Setup", "", "| Part | What |", "| --- | --- |", row(["Machine", dataset.setup.machine]), row(["Local engine", dataset.setup.engine]), row(["Platform", dataset.setup.platform]), row(["Hosted provider", dataset.setup.hostedProvider]));
  if (dataset.commit) lines.push(row(["Orazaka commit", `\`${dataset.commit}\``]));
  lines.push("", "Times are **command to file**: from the start of the Studio run to the finished output (queueing, transfer and storage included). The engine's own time, where it is logged, is given beside it.", "");

  lines.push("## Per model", "", "| Model | Where | Runs (ok / all) | Time, command to file (median, min–max) | Engine time | Price | Spent |", "| --- | --- | --- | --- | --- | --- | --- |");
  for (const s of Object.values(summary)) {
    lines.push(row([`**${s.model.label}** (${s.model.kind})`, where[s.model.where], `${s.succeeded} / ${s.runs}`, fmt(s.seconds), fmt(s.engine, 1), price(s.model), s.costUsd == null ? "—" : `$${s.costUsd.toFixed(2)}`]));
  }
  lines.push("");
  const left = Object.values(summary).filter((s) => s.excluded > 0);
  for (const s of left) lines.push(`${s.excluded} run${s.excluded > 1 ? "s" : ""} of ${s.model.label} succeeded but ${s.excluded > 1 ? "are" : "is"} left out of its time statistics (${dataset.runs.find((run) => run.model === s.id && run.excludedFromStats).excludedFromStats}).`, "");

  lines.push("## Parameters", "");
  for (const s of Object.values(summary)) {
    lines.push(`### ${s.model.label}`, "", `${where[s.model.where]} · licence ${s.model.licence}`, "");
    for (const [name, value] of Object.entries(s.model.parameters)) lines.push(`- **${name}:** ${value}`);
    lines.push("");
  }

  if (dataset.upscaleTests?.length) {
    lines.push("## Video upscaling", "", "The same clips, taken from 720p (or 480p) to 1080p or 4K by each candidate, on one NVIDIA H100 80 GB. The verdict is a person's, after looking at the same frames side by side with a plain Lanczos upscale; the cost is the run's own time at the GPU's hourly rate.", "", "| Model | Licence | Commercial use | Size | Time (5 s clip) | Cost per clip | Verdict |", "| --- | --- | --- | --- | --- | --- | --- |");
    for (const t of dataset.upscaleTests) {
      const ran = typeof t.seconds === "number";
      lines.push(row([t.model, t.licence, t.commercial, ran ? `${t.from} → ${t.to}` : "—", ran ? `${t.seconds} s` : "not run", ran ? `$${((t.seconds / 3600) * t.gpuUsdPerHour).toFixed(2)}` : "—", t.verdict]));
    }
    lines.push("");
  }

  if (dataset.bakeOff?.rows?.length) {
    const b = dataset.bakeOff;
    lines.push("## Image-to-video bake-off", "", `${b.note}. One NVIDIA H100 80 GB; the cost is the run's own time at the GPU's hourly rate. The verdict is a person's, after looking at the frame grid (first, quarter, middle, three quarters, last) of both shots.`, "", "| Model | Licence | Commercial use | Shot | Size | Time | Cost | VRAM | Verdict |", "| --- | --- | --- | --- | --- | --- | --- | --- | --- |");
    for (const r of b.rows) {
      const ran = typeof r.seconds === "number";
      lines.push(row([r.model, r.licence, r.commercial, r.shot, r.size, ran ? `${r.seconds} s` : "not finished", ran ? `$${((r.seconds / 3600) * b.gpuUsdPerHour).toFixed(2)}` : "—", r.vramGb ? `${r.vramGb} GB` : "—", r.verdict]));
    }
    lines.push("");
    if (b.winners?.length) {
      lines.push("**Winners:**", "");
      for (const w of b.winners) lines.push(`- **${w.use}**: ${w.model} — ${w.why}`);
      lines.push("");
    }
  }

  lines.push("## Failures, timeouts and limits", "", "| Kind | What happened | Cause |", "| --- | --- | --- |");
  for (const event of dataset.events) lines.push(row([event.kind, event.what, event.cause]));
  lines.push("");

  const spent = Object.values(summary).filter((s) => s.costUsd != null);
  const total = Math.round(spent.reduce((sum, s) => sum + s.costUsd, 0) * 100) / 100;
  lines.push("## What it cost", "", "| Provider | Output | Price | Runs | Spent |", "| --- | --- | --- | --- | --- |");
  for (const s of spent) lines.push(row([where[s.model.where], s.model.label, price(s.model), s.succeeded, `$${s.costUsd.toFixed(2)}`]));
  lines.push(row(["**Total of the runs above**", "", "", "", `**$${total.toFixed(2)}**`]), "");
  if (dataset.gpuSessions?.length) {
    const billed = Math.round(dataset.gpuSessions.reduce((sum, g) => sum + g.hours * g.usdPerHour, 0) * 100) / 100;
    lines.push("### GPU droplet sessions (what was billed)", "", "| Session | GPU | Hours | Rate | Billed |", "| --- | --- | --- | --- | --- |");
    for (const g of dataset.gpuSessions) lines.push(row([g.what, g.gpu, g.hours.toFixed(2), `$${g.usdPerHour.toFixed(2)}/h`, `$${(g.hours * g.usdPerHour).toFixed(2)}`]));
    lines.push(row(["**Total billed**", "", "", "", `**$${billed.toFixed(2)}**`]), "", "A droplet is billed from its creation to its destruction: the sessions include the setup (image build, weights), the load of the model and the attempts that failed, which the runs above do not. The cost of a run is its own start-to-finish time at the hourly rate.", "");
  }
  lines.push("The local models cost nothing per output; the energy of the Mac was **not measured**, so none is claimed. Probes and refused calls (a 403, a size test) are not runs and are not in this table.", "", "See also: [Supported models](MODELS.md) for the registry, and the comparison images on the gallery (`/gallery?kind=compare`).", "");
  return lines.join("\n");
}
