/**
 * @file interception.api.test.ts
 * @description Tests for the interception API adapter (REST).
 */

import { InterceptionApi } from "@/services/interception.api";
import { restRequest } from "@/services/rest-client";

jest.mock("@/services/rest-client", () => ({
  restRequest: jest.fn(),
}));

const mockedRest = restRequest as unknown as jest.Mock;

describe("InterceptionApi", () => {
  beforeEach(() => {
    mockedRest.mockClear();
  });

  describe("fetchSchema", () => {
    it("returns the parsed schema descriptor", async () => {
      const schema = {
        title: "Onboarding",
        description: "Complete your profile",
        fields: [{ name: "industry", label: "Industry", type: "select", required: true }],
      };
      mockedRest.mockResolvedValueOnce(schema);

      const result = await InterceptionApi.fetchSchema("onboarding-v1");

      expect(result.title).toBe("Onboarding");
      expect(result.fields).toHaveLength(1);
      expect(result.fields[0].name).toBe("industry");
    });

    it("requests the schema by id", async () => {
      mockedRest.mockResolvedValueOnce({ title: "t", description: "d", fields: [] });

      await InterceptionApi.fetchSchema("feedback-v2");

      expect(mockedRest).toHaveBeenCalledWith("/api/v1/interceptions/feedback-v2");
    });

    it("throws when no schema returned", async () => {
      mockedRest.mockResolvedValueOnce(undefined);

      await expect(InterceptionApi.fetchSchema("missing")).rejects.toThrow("No schema returned from server.");
    });
  });

  describe("resolve", () => {
    it("submits responses and returns boolean", async () => {
      mockedRest.mockResolvedValueOnce({ resolved: true });

      const result = await InterceptionApi.resolve("onboarding", "onboarding-v1", {
        industry: "tech",
        role: "developer",
      });

      expect(result).toBe(true);
      expect(mockedRest).toHaveBeenCalledWith("/api/v1/interceptions/resolve", {
        method: "POST",
        body: {
          interceptionType: "onboarding",
          schemaId: "onboarding-v1",
          responses: { industry: "tech", role: "developer" },
        },
      });
    });

    it("returns false when resolution fails", async () => {
      mockedRest.mockResolvedValueOnce({ resolved: false });

      const result = await InterceptionApi.resolve("feedback", "fb-v1", {});

      expect(result).toBe(false);
    });
  });
});
