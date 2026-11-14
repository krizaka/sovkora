// The pure part of the download videos' scheduled watch (krizaka-internal#3626): which recordings are older than two weeks, and the alert
// that says so. Staleness is a property of time, not of a change, so it is watched daily (`.github/workflows/stale-videos.yml`) and never
// judged by a unit test that would block every push, the repair of the recording workflow included.
import { subDays } from "date-fns";

/** The alert's identity: ONE issue per key (alert-issue, krizaka-internal#2282). */
export const ALERT_KEY = "stale-videos/sovkora";

/** A recording older than this is stale. Same figure as `MAX_AGE_DAYS` of `src/features/landing/utils/app-videos.ts` (a test keeps them equal). */
// @fixed: the guard of krizaka/.github#315 — a recording older than two weeks means the e2e job stopped publishing
export const MAX_AGE_DAYS = 14;

/** The id of a video in the messages, as `videoId` of `app-videos.ts`. */
export const videoId = (v) => `${v.platform}-${v.flow}`;

/** The videos recorded more than MAX_AGE_DAYS before `now`. Pure. */
export function staleVideos(now, videos) {
  const cutoff = subDays(now, MAX_AGE_DAYS);
  return videos.filter((v) => new Date(v.capturedAt) < cutoff);
}

/** The alert for this day: `red` names the stale ids (raises or comments), `green` clears it. Pure. */
export function alertFor(now, videos, runUrl = "") {
  const stale = staleVideos(now, videos);
  if (stale.length === 0)
    return {
      state: "green",
      ids: [],
      body: `All ${videos.length} recordings are less than ${MAX_AGE_DAYS} days old.\n`,
    };
  const ids = stale.map(videoId);
  const lines = [
    `${ids.length} of ${videos.length} download-page recordings are older than ${MAX_AGE_DAYS} days: the e2e job stopped publishing.`,
    "",
    ...stale.map((v) => `- \`${videoId(v)}\` recorded ${v.capturedAt}`),
    "",
    "Look at the `Download videos` workflow of krizaka/sovkora (`.github/workflows/web-videos.yml`); the next successful run republishes them and this alert clears by itself.",
  ];
  if (runUrl) lines.push("", `Watch run: ${runUrl}`);
  return { state: "red", ids, body: `${lines.join("\n")}\n` };
}
