/**
 * @file mobile.ts
 * @description The steps of the Sovkora mobile app, in the order a person lives them: sign in, make an image, ask, follow the jobs. Each step is one recording of the real app (iOS simulator, signed in as the demo
 * persona (Mira), against www.orazaka.com), in dark and in light, on the CDN under sites/orazaka/mobile, already in a device frame, 9:16 (hashes:
 * core/media/media.generated.json); its words — goal, purpose, alt, the things to notice — are `t.mobile.steps`.
 * Typing is sped up and waits are cut, each cut labelled on the recording. A recording of anything but the app is not
 * shown: there is no mock-up, and no step without a recording.
 */
import { mediaUrl } from "@/core/media/cdn";

export const STEPS = ["sign-in", "studio-run", "ask", "jobs"] as const;
export type StepId = (typeof STEPS)[number];
export type ClipId = StepId;

/** The two looks the app has; the site shows the one its visitor reads the page in. */
export const APPEARANCES = ["dark", "light"] as const;
export type Appearance = (typeof APPEARANCES)[number];

const AREA = "sites/orazaka/mobile";

export function clipUrls(id: ClipId, appearance: Appearance) {
  return {
    webm: mediaUrl(`${AREA}/${id}-${appearance}-916.webm`),
    mp4: mediaUrl(`${AREA}/${id}-${appearance}-916.mp4`),
    poster: mediaUrl(`${AREA}/${id}-${appearance}-916-poster.webp`),
  };
}
