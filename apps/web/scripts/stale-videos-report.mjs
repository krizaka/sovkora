#!/usr/bin/env node
// Reads the download page's manifest, writes the alert body to the file given and the state (`red` | `green`) to the step's output, for
// `krizaka/.github/actions/alert-issue` (`.github/workflows/stale-videos.yml`, krizaka-internal#3626).
import { appendFileSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { alertFor } from "./stale-videos-lib.mjs";

const MANIFEST = new URL("../content/app-videos.json", import.meta.url);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const out = process.argv[2];
  if (!out) {
    console.error("usage: stale-videos-report.mjs <body-file>");
    process.exit(2);
  }
  const temp = path.resolve(process.env.RUNNER_TEMP ?? tmpdir());
  const target = path.resolve(temp, out);
  if (path.relative(temp, target).startsWith("..")) throw new Error("the report path leaves the temporary folder");
  const { videos } = JSON.parse(readFileSync(MANIFEST, "utf8"));
  const {
    GITHUB_SERVER_URL: server,
    GITHUB_REPOSITORY: repo,
    GITHUB_RUN_ID: runId,
    GITHUB_OUTPUT: output,
  } = process.env;
  const alert = alertFor(new Date(), videos, runId ? `${server}/${repo}/actions/runs/${runId}` : "");
  writeFileSync(target, alert.body);
  if (output) appendFileSync(output, `state=${alert.state}\n`);
  process.stdout.write(`${alert.state}: ${alert.ids.length ? alert.ids.join(", ") : "no stale recording"}\n`);
}
