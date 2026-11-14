// A RunPod pod's job service token for the launcher tests (krizaka-internal#3645): the claims the job service mints, unsigned
// (the launcher and the body builder only read them; the job service verifies). Not a test file: imported by the tests.
import { createServer } from "node:http";

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
export const jwt = (claims, header = { alg: "HS256", kid: "gpu-pod-v1" }) => `${b64(header)}.${b64(claims)}.sig`;
export const podToken = (key, extra = {}) =>
  jwt({ sub: `gpu-pod:${key}`, aud: ["sovkora-job-service"], roles: ["GPU_POD"], jti: `jti-${key}`, exp: 4102444800, ...extra });
/** What `mint_service_token` gives the launcher's own call: a SERVICE token, which must never reach a pod. */
export const SERVICE_JWT = jwt({ sub: "sovkora-launcher", roles: ["SERVICE"], exp: 4102444800 }, { alg: "HS256", typ: "JWT" });
/** Every JWT-shaped string in a text, decoded. */
export const jwtsIn = (text) =>
  [...text.matchAll(/eyJ[\w-]+\.(eyJ[\w-]+)\.[\w-]+/g)].map((m) => JSON.parse(Buffer.from(m[1], "base64url").toString()));

/** The job service's four pod-token routes, stubbed: what it was asked, by whom, and which pod tokens are live. */
export async function jobService({ refuseMint = false } = {}) {
  const state = { calls: [], live: new Map() };
  const server = createServer((req, res) => {
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      const path = new URL(req.url, "http://x").pathname;
      const bearer = (req.headers.authorization ?? "").replace(/^Bearer /, "");
      const body = raw ? JSON.parse(raw) : undefined;
      state.calls.push({ method: req.method, path, body, bearer });
      const send = (code, data) => { res.writeHead(code, { "content-type": "application/json" }); res.end(data ? JSON.stringify(data) : ""); };
      const pod = [...state.live.values()].includes(bearer);
      if (pod) {
        if (/^\/internal\/v1\/(workers\/[^/]+\/heartbeat|jobs\/[^/]+\/input\/image)$/.test(path)) return send(404);
        return send(403);
      }
      if (bearer !== SERVICE_JWT) return send(401);
      let m;
      if ((m = path.match(/^\/internal\/v1\/gpu-pods\/([^/]+)\/token$/))) {
        if (req.method === "POST") {
          if (refuseMint) return send(503);
          const token = podToken(m[1], { lifetime: body.lifetimeSeconds });
          state.live.set(m[1], token);
          return send(201, { token, jti: `jti-${m[1]}`, expiresAt: "2026-11-15T03:00:00Z" });
        }
        const had = state.live.delete(m[1]);
        return send(200, { revoked: had ? [m[1]] : [] });
      }
      if (path === "/internal/v1/gpu-pods/tokens/sweep") {
        const revoked = [...state.live.keys()].filter((k) => !body.running.includes(k));
        revoked.forEach((k) => state.live.delete(k));
        return send(200, { revoked });
      }
      return send(404);
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  server.unref();
  return { state, base: `http://127.0.0.1:${server.address().port}`, close: () => { server.close(); server.closeAllConnections(); } };
}
