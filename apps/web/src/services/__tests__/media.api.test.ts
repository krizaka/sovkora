/**
 * @file media.api.test.ts
 * @description Tests for the media API adapter.
 */

import { MediaApi } from "@/services/media.api";
import { getSession } from "next-auth/react";
import { HTTP_METHOD } from "@/core/constants/http.constants";

jest.mock("next-auth/react", () => ({
  getSession: jest.fn(),
}));

const mockFetch = jest.fn();
global.fetch = mockFetch;
const mockedGetSession = getSession as jest.MockedFunction<typeof getSession>;

describe("MediaApi", () => {
  beforeEach(() => {
    mockFetch.mockClear();
    mockedGetSession.mockClear();
  });

  describe("searchRag", () => {
    it("sends encoded search query with auth header", async () => {
      mockedGetSession.mockResolvedValueOnce({
        user: { id: "u1" },
        expires: "2099-01-01",
      } as never);
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ context: "Virtual threads use..." }),
      });

      const result = await MediaApi.searchRag("virtual threads");

      expect(mockFetch).toHaveBeenCalledWith(
        "/api/v1/media/search?q=virtual%20threads",
        expect.objectContaining({
          headers: { Authorization: "Bearer u1" },
        }),
      );
      expect(result).toBe("Virtual threads use...");
    });

    it("uses fallback token when no session", async () => {
      mockedGetSession.mockResolvedValueOnce(null);
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ context: "" }),
      });

      const result = await MediaApi.searchRag("test");

      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: { Authorization: "Bearer user-mock" },
        }),
      );
      expect(result).toBe("No context found matching query.");
    });

    it("throws on HTTP error", async () => {
      mockedGetSession.mockResolvedValueOnce(null);
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 });

      await expect(MediaApi.searchRag("query")).rejects.toThrow("RAG query failed with status 500");
    });
  });

  describe("analyzeVideo", () => {
    it("sends POST with assetId and model", async () => {
      mockedGetSession.mockResolvedValueOnce({
        user: { id: "u2" },
        expires: "2099-01-01",
      } as never);
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ jobId: "job-abc" }),
      });

      const result = await MediaApi.analyzeVideo("asset-123", "llava:latest");

      expect(mockFetch).toHaveBeenCalledWith("/api/v1/media/analyze/video", {
        method: HTTP_METHOD.POST,
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer u2",
        },
        body: JSON.stringify({ assetId: "asset-123", model: "llava:latest" }),
      });
      expect(result.jobId).toBe("job-abc");
    });

    it("throws on HTTP error", async () => {
      mockedGetSession.mockResolvedValueOnce(null);
      mockFetch.mockResolvedValueOnce({
        ok: false,
        statusText: "Bad Request",
      });

      await expect(MediaApi.analyzeVideo("a", "m")).rejects.toThrow("Analysis failed: Bad Request");
    });
  });

  describe("uploadMedia", () => {
    it("sends FormData with file and auth header", async () => {
      mockedGetSession.mockResolvedValueOnce({
        user: { id: "u3" },
        expires: "2099-01-01",
      } as never);
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ assetId: "uploaded-asset-id" }),
      });

      const file = new File(["content"], "test.mp4", { type: "video/mp4" });
      const result = await MediaApi.uploadMedia(file);

      expect(mockFetch).toHaveBeenCalledWith("/api/v1/media/upload", {
        method: HTTP_METHOD.POST,
        headers: { Authorization: "Bearer u3" },
        body: expect.any(FormData),
      });
      expect(result.assetId).toBe("uploaded-asset-id");
    });

    it("throws on upload failure", async () => {
      mockedGetSession.mockResolvedValueOnce(null);
      mockFetch.mockResolvedValueOnce({ ok: false, status: 413 });

      const file = new File(["x".repeat(200)], "big.mp4");
      await expect(MediaApi.uploadMedia(file)).rejects.toThrow("Media upload failed with status 413");
    });
  });
});
