/**
 * @file rest-client.test.ts
 * @description The error message a failed REST call surfaces.
 */

import { RestError, restRequest } from "@/services/rest-client";

jest.mock("next-auth/react", () => ({
  signOut: jest.fn(),
}));

const mockFetch = jest.fn();
global.fetch = mockFetch;

function failing(status: number, body: unknown) {
  return {
    ok: false,
    status,
    statusText: "Error",
    json: async () => body,
  };
}

describe("restRequest errors", () => {
  beforeEach(() => mockFetch.mockReset());

  it("reads the detail of a Problem Details body", async () => {
    mockFetch.mockResolvedValueOnce(
      failing(404, {
        type: "https://krizaka.com/problems/job-not-found",
        title: "job not found",
        status: 404,
        detail: "No job j1.",
        code: "job-not-found",
        requestId: "r-1",
      }),
    );

    await expect(restRequest("/api/v1/jobs/j1")).rejects.toThrow("No job j1.");
  });

  it("still reads the legacy error field", async () => {
    mockFetch.mockResolvedValueOnce(failing(400, { error: "Model 'x' is not supported" }));

    await expect(restRequest("/api/v1/chat")).rejects.toThrow("Model 'x' is not supported");
  });

  it("falls back to the status when the body says nothing", async () => {
    mockFetch.mockResolvedValueOnce(failing(500, {}));

    await expect(restRequest("/api/v1/chat")).rejects.toThrow("REST request failed: 500 Error");
  });

  it("keeps the status and the parsed body on the error, and reads the Studio service's `message`", async () => {
    mockFetch.mockResolvedValueOnce(
      failing(451, { status: "consent_required", message: "consent needed", consentVersion: "1.0" }),
    );

    const failure = await restRequest("/api/v1/studios/x/installations").catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(RestError);
    expect(failure).toMatchObject({ message: "consent needed", status: 451, body: { consentVersion: "1.0" } });
  });
});
