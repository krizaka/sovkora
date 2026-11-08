/**
 * Answers the app's `fetch` from recorded edge responses (`src/__fixtures__/api`, recorded from the local Sovkora
 * stack as the demo persona): a route is a path prefix → a JSON body (200) or `{ status, body }`. Every call is kept.
 */
export interface Call {
  readonly path: string;
  readonly method: string;
  readonly headers: Record<string, string>;
  readonly body: unknown;
}

type Answer = { status: number; body: unknown };
const isAnswer = (r: unknown): r is Answer => typeof r === "object" && r !== null && "status" in r && "body" in r;

export function answerFetch(routes: Record<string, unknown>): Call[] {
  const calls: Call[] = [];
  global.fetch = jest.fn(
    async (input: unknown, init?: { method?: string; headers?: Record<string, string>; body?: unknown }) => {
      const url = new URL(String(input));
      // The app talks to the dev edge (https://www.orazaka.com/edge/…): the recorded routes are the edge's own paths.
      const path = url.pathname.replace(/^\/edge(?=\/)/, "") + url.search;
      calls.push({
        path,
        method: init?.method ?? "GET",
        headers: init?.headers ?? {},
        body: typeof init?.body === "string" && init.body !== "" ? JSON.parse(init.body) : undefined,
      });
      const key = Object.keys(routes)
        .filter((k) => path.startsWith(k))
        .sort((a, b) => b.length - a.length)[0];
      if (key === undefined) throw new TypeError("Network request failed");
      const route = routes[key];
      const { status, body } = isAnswer(route) ? route : { status: 200, body: route };
      return {
        ok: status >= 200 && status < 300,
        status,
        text: async () => JSON.stringify(body),
        json: async () => body,
      } as Response;
    },
  ) as unknown as typeof fetch;
  return calls;
}
