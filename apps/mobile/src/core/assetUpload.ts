import { ApiError, edgeUrl } from "../config/api";

/** The MIME type of a photo from its file name; the library gives a uri, rarely a type. */
export function imageMimeOf(fileName: string | null | undefined): string {
  const extension = /\.([a-z0-9]+)$/i.exec(fileName ?? "")?.[1]?.toLowerCase();
  switch (extension) {
    case "png":
      return "image/png";
    case "webp":
      return "image/webp";
    case "heic":
      return "image/heic";
    default:
      return "image/jpeg";
  }
}

interface UploadOptions {
  /** The file on the device. */
  readonly uri: string;
  readonly fileName?: string | null;
  readonly token: string | null;
}

/**
 * Sends a photo from the device to the authenticated upload route (`POST /api/v1/media/upload`, multipart) and returns
 * the asset id a Studio input takes — the person never sees or types it.
 *
 * @throws ApiError when the edge answers non-2xx or sends no asset id
 */
export async function uploadAsset({ uri, fileName, token }: UploadOptions): Promise<string> {
  const name = fileName ?? uri.split("/").pop() ?? "photo.jpg";
  const form = new FormData();
  // React Native reads a { uri, name, type } part from the device's file system.
  form.append("file", { uri, name, type: imageMimeOf(name) } as unknown as Blob);
  const response = await fetch(edgeUrl("/api/v1/media/upload"), {
    method: "POST",
    // No Content-Type: the runtime sets the multipart boundary.
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form as unknown as RequestInit["body"],
  });
  const text = await response.text();
  let parsed: { assetId?: unknown } | undefined;
  try {
    parsed = text === "" ? undefined : (JSON.parse(text) as { assetId?: unknown });
  } catch {
    parsed = undefined;
  }
  if (!response.ok || typeof parsed?.assetId !== "string" || parsed.assetId === "") {
    throw new ApiError(response.status, `Upload failed (${response.status})`);
  }
  return parsed.assetId;
}
