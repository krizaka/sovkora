/**
 * @file media.api.test.ts
 * @description Tests for the media API adapter. It calls the BFF only, which adds the session's Bearer token
 * server-side: the browser never holds or sends a backend token (krizaka-restricted#347).
 */

import { MediaApi } from "@/services/media.api";
import { HTTP_METHOD } from "@/core/constants/http.constants";

const mockFetch = jest.fn();
global.fetch = mockFetch;

describe("MediaApi", () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  describe("searchRag", () => {
    it("sends the encoded search query to the BFF, with no Authorization header", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ context: "Virtual threads use..." }),
      });

      const result = await MediaApi.searchRag("virtual threads");

      expect(mockFetch).toHaveBeenCalledWith("/api/v1/media/search?q=virtual%20threads");
      expect(result).toBe("Virtual threads use...");
    });

    it("says so when nothing matches", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ context: "" }),
      });

      expect(await MediaApi.searchRag("test")).toBe("No context found matching query.");
    });

    it("throws on HTTP error", async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 500 });

      await expect(MediaApi.searchRag("query")).rejects.toThrow("RAG query failed with status 500");
    });
  });

  describe("analyzeVideo", () => {
    it("sends POST with assetId and model, with no Authorization header", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ jobId: "job-abc" }),
      });

      const result = await MediaApi.analyzeVideo("asset-123", "llava:latest");

      expect(mockFetch).toHaveBeenCalledWith("/api/v1/media/analyze/video", {
        method: HTTP_METHOD.POST,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ assetId: "asset-123", model: "llava:latest" }),
      });
      expect(result.jobId).toBe("job-abc");
    });

    it("throws on HTTP error", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        statusText: "Bad Request",
      });

      await expect(MediaApi.analyzeVideo("a", "m")).rejects.toThrow("Analysis failed: Bad Request");
    });
  });

  describe("uploadMedia", () => {
    it("sends FormData with the file, with no Authorization header", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ assetId: "uploaded-asset-id" }),
      });

      const file = new File(["content"], "test.mp4", { type: "video/mp4" });
      const result = await MediaApi.uploadMedia(file);

      expect(mockFetch).toHaveBeenCalledWith("/api/v1/media/upload", {
        method: HTTP_METHOD.POST,
        body: expect.any(FormData),
      });
      expect(result.assetId).toBe("uploaded-asset-id");
    });

    it("throws on upload failure", async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 413 });

      const file = new File(["x".repeat(200)], "big.mp4");
      await expect(MediaApi.uploadMedia(file)).rejects.toThrow("Media upload failed with status 413");
    });
  });
});
