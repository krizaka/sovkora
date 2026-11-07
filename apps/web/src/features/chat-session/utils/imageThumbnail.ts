/** Longest-edge size (px) of the inline chat preview thumbnail. */
const THUMBNAIL_MAX_EDGE = 320;

/**
 * Produces a small, downscaled JPEG data URL preview of an image file, suitable
 * for persisting inline with a chat message so the user keeps a visible trace of
 * what they sent. Downscaling keeps the stored history well within localStorage
 * limits. Returns "" for non-images or when the browser canvas is unavailable.
 */
export async function fileToThumbnail(file: File, maxEdge: number = THUMBNAIL_MAX_EDGE): Promise<string> {
  if (!file.type.startsWith("image/")) return "";

  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });

  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const element = new Image();
    element.onload = () => resolve(element);
    element.onerror = () => reject(new Error("Failed to decode image"));
    element.src = dataUrl;
  });

  const scale = Math.min(1, maxEdge / Math.max(img.width, img.height));
  const width = Math.max(1, Math.round(img.width * scale));
  const height = Math.max(1, Math.round(img.height * scale));

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) return dataUrl;
  ctx.drawImage(img, 0, 0, width, height);
  return canvas.toDataURL("image/jpeg", 0.8);
}
