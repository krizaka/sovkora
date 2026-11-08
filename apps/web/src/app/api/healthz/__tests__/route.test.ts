/** @jest-environment node */
import { GET } from "@/app/api/healthz/route";

describe("GET /api/healthz (Dev preview: site.healthy)", () => {
  it("answers 200 with the version of the build, uncached", async () => {
    const res = await GET();
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("no-store");
    const body = await res.json();
    expect(body.status).toBe("ok");
    expect(typeof body.version).toBe("string");
  });
});
