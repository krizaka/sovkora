const redirect = jest.fn((to: string) => {
  throw new Error(`NEXT_REDIRECT:${to}`);
});
jest.mock("next/navigation", () => ({ redirect: (to: string) => redirect(to) }));

import SettingsIndex from "@/app/settings/page";

async function visit(search: { tab?: string | string[] }) {
  await expect(SettingsIndex({ searchParams: Promise.resolve(search) })).rejects.toThrow(/NEXT_REDIRECT/);
  return redirect.mock.calls.at(-1)?.[0];
}

describe("/settings (the old entry point)", () => {
  it("goes to /settings/account without a tab", async () => {
    expect(await visit({})).toBe("/settings/account");
  });
  it("keeps /settings?tab=<section> working: it redirects to the section's route", async () => {
    expect(await visit({ tab: "appearance" })).toBe("/settings/appearance");
    expect(await visit({ tab: "integrations" })).toBe("/settings/integrations");
  });
  it("falls back to account for an unknown tab", async () => {
    expect(await visit({ tab: "nope" })).toBe("/settings/account");
  });
});
