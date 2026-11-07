/**
 * @file cdn.ts
 * @description URLs of the media of this site that live on Bunny (never in the repository). A file is
 * `<cdn>/<path>.<hash>.<ext>` (`sites/<site>/<kind>/…`, Bunny layout v2, ADR-014) and a URL never changes content. The hashes are `media.generated.json`
 * (scripts/sync-media.mjs); `NEXT_PUBLIC_MEDIA_CDN_URL` switches the host (read at build).
 */
import data from "./media.generated.json";

const withoutTrailingSlashes = (url: string): string => {
  let end = url.length;
  while (end > 0 && url[end - 1] === "/") end -= 1;
  return url.slice(0, end);
};

export const MEDIA_CDN_URL = withoutTrailingSlashes(
  process.env.NEXT_PUBLIC_MEDIA_CDN_URL || "https://krizaka-assets.b-cdn.net",
);

const FILES: Record<string, string> = data.files;

/** `sites/orazaka/mobile/clip-ask.mp4` → `https://…/sites/orazaka/mobile/clip-ask.<hash>.mp4`; throws on an unknown file. */
export function mediaUrl(path: string): string {
  const hash = FILES[path];
  if (!hash) throw new Error(`${path} is not a known CDN file`);
  const dot = path.lastIndexOf(".");
  return `${MEDIA_CDN_URL}/${path.slice(0, dot)}.${hash}${path.slice(dot)}`;
}

export const hasMedia = (path: string): boolean => path in FILES;

/** Every file the manifest knows under a prefix, sorted: what a page lists when it is data-driven (`sites/orazaka/video/`). */
export const mediaPaths = (prefix: string): string[] =>
  Object.keys(FILES)
    .filter((path) => path.startsWith(prefix))
    .sort((a, b) => a.localeCompare(b));
