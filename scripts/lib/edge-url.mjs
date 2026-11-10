// The tools that call the public edge (journey-dev, studio-preflight) reach it only on its own origin and under its own path:
// the base is checked once, an id read from an answer is one path segment, and a path read from an answer cannot climb out.

const DEFAULT_EDGE = "https://dev.sovkora.com/edge";
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);
const LEAVES_EDGE = "the path must stay on the edge";

/** The edge base from ORAZAKA_EDGE_URL (default dev): https, or http on this machine only; never credentials in it. */
export function edgeBase(env = process.env) {
  const raw = env.ORAZAKA_EDGE_URL ?? DEFAULT_EDGE;
  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("ORAZAKA_EDGE_URL is not an edge URL");
  }
  const local = url.protocol === "http:" && LOCAL_HOSTS.has(url.hostname);
  const userInfo = url.username !== "" || url.password !== "";
  if ((url.protocol !== "https:" && !local) || userInfo || url.search || url.hash) {
    throw new Error("ORAZAKA_EDGE_URL must be an https edge URL (or http on localhost)");
  }
  let pathname = url.pathname;
  while (pathname.endsWith("/")) pathname = pathname.slice(0, -1);
  return Object.freeze({ origin: url.origin, pathname, href: url.origin + pathname });
}

/** The absolute URL of `path` (`/api/v1/…`, query allowed) under the edge; throws when it would leave the edge. */
export function edgeUrl(base, path) {
  const [pathPart] = String(path).split("?", 1);
  if (!pathPart.startsWith("/") || pathPart.startsWith("//") || pathPart.includes("\\")) throw new Error(LEAVES_EDGE);
  let decoded;
  try {
    decoded = decodeURIComponent(pathPart);
  } catch {
    throw new Error(LEAVES_EDGE);
  }
  if (decoded.split("/").some((s) => s === "." || s === "..")) throw new Error(LEAVES_EDGE);
  const url = new URL(base.href + path);
  if (url.origin !== base.origin || !url.pathname.startsWith(`${base.pathname}/`)) throw new Error(LEAVES_EDGE);
  return url.href;
}

/** An id read from an answer (a run, a session), as one path segment. */
export function idSegment(id) {
  const text = typeof id === "number" ? String(id) : id;
  if (typeof text !== "string" || !/^[A-Za-z0-9_-]+$/.test(text)) throw new Error("an id from the edge is not a plain id");
  return text;
}

/** A message from an answer, safe for one log line: control characters become spaces. */
export function oneLine(text) {
  return String(text).replace(/[\u0000-\u001f\u007f]/g, " ");
}
