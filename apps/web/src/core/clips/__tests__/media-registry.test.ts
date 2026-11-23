import { MEDIA_REGISTRY, clipPublishable, sectionMedia, type MediaRegistry } from "@/core/clips/media-registry";

const use = (page: string, role: string) => [{ page, role }];

describe("section media (content/media-registry.json)", () => {
  it("answers a page section by role, from the registry", () => {
    expect(sectionMedia("home", "hero")).toEqual(["phone-studio"]);
    expect(sectionMedia("docs", "lead")).toEqual(["phone-studio"]);
    expect(sectionMedia("home", "records")).toEqual(["phone-jobs"]);
    expect(sectionMedia("pricing", "lead")).toEqual([]);
  });

  it("never shows a blocked recording, even when a section lists it", () => {
    const media: MediaRegistry = {
      media: [
        {
          id: "take",
          origin: "test",
          owner: "sovkora",
          location: "x",
          blocked: "retired name",
          uses: use("home", "hero"),
        },
      ],
    };
    expect(sectionMedia("home", "hero", media)).toEqual([]);
    expect(clipPublishable("take", media)).toBe(false);
  });

  it("shows a generated or founder media only with the founder's yes", () => {
    const pending: MediaRegistry = {
      media: [{ id: "studio", origin: "generated", owner: "sovkora", location: "x", uses: use("home", "hero") }],
    };
    expect(sectionMedia("home", "hero", pending)).toEqual([]);
    const yes: MediaRegistry = {
      media: [{ ...pending.media[0], founderYes: true }],
    };
    expect(sectionMedia("home", "hero", yes)).toEqual(["studio"]);
  });

  it("fails closed on a clip the registry does not know", () => {
    expect(clipPublishable("take", { media: [] })).toBe(false);
  });

  it("blocks every recording that shows the retired code name, and only test recordings are shown today", () => {
    const shown = MEDIA_REGISTRY.media.filter((m) => clipPublishable(m.id)).map((m) => m.id);
    expect(shown).toEqual(["phone-studio", "phone-jobs"]);
    expect(MEDIA_REGISTRY.media.filter((m) => clipPublishable(m.id)).every((m) => m.origin === "test")).toBe(true);
  });
});
