import { edgeUrl } from "../config/api";

/**
 * Where a media artefact is read from. The run answers a path on the edge (`/api/v1/assets/<job>/video.mp4`) or an
 * absolute URL the executor published; the edge's assets need the Bearer, a public URL must not receive it.
 */
export function mediaSource(value: string, token: string | null): { uri: string; headers?: Record<string, string> } {
  if (/^https?:\/\//.test(value)) return { uri: value };
  return {
    uri: edgeUrl(value),
    ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {}),
  };
}
