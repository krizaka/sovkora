/**
 * @jest-environment node
 */
import fs from "node:fs";
import path from "node:path";
import { desktop } from "@/core/context/translations.desktop";
import { APP_VIDEOS, MAX_AGE_DAYS, staleVideos, videoId } from "@/features/landing/utils/app-videos";

const PATH = /^sites\/orazaka\/download\/(web|android|ios|mac)-[a-z0-9-]+\.[0-9a-f]{10}\.(webm|mp4|jpg)$/;
const read = (p: string) => fs.readFileSync(path.join(process.cwd(), p), "utf8");

describe("the download page's videos (krizaka/.github#315)", () => {
  it("are known to the manifest with a file of each kind, hashed, under the product's download folder", () => {
    for (const v of APP_VIDEOS) {
      expect(Object.keys(v.files).sort()).toEqual(["mp4", "poster", "webm"]);
      for (const p of Object.values(v.files)) expect(p).toMatch(PATH);
      expect(v.seconds).toBeLessThanOrEqual(30);
    }
  });

  it.each(["en", "fr"] as const)(
    "each video has its words in %s (the words may come first: the automated manifest pull request must pass)",
    (locale) => {
      const items = desktop[locale].videos.items;
      for (const v of APP_VIDEOS) {
        expect(items[videoId(v)]?.name).toBeTruthy();
        expect(items[videoId(v)]?.body).toBeTruthy();
      }
    },
  );

  it("the page reads the manifest only: no address of a video is typed in it", () => {
    const section = read("src/features/landing/components/AppVideosSection.tsx");
    expect(section).toContain("APP_VIDEOS");
    expect(section).not.toMatch(/b-cdn|sites\/orazaka|\.(mp4|webm|jpg)["'`]/);
  });

  it(`none is older than ${MAX_AGE_DAYS} days: a stale recording means the e2e job stopped publishing`, () => {
    expect(staleVideos(new Date()).map(videoId)).toEqual([]);
  });

  it("staleVideos tells a fresh recording from an old one", () => {
    const now = new Date("2026-11-20T00:00:00Z");
    const v = (capturedAt: string) => ({
      platform: "web",
      flow: "x",
      seconds: 5,
      capturedAt,
      files: { webm: "", mp4: "", poster: "" },
    });
    expect(staleVideos(now, [v("2026-11-07T00:00:01Z")])).toEqual([]);
    expect(staleVideos(now, [v("2026-11-05T00:00:00Z")])).toHaveLength(1);
  });

  it("the words say nothing of stores or availability", () => {
    for (const locale of ["en", "fr"] as const) {
      const { title, lead, play, recorded, seconds } = desktop[locale].videos;
      expect([title, lead, play, recorded, seconds].join(" ")).not.toMatch(
        /App Store|Google Play|TestFlight|disponible|available/i,
      );
    }
  });
});
