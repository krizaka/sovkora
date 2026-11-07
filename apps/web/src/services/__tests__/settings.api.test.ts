/**
 * @file settings.api.test.ts
 * @description Tests for the settings API adapter (REST).
 */

import { SettingsApi } from "@/services/settings.api";
import { restRequest } from "@/services/rest-client";
import { THEME_MODE } from "@/core/constants/http.constants";

jest.mock("@/services/rest-client", () => ({
  restRequest: jest.fn(),
}));

const mockedRest = restRequest as unknown as jest.Mock;

describe("SettingsApi", () => {
  beforeEach(() => {
    mockedRest.mockClear();
  });

  describe("fetch", () => {
    it("returns hydrated settings with defaults when preferences are empty", async () => {
      mockedRest.mockResolvedValueOnce({ preferences: {} });

      const result = await SettingsApi.fetch();

      expect(result.language).toBe("en");
      expect(result.autoSave).toBe(true);
      expect(result.aiPersona).toBe("standard");
      expect(result.themeName).toBe("Sovkora");
      expect(result.themeTagline).toBe("Decoupled Intelligence");
      expect(result.themeAccent).toBe("zinc");
      expect(result.themeLayout).toBe("standard");
      expect(result.theme).toBe("system");
      expect(result.tenantId).toBe("orazaka-default");
    });

    it("returns user preferences when populated", async () => {
      mockedRest.mockResolvedValueOnce({
        preferences: {
          language: "fr",
          autoSave: false,
          aiPersona: "creative",
          themeName: "Custom",
          themeTagline: "My App",
          themeAccent: "rose",
          themeLayout: "compact",
          theme: THEME_MODE.DARK,
          tenantId: "tenant-abc",
        },
      });

      const result = await SettingsApi.fetch();

      expect(result.language).toBe("fr");
      expect(result.autoSave).toBe(false);
      expect(result.aiPersona).toBe("creative");
      expect(result.themeName).toBe("Custom");
      expect(result.themeAccent).toBe("rose");
      expect(result.themeLayout).toBe("compact");
      expect(result.theme).toBe(THEME_MODE.DARK);
      expect(result.tenantId).toBe("tenant-abc");
    });

    it("handles an empty response gracefully", async () => {
      mockedRest.mockResolvedValueOnce(undefined);

      const result = await SettingsApi.fetch();

      expect(result.language).toBe("en");
      expect(result.autoSave).toBe(true);
    });
  });

  describe("update", () => {
    it("PUTs partial settings and returns updated preferences", async () => {
      mockedRest.mockResolvedValueOnce({ preferences: { language: "fr" } });

      const result = await SettingsApi.update({ language: "fr" });

      expect(result).toEqual({ language: "fr" });
      expect(mockedRest).toHaveBeenCalledWith("/api/v1/profile/preferences", {
        method: "PUT",
        body: { language: "fr" },
      });
    });

    it("returns empty object when response has no preferences", async () => {
      mockedRest.mockResolvedValueOnce(undefined);

      const result = await SettingsApi.update({ theme: THEME_MODE.DARK });

      expect(result).toEqual({});
    });
  });
});
