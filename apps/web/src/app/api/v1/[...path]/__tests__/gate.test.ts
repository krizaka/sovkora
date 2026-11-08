/** @jest-environment node */
import { getServerSession } from "next-auth/next";
import { GET } from "@/app/api/v1/[...path]/route";

jest.mock("next-auth/next", () => ({ getServerSession: jest.fn() }));
jest.mock("@/core/auth/auth-options", () => ({ authOptions: {} }));

describe("BFF proxy: the gate", () => {
  it("answers 401 without a session and never calls the backend", async () => {
    (getServerSession as jest.Mock).mockResolvedValue(null);
    global.fetch = jest.fn() as never;
    const res = await GET(new Request("http://web/api/v1/profile"), { params: Promise.resolve({ path: ["profile"] }) });
    expect(res.status).toBe(401);
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
