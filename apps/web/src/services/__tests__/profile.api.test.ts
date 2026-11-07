/**
 * @file profile.api.test.ts
 * @description Tests for the profile API adapter (REST).
 */

import { ProfileApi } from "@/services/profile.api";
import { restRequest } from "@/services/rest-client";

jest.mock("@/services/rest-client", () => ({
  restRequest: jest.fn(),
}));

const mockedRest = restRequest as unknown as jest.Mock;

describe("ProfileApi", () => {
  beforeEach(() => {
    mockedRest.mockClear();
  });

  describe("fetch", () => {
    it("returns the user profile from the REST response", async () => {
      const profile = {
        id: "user-uuid-123",
        username: "orazaka_admin",
        email: "admin@orazaka.io",
        authorities: ["ROLE_ADMIN", "ROLE_USER"],
        preferences: { language: "en", theme: "dark" },
      };
      mockedRest.mockResolvedValueOnce(profile);

      const result = await ProfileApi.fetch();

      expect(result).toEqual(profile);
      expect(result.authorities).toContain("ROLE_ADMIN");
    });

    it("calls restRequest with the profile path", async () => {
      mockedRest.mockResolvedValueOnce({
        id: "1",
        username: "u",
        email: "e@e.com",
        authorities: [],
        preferences: {},
      });

      await ProfileApi.fetch();

      expect(mockedRest).toHaveBeenCalledWith("/api/v1/profile");
    });

    it("propagates REST errors", async () => {
      mockedRest.mockRejectedValueOnce(new Error("Unauthorized"));

      await expect(ProfileApi.fetch()).rejects.toThrow("Unauthorized");
    });
  });
});
