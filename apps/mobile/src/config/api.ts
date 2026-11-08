/**
 * @file api.ts
 * @description Resolves the edge base URL for the mobile client and carries its bearer token.
 *
 * The mobile app talks to the **edge** (:8088), not to the Next.js BFF. AGENTS.md §8 requires the
 * BFF for the *browser*, and for a good reason — a page must never learn a service port. A native
 * app is a different client: the BFF authenticates with a next-auth session **cookie**, which
 * React Native cannot obtain or carry, while the edge already speaks `Authorization: Bearer` and
 * exchanges API keys for JWTs (`ApiKeyExchangeFilter`). Routing the app through the BFF would have
 * meant inventing a second, native-only authentication path inside a component whose whole purpose
 * is to serve the browser.
 *
 * Where the edge lives is not a build-time constant: `config/discovery.ts` asks the signed manifest on krizaka.com
 * (@krizaka/discovery, krizaka/.github ADR-003) and registers itself here with `setEdgeBase`, so moving the edge never
 * needs a store release. Until it does (a unit test, the first frame), the dev edge below answers.
 */

/**
 * The edge of the public dev site. www.orazaka.com itself is the Next.js BFF (a cookie session a native app cannot
 * hold); its edge answers a bearer client under `/edge`. It is the baked default and the legacy address of the app.
 */
export const DEV_EDGE_URL = "https://www.orazaka.com/edge";

let edgeBaseProvider: () => string = () => DEV_EDGE_URL;

/** Registers where the edge address comes from (discovery). Read at every request, never kept. */
export function setEdgeBase(provider: () => string): void {
  edgeBaseProvider = provider;
}

/** `https://host/edge///` → `https://host/edge` (a loop, not a regular expression: the input is a URL read from elsewhere). */
export function trimSlashes(value: string): string {
  let end = value.length;
  while (end > 0 && value[end - 1] === "/") end -= 1;
  return value.slice(0, end);
}

/** Absolute base URL of the edge, as of now. */
export function edgeBase(): string {
  return trimSlashes(edgeBaseProvider());
}

/** Joins the edge base with an API path (e.g. `"/api/v1/studios"`). */
export function edgeUrl(path: string): string {
  const rooted = path.startsWith("/") ? path : `/${path}`;
  return `${edgeBase()}${rooted}`;
}

/**
 * Raised when the edge answers with a non-2xx status.
 *
 * Carries the status so callers can tell "your session expired" (401) from "the server is having
 * a bad day" (500). Collapsing both into one message is how an app ends up telling a signed-out
 * user that the network is down.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/** Reads the message an Sovkora service puts in its error body, if it left one. */
function messageOf(body: unknown, fallback: string): string {
  if (typeof body === "object" && body !== null) {
    const record = body as Record<string, unknown>;
    for (const key of ["error", "message"]) {
      if (typeof record[key] === "string" && record[key] !== "") {
        return record[key];
      }
    }
  }
  return fallback;
}

/**
 * The language the app speaks, sent as `Accept-Language` so the platform answers in it (a Studio's name, its fields).
 * Set once by the app's locale provider; the default is English, like the catalogues.
 */
let requestLanguage = "en";
export function setRequestLanguage(language: string): void {
  requestLanguage = language;
}

interface RequestOptions {
  readonly method?: string;
  readonly body?: unknown;
  /** The bearer token; omitted on the public auth endpoints. */
  readonly token?: string | null;
}

/**
 * One request to the edge, with the bearer attached and the error shape normalised.
 *
 * Every screen goes through here so that "how do we talk to the backend" is one answer: no screen
 * builds a URL, sets a header, or decides what a failed response means.
 *
 * @param path the API path
 * @param options method, JSON body and bearer token
 * @returns the parsed JSON body, or `undefined` for an empty 204
 * @throws ApiError when the edge answers non-2xx
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, token } = options;

  const response = await fetch(edgeUrl(path), {
    method,
    headers: {
      "Content-Type": "application/json",
      "Accept-Language": requestLanguage,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

  const text = await response.text();
  // A gateway error is an HTML page, not JSON: a failed status is an ApiError whatever the body is.
  let parsed: unknown;
  try {
    parsed = text === "" ? undefined : JSON.parse(text);
  } catch {
    if (response.ok) throw new ApiError(response.status, "Unreadable response");
    parsed = undefined;
  }

  if (!response.ok) {
    const message = messageOf(parsed, `Request failed (${response.status})`);
    // The website's BFF answers a bearer client like this: the address is the website, not the API.
    if (response.status === 401 && message.includes("Missing active security context")) {
      throw new ApiError(
        response.status,
        `${edgeBase()} is the website, not the edge: its apiBase in the services manifest must be ${DEV_EDGE_URL} (or your own edge).`,
      );
    }
    throw new ApiError(response.status, message);
  }
  return parsed as T;
}
