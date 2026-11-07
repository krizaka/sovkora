/** @jest-environment node */
import { GET } from "@/app/desktop/download/route";
import { loadDesktopRelease } from "@/features/landing/utils/release";

jest.mock("@/features/landing/utils/release", () => ({ loadDesktopRelease: jest.fn() }));
const load = loadDesktopRelease as jest.Mock;

const dmg = {
  tag: "desktop-v1",
  version: "1",
  prerelease: true,
  publishedAt: null,
  pageUrl: "x",
  dmg: { name: "O.dmg", url: "https://example.test/O.dmg", bytes: 1 },
  shaUrl: null,
  sha256: null,
};

afterEach(() => {
  delete process.env.APP_DOWNLOADS_MAC_ENABLED;
  load.mockReset();
});

describe("GET /desktop/download", () => {
  it("answers 404 while the switch is off, without even reading the release", async () => {
    const res = await GET();
    expect(res.status).toBe(404);
    expect(res.headers.get("location")).toBeNull();
    expect(load).not.toHaveBeenCalled();
  });
  it("redirects to the file once the switch is on", async () => {
    process.env.APP_DOWNLOADS_MAC_ENABLED = "true";
    load.mockResolvedValue(dmg);
    const res = await GET();
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe(dmg.dmg.url);
  });
  it("answers 404 when on but no release can be read", async () => {
    process.env.APP_DOWNLOADS_MAC_ENABLED = "true";
    load.mockResolvedValue(null);
    expect((await GET()).status).toBe(404);
  });
});
