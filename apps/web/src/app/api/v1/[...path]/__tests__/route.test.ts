/** @jest-environment node */
import { DELETE, POST } from "@/app/api/v1/[...path]/route";

jest.mock("next-auth/next", () => ({ getServerSession: jest.fn().mockResolvedValue({ user: { id: "u1" } }) }));
jest.mock("@/core/auth/auth-options", () => ({ authOptions: {} }));

const call = (handler: typeof POST, status: number, headers: Record<string, string> = {}) => {
  global.fetch = jest.fn().mockResolvedValue(new Response(null, { status, headers })) as never;
  return handler(new Request("http://web/api/v1/studios/runs/r1/approve", { method: "POST", body: "{}" }), {
    params: Promise.resolve({ path: ["studios", "runs", "r1", "approve"] }),
  });
};

describe("BFF proxy: bodiless upstream answers", () => {
  it.each([204, 205, 304])("passes a %i through instead of failing with a 500", async (status) => {
    const res = await call(POST, status);
    expect(res.status).toBe(status);
  });

  it("passes an uninstall's 204 (DELETE) through", async () => {
    const res = await call(DELETE, 204);
    expect(res.status).toBe(204);
  });

  it("still relays a JSON body", async () => {
    global.fetch = jest
      .fn()
      .mockResolvedValue(
        new Response('{"a":1}', { status: 200, headers: { "content-type": "application/json" } }),
      ) as never;
    const res = await POST(new Request("http://web/api/v1/x", { method: "POST", body: "{}" }), {
      params: Promise.resolve({ path: ["x"] }),
    });
    expect(await res.json()).toEqual({ a: 1 });
  });
});
