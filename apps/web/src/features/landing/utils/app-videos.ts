import { subDays } from "date-fns";
import manifest from "@/../content/app-videos.json";

/**
 * The videos of the Download page: recorded by the web journeys (scripts/showcase-web.mjs, krizaka/.github#315, the `app-videos`
 * workflow), uploaded to Bunny, listed in `content/app-videos.json` by an automated pull request. The page reads only this list;
 * never a typed URL.
 */
export interface AppVideo {
  platform: string;
  flow: string;
  seconds: number;
  capturedAt: string;
  files: { webm: string; mp4: string; poster: string };
}

export const APP_VIDEOS: readonly AppVideo[] = manifest.videos;

/** A video older than this is stale: the test that guards the page fails, so the recording job is looked at (#315). */
// @fixed: the guard of krizaka/.github#315 — a recording older than two weeks means the e2e job stopped publishing
export const MAX_AGE_DAYS = 14;

/** The id of a video in the messages (`desktop.videos.items.<id>`). */
export const videoId = (v: Pick<AppVideo, "platform" | "flow">): string => `${v.platform}-${v.flow}`;

/** A file of the manifest as an address of the media CDN. */
export const videoUrl = (path: string): string => `${manifest.cdn}/${path}`;

/** The videos recorded more than MAX_AGE_DAYS before `now`. */
export const staleVideos = (now: Date, videos: readonly AppVideo[] = APP_VIDEOS): AppVideo[] => {
  const cutoff = subDays(now, MAX_AGE_DAYS);
  return videos.filter((v) => new Date(v.capturedAt) < cutoff);
};

/** The Mac app's film (platform `mac`): shown beside the Mac download button, not in the grid. */
export const macVideo = (videos: readonly AppVideo[] = APP_VIDEOS): AppVideo | undefined =>
  videos.find((v) => v.platform === "mac");

/** The WebVTT tracks of a video with words for them (`desktop.videos.items.<id>.cues`), English and French. */
export const captionTracks = (v: Pick<AppVideo, "platform" | "flow">) =>
  [
    { lang: "en", label: "English" },
    { lang: "fr", label: "Français" },
  ].map((t) => ({ ...t, src: `/desktop/videos/captions/${videoId(v)}/${t.lang}` }));
