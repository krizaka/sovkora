/**
 * @file bff-body.ts
 * @description What the console's BFF sends on to the edge: a multipart upload as it came (its bytes, and its type,
 * which carries the boundary — the manual import of the law corpus, ADR-089 §8), any other write as JSON text, a read
 * nothing. Pure, so `npm run test:console` checks it without a server.
 */

const WRITES = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export interface ForwardedBody {
  body: BodyInit | undefined;
  contentType: string;
}

export async function forwardedBody(request: Request, method: string): Promise<ForwardedBody> {
  if (!WRITES.has(method)) return { body: undefined, contentType: "application/json" };
  const type = request.headers.get("content-type") ?? "";
  if (type.toLowerCase().startsWith("multipart/form-data"))
    return { body: await request.arrayBuffer(), contentType: type };
  return { body: await request.text(), contentType: "application/json" };
}
