import { getSession } from "next-auth/react";
import { HTTP_METHOD } from "@/core/constants/http.constants";

export interface VideoAnalysisResult {
  transcript: string;
  keyframeCount: number;
}

/**
 * Stateless adapter exposing media and search related network operations.
 */
export const MediaApi = {
  /**
   * Performs a passive RAG context search against the semantic index.
   *
   * @param query - The search query.
   * @returns The retrieved semantic context string.
   */
  searchRag: async (query: string): Promise<string> => {
    const session = await getSession();
    const token = session?.user?.id || "user-mock";

    const response = await fetch(`/api/v1/media/search?q=${encodeURIComponent(query)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      throw new Error(`RAG query failed with status ${response.status}`);
    }

    const data = await response.json();
    return data.context || "No context found matching query.";
  },

  /**
   * Triggers keyframe + transcript extraction analysis on an already uploaded video asset ID.
   *
   * @param assetId - The UUID reference of the uploaded video file.
   * @param model - The model selected for analysis.
   * @returns The active Job ID wrapper.
   */
  analyzeVideo: async (assetId: string, model: string): Promise<{ jobId: string }> => {
    const session = await getSession();
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (session?.user?.id) {
      headers["Authorization"] = `Bearer ${session.user.id}`;
    }

    const response = await fetch("/api/v1/media/analyze/video", {
      method: HTTP_METHOD.POST,
      headers,
      body: JSON.stringify({ assetId, model }),
    });

    if (!response.ok) {
      throw new Error(`Analysis failed: ${response.statusText}`);
    }

    const data = await response.json();
    return {
      jobId: data.jobId,
    };
  },

  /**
   * Uploads raw media file and retrieves a unique asset reference UUID.
   *
   * @param file - The raw file to upload.
   * @returns The parsed upload response with assetId.
   */
  uploadMedia: async (file: File): Promise<{ assetId: string }> => {
    const formData = new FormData();
    formData.append("file", file);

    const session = await getSession();
    const headers: Record<string, string> = {};
    if (session?.user?.id) {
      headers["Authorization"] = `Bearer ${session.user.id}`;
    }

    const response = await fetch("/api/v1/media/upload", {
      method: HTTP_METHOD.POST,
      headers,
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`Media upload failed with status ${response.status}`);
    }

    return response.json();
  },
} as const;
