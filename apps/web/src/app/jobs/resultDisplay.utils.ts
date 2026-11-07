/** Pure payload/format detection helpers for the job ResultDisplay. */

/** Extract a jobId from a result payload (object with jobId, or a JSON string carrying one). */
export function getJobId(payload: unknown): string | undefined {
  if (typeof payload === "object" && payload !== null) {
    if ("jobId" in payload) {
      return (payload as Record<string, unknown>).jobId as string;
    }
  } else if (typeof payload === "string") {
    try {
      const parsed = JSON.parse(payload);
      if (parsed && typeof parsed === "object" && "jobId" in parsed) {
        return parsed.jobId;
      }
    } catch {
      // Not a JSON string with a jobId
    }
  }
  return undefined;
}

export function isVideo(format: string | undefined, url: string): boolean {
  return (
    format === "mp4" ||
    (!!url &&
      (url.startsWith("data:video/") ||
        url.endsWith(".mp4") ||
        (url.includes("/api/v1/assets/") && url.endsWith(".mp4"))))
  );
}

export function isAudio(format: string | undefined, url: string): boolean {
  return (
    format === "mp3" ||
    (!!url &&
      (url.startsWith("data:audio/") ||
        url.endsWith(".mp3") ||
        url.endsWith(".wav") ||
        (url.includes("/api/v1/assets/") && (url.endsWith(".mp3") || url.endsWith(".wav")))))
  );
}

export function isImage(format: string | undefined, url: string): boolean {
  return (
    !!url &&
    (url.startsWith("data:image/") ||
      url.startsWith("data:application/") ||
      format === "png" ||
      format === "jpg" ||
      format === "jpeg" ||
      url.endsWith(".png") ||
      url.endsWith(".jpg") ||
      url.endsWith(".jpeg") ||
      (url.includes("/api/v1/assets/") && (url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg"))))
  );
}
