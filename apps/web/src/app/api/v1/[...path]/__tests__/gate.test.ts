/** @jest-environment node */
import { NextRequest } from "next/server";
import { backendToken } from "@/core/auth/backend-token";
import { GET } from "@/app/api/v1/[...path]/route";

jest.mock("@/core/auth/backend-token", () => ({ backendToken: jest.fn() }));

describe("BFF proxy: the gate", () => {
  it("answers 401 without a session and never calls the backend", async () => {
    (backendToken as jest.Mock).mockResolvedValue(null);
    global.fetch = jest.fn() as never;
    const res = await GET(new NextRequest("http://web/api/v1/profile"), {
      params: Promise.resolve({ path: ["profile"] }),
    });
    expect(res.status).toBe(401);
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
