import { parseSha256, pickDesktopRelease, type GithubRelease } from "@/features/landing/utils/release";

const rel = (tag: string, at: string, over: Partial<GithubRelease> = {}): GithubRelease => ({
  tag_name: tag,
  draft: false,
  prerelease: true,
  published_at: at,
  html_url: `https://github.com/krizaka/orazaka/releases/tag/${tag}`,
  assets: [
    {
      name: "Sovkora_0.1.0_aarch64.dmg",
      browser_download_url: `https://example.test/${tag}/Sovkora.dmg`,
      size: 6271937,
    },
    {
      name: "Sovkora_0.1.0_aarch64.dmg.sha256",
      browser_download_url: `https://example.test/${tag}/Sovkora.dmg.sha256`,
      size: 92,
    },
  ],
  ...over,
});

describe("pickDesktopRelease", () => {
  it("takes the newest published desktop-* release, ignoring other tags and drafts", () => {
    const picked = pickDesktopRelease([
      rel("v1.2.0", "2026-10-10T00:00:00Z"),
      rel("desktop-v0.1.0-dev", "2026-10-14T00:00:00Z"),
      rel("desktop-v0.2.0", "2026-10-15T00:00:00Z", { draft: true }),
      rel("desktop-v0.1.1", "2026-10-15T01:00:00Z", { prerelease: false }),
    ]);
    expect(picked?.tag).toBe("desktop-v0.1.1");
    expect(picked?.version).toBe("0.1.1");
    expect(picked?.prerelease).toBe(false);
    expect(picked?.dmg.url).toBe("https://example.test/desktop-v0.1.1/Sovkora.dmg");
    expect(picked?.shaUrl).toBe("https://example.test/desktop-v0.1.1/Sovkora.dmg.sha256");
  });

  it("is null without a .dmg or without a desktop release", () => {
    expect(pickDesktopRelease([rel("v1.0.0", "2026-10-10T00:00:00Z")])).toBeNull();
    expect(pickDesktopRelease([rel("desktop-v0.1.0", "2026-10-10T00:00:00Z", { assets: [] })])).toBeNull();
    expect(pickDesktopRelease([])).toBeNull();
  });
});

describe("parseSha256", () => {
  it("reads the hash of a shasum line", () => {
    const h = "fb8a1570c2f1032bf6029e3eebaf711d77b2f6dfe7c706cd6d592b3bea8d834b";
    expect(parseSha256(`${h}  Sovkora_0.1.0_aarch64.dmg\n`)).toBe(h);
    expect(parseSha256("not a hash")).toBeNull();
  });
});
