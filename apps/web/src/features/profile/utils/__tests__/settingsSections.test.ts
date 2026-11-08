import {
  SETTINGS_SECTIONS,
  isSettingsSection,
  legacyTabTarget,
  resolveSection,
  settingsHref,
  visibleSections,
} from "@/features/profile/utils/settingsSections";

describe("settings sections: the URL is the state", () => {
  it("has one route per section", () => {
    expect(SETTINGS_SECTIONS).toEqual(["account", "appearance", "workspace", "integrations", "api-keys", "webhooks"]);
    expect(settingsHref("appearance")).toBe("/settings/appearance");
    expect(settingsHref("api-keys")).toBe("/settings/api-keys");
    expect(settingsHref("webhooks")).toBe("/settings/webhooks");
  });

  it("hides the workspace from non-admins", () => {
    expect(visibleSections(true)).toContain("workspace");
    expect(visibleSections(false)).not.toContain("workspace");
    expect(visibleSections(false)[0]).toBe("account");
  });

  it("resolves an unknown or unauthorised section to account", () => {
    expect(resolveSection("integrations", false)).toBe("integrations");
    expect(resolveSection("workspace", false)).toBe("account");
    expect(resolveSection("nope", true)).toBe("account");
    expect(resolveSection(undefined, true)).toBe("account");
    expect(isSettingsSection("account")).toBe(true);
    expect(isSettingsSection("nope")).toBe(false);
  });

  it("redirects the old /settings?tab= to the section's route", () => {
    expect(legacyTabTarget("appearance")).toBe("/settings/appearance");
    expect(legacyTabTarget("integrations")).toBe("/settings/integrations");
    expect(legacyTabTarget("workspace")).toBe("/settings/workspace");
    expect(legacyTabTarget("garbage")).toBe("/settings/account");
    expect(legacyTabTarget(undefined)).toBe("/settings/account");
    expect(legacyTabTarget(null)).toBe("/settings/account");
  });
});
