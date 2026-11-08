/** @jest-environment node */
import { POST } from "@/app/api/auth/register/route";

const req = () =>
  new Request("http://web/api/auth/register", { method: "POST", body: JSON.stringify({ email: "a@b.c" }) });

describe("POST /api/auth/register (Dev preview: site.signup-closed)", () => {
  const saved = process.env.AUTH_SIGNUP_ENABLED;
  afterEach(() => {
    if (saved === undefined) delete process.env.AUTH_SIGNUP_ENABLED;
    else process.env.AUTH_SIGNUP_ENABLED = saved;
  });

  it("answers 403 and calls no backend while AUTH_SIGNUP_ENABLED is off (the default)", async () => {
    delete process.env.AUTH_SIGNUP_ENABLED;
    global.fetch = jest.fn() as never;
    const res = await POST(req());
    expect(res.status).toBe(403);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("answers 403 for any value that is not a yes", async () => {
    process.env.AUTH_SIGNUP_ENABLED = "false";
    expect((await POST(req())).status).toBe(403);
  });

  it("relays to the edge when sign-up is open", async () => {
    process.env.AUTH_SIGNUP_ENABLED = "true";
    global.fetch = jest
      .fn()
      .mockResolvedValue(
        new Response('{"ok":true}', { status: 201, headers: { "content-type": "application/json" } }),
      ) as never;
    const res = await POST(req());
    expect(res.status).toBe(201);
    expect((global.fetch as jest.Mock).mock.calls[0][0]).toMatch(/\/api\/v1\/auth\/register$/);
  });
});
