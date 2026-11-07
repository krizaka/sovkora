import { act, renderHook, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { errorForStatus, isHttpsUrl, useWebhooks, type WebhookEndpoint } from "@/features/profile/hooks/useWebhooks";

const headers = async () => ({ "Content-Type": "application/json" });
const endpoint: WebhookEndpoint = {
  id: "e1",
  url: "https://hooks.example.com/x",
  events: ["run.completed"],
  enabled: true,
  createdAt: "2026-10-20T00:00:00Z",
};

function reply(status: number, body?: unknown): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as unknown as Response;
}

/** jsdom has no fetch: the hook's calls go to a mock the test scripts. */
function mockFetch(): jest.Mock {
  const mock = jest.fn();
  global.fetch = mock as unknown as typeof fetch;
  return mock;
}

describe("webhook address and error rules", () => {
  it("accepts only an https address with a host and no credentials", () => {
    expect(isHttpsUrl("https://hooks.example.com/x")).toBe(true);
    expect(isHttpsUrl("http://hooks.example.com/x")).toBe(false);
    expect(isHttpsUrl("https://user:pw@hooks.example.com")).toBe(false);
    expect(isHttpsUrl("nope")).toBe(false);
  });

  it("maps the API's refusals to what the form says", () => {
    expect(errorForStatus(400)).toBe("url");
    expect(errorForStatus(409)).toBe("limit");
    expect(errorForStatus(500)).toBe("generic");
  });
});

describe("useWebhooks", () => {
  it("lists the endpoints on mount", async () => {
    mockFetch().mockResolvedValue(reply(200, [endpoint]));
    const { result } = renderHook(() => useWebhooks(headers), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.endpoints).toEqual([endpoint]);
  });

  it("shows the secret of a new endpoint once, and refuses a non-https address without calling the API", async () => {
    const fetchMock = mockFetch();
    fetchMock.mockResolvedValueOnce(reply(200, []));
    const { result } = renderHook(() => useWebhooks(headers), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    let ok = true;
    await act(async () => {
      ok = await result.current.create("http://insecure.example.com", ["run.completed"]);
    });
    expect(ok).toBe(false);
    expect(result.current.error).toBe("url");
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fetchMock
      .mockResolvedValueOnce(reply(201, { ...endpoint, secret: "whsec_abc" }))
      .mockResolvedValueOnce(reply(200, [endpoint]));
    await act(async () => {
      ok = await result.current.create("https://hooks.example.com/x", ["run.completed"]);
    });
    expect(ok).toBe(true);
    expect(result.current.revealed).toEqual({ url: endpoint.url, secret: "whsec_abc" });
    act(() => result.current.dismissRevealed());
    expect(result.current.revealed).toBeNull();
  });

  it("says when the limit is reached", async () => {
    const fetchMock = mockFetch();
    fetchMock.mockResolvedValueOnce(reply(200, [])).mockResolvedValueOnce(reply(409, { status: "too_many_webhooks" }));
    const { result } = renderHook(() => useWebhooks(headers), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    await act(async () => {
      await result.current.create("https://hooks.example.com/x", ["run.completed"]);
    });
    expect(result.current.error).toBe("limit");
  });

  it("rotates a secret and shows the new one", async () => {
    const fetchMock = mockFetch();
    fetchMock.mockResolvedValueOnce(reply(200, [endpoint])).mockResolvedValueOnce(reply(200, { secret: "whsec_new" }));
    const { result } = renderHook(() => useWebhooks(headers), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    await act(async () => {
      await result.current.rotate(endpoint);
    });
    expect(result.current.revealed?.secret).toBe("whsec_new");
    expect(String(fetchMock.mock.calls[1][0])).toBe("/api/v1/studios/webhooks/e1/rotate");
  });

  it("removes a deleted endpoint at once and opens an endpoint's log", async () => {
    const fetchMock = mockFetch();
    fetchMock
      .mockResolvedValueOnce(reply(200, [endpoint]))
      .mockResolvedValueOnce(
        reply(200, [
          {
            id: "d1",
            runId: "r1",
            event: "run.completed",
            status: "DELIVERED",
            attempts: 1,
            lastHttpStatus: 200,
            lastError: null,
            createdAt: "2026-10-20T00:00:00Z",
            deliveredAt: "2026-10-20T00:00:01Z",
          },
        ]),
      )
      .mockResolvedValueOnce(reply(204));
    const { result } = renderHook(() => useWebhooks(headers), { wrapper: queryWrapper() });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.openLog(endpoint);
    });
    expect(result.current.log?.deliveries).toHaveLength(1);

    await act(async () => {
      await result.current.remove(endpoint);
    });
    await waitFor(() => expect(result.current.endpoints).toEqual([]));
  });
});
