/**
 * @jest-environment node
 */
import fs from "node:fs";
import path from "node:path";
import { cuesVtt } from "@/core/clips/clips";
import { desktop } from "@/core/context/translations.desktop";
import { captionTracks, macVideo, type AppVideo } from "@/features/landing/utils/app-videos";

const read = (p: string) => fs.readFileSync(path.join(process.cwd(), p), "utf8");
const mac: AppVideo = {
  platform: "mac",
  flow: "control-panel",
  seconds: 20,
  capturedAt: "2026-10-30T05:41:00.000Z",
  files: { webm: "a", mp4: "b", poster: "c" },
};

describe("the Mac app's film on the Download page (krizaka-internal#230)", () => {
  it("is the manifest's `mac` video, and nothing is drawn while there is none", () => {
    expect(macVideo([])).toBeUndefined();
    expect(macVideo([{ ...mac, platform: "web" }, mac])).toBe(mac);
  });

  it("has its words and captions in English and French, with the same number of cues", () => {
    const en = desktop.en.videos.items["mac-control-panel"];
    const fr = desktop.fr.videos.items["mac-control-panel"];
    expect(en.cues?.length).toBeGreaterThan(0);
    expect(fr.cues).toHaveLength(en.cues!.length);
    expect(captionTracks(mac).map((t) => t.lang)).toEqual(["en", "fr"]);
  });

  it("spreads the cues over the film's length as WebVTT", () => {
    const vtt = cuesVtt(["one", "two"], 20);
    expect(vtt).toBe("WEBVTT\n\n00:00.000 --> 00:10.000\none\n\n00:10.000 --> 00:20.000\ntwo\n");
  });

  it("stands beside the Mac button in the hero, the grid leaves it out, and reduced motion keeps the poster", () => {
    expect(read("src/features/landing/components/DesktopPage.tsx")).toMatch(/macVideo\(\) \? \(\s*<MacVideo \/>/);
    expect(read("src/features/landing/components/AppVideosSection.tsx")).toContain('video.platform !== "mac"');
    const player = read("src/features/landing/components/AppVideoPlayer.tsx");
    expect(player).toContain("prefers-reduced-motion: reduce");
    expect(player).toMatch(/autoPlay=\{!still\}/);
    expect(player).toMatch(/kind="captions"/);
  });
});
