import { pickListedRepos, type GithubRepo } from "@/features/landing/utils/repositories";

const repo = (over: Partial<GithubRepo>): GithubRepo => ({
  name: "orazaka",
  description: "d",
  html_url: "https://github.com/krizaka/orazaka",
  pushed_at: "2026-10-01T00:00:00Z",
  archived: false,
  fork: false,
  ...over,
});

describe("pickListedRepos", () => {
  it("keeps the live Sovkora repositories only, most recently pushed first", () => {
    const picked = pickListedRepos([
      repo({ name: "orazaka-studio", pushed_at: "2026-10-20T00:00:00Z" }),
      repo({ name: "yomizaka" }),
      repo({ name: "orazaka-old", archived: true }),
      repo({ name: "orazaka-fork", fork: true }),
      repo({ name: "orazaka", pushed_at: "2026-10-27T00:00:00Z" }),
    ]);
    expect(picked.map((r) => r.name)).toEqual(["orazaka", "orazaka-studio"]);
  });

  it("never invents a description", () => {
    expect(pickListedRepos([repo({ description: null })])[0].description).toBe("");
  });
});
