/**
 * @file jobs.api.test.ts
 * @description Tests for the jobs API adapter.
 */

import { JobsApi } from "@/services/jobs.api";
import { signOut } from "next-auth/react";
import { JOB_STATUS } from "@/core/constants/http.constants";

jest.mock("next-auth/react", () => ({
  signOut: jest.fn(),
}));

const mockFetch = jest.fn();
global.fetch = mockFetch;
const mockedSignOut = signOut as jest.MockedFunction<typeof signOut>;

describe("JobsApi", () => {
  beforeEach(() => {
    mockFetch.mockClear();
    mockedSignOut.mockClear();
  });

  describe("fetchPage", () => {
    it("fetches jobs through the BFF proxy without a browser-side Authorization header", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () =>
          JSON.stringify({
            content: [{ id: "j1", status: JOB_STATUS.COMPLETED }],
            totalPages: 1,
            totalElements: 1,
          }),
      });

      const result = await JobsApi.fetchPage(0, 20);

      expect(mockFetch).toHaveBeenCalledWith(
        "/api/v1/jobs?page=0&size=20",
        expect.objectContaining({
          headers: { "Content-Type": "application/json" },
        }),
      );
      expect(result.content).toHaveLength(1);
      expect(result.totalPages).toBe(1);
      expect(result.totalElements).toBe(1);
    });

    it("throws on HTTP error", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: "Internal Server Error",
        json: async () => {
          throw new Error("not json");
        },
      });

      await expect(JobsApi.fetchPage(0, 10)).rejects.toThrow("REST request failed: 500 Internal Server Error");
      expect(mockedSignOut).not.toHaveBeenCalled();
    });

    it("tears down a stale session when the proxy rejects the token", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        statusText: "Unauthorized",
        json: async () => ({ error: "Unauthorized" }),
      });

      await expect(JobsApi.fetchPage(0, 10)).rejects.toThrow("Unauthorized");
      expect(mockedSignOut).toHaveBeenCalledWith({ callbackUrl: "/login" });
    });

    it("defaults to empty content when data fields are missing", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: async () => JSON.stringify({}),
      });

      const result = await JobsApi.fetchPage(0, 5);

      expect(result.content).toEqual([]);
      expect(result.totalPages).toBe(0);
      expect(result.totalElements).toBe(0);
    });
  });
});
