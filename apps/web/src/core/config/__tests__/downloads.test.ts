import { readDownloads, readDownloadsFromEnv } from "@/core/config/downloads";

const ON = { APP_DOWNLOADS_MAC_ENABLED: "true" };
const EDGE = { ROUTER_URL: "http://edge.test" };

const answer = (body: unknown, ok = true) =>
  Promise.resolve({ ok, json: () => Promise.resolve(body) }) as unknown as Promise<Response>;

describe("readDownloadsFromEnv", () => {
  it("is off by default, empty or anything but « true »", () => {
    expect(readDownloadsFromEnv({}).macEnabled).toBe(false);
    expect(readDownloadsFromEnv({ APP_DOWNLOADS_MAC_ENABLED: "" }).macEnabled).toBe(false);
    expect(readDownloadsFromEnv({ APP_DOWNLOADS_MAC_ENABLED: "1" }).macEnabled).toBe(false);
    expect(readDownloadsFromEnv({ APP_DOWNLOADS_MAC_ENABLED: "false" }).macEnabled).toBe(false);
  });
  it("is on only when set to true", () => {
    expect(readDownloadsFromEnv(ON).macEnabled).toBe(true);
    expect(readDownloadsFromEnv({ APP_DOWNLOADS_MAC_ENABLED: " TRUE " }).macEnabled).toBe(true);
  });
});

describe("readDownloads (the admin's setting, the environment only as the bootstrap default)", () => {
  it("asks the public settings endpoint of the edge, with no credential, and trusts a boolean answer", async () => {
    const fetcher = jest.fn(() => answer({ macEnabled: true }));
    expect((await readDownloads({ vars: EDGE, fetcher })).macEnabled).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1);
    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit & { next?: { revalidate: number } }];
    expect(url).toBe("http://edge.test/api/v1/studios/public/downloads");
    expect(init.headers).toBeUndefined();
    expect(init.next?.revalidate).toBe(60);
  });
  it("the admin's off wins over an environment value that is on", async () => {
    expect(
      (await readDownloads({ vars: { ...EDGE, ...ON }, fetcher: () => answer({ macEnabled: false }) })).macEnabled,
    ).toBe(false);
  });
  it("falls back to the environment value when no edge is configured, without calling anything", async () => {
    const fetcher = jest.fn();
    expect((await readDownloads({ vars: ON, fetcher })).macEnabled).toBe(true);
    expect((await readDownloads({ vars: {}, fetcher })).macEnabled).toBe(false);
    expect(fetcher).not.toHaveBeenCalled();
  });
  it("falls back to the environment value (off by default) when the call fails or answers something else", async () => {
    expect((await readDownloads({ vars: EDGE, fetcher: () => Promise.reject(new Error("down")) })).macEnabled).toBe(
      false,
    );
    expect((await readDownloads({ vars: EDGE, fetcher: () => answer({}, false) })).macEnabled).toBe(false);
    expect((await readDownloads({ vars: EDGE, fetcher: () => answer({ macEnabled: "true" }) })).macEnabled).toBe(false);
    expect((await readDownloads({ vars: EDGE, fetcher: () => answer(null) })).macEnabled).toBe(false);
    expect(
      (await readDownloads({ vars: { ...EDGE, ...ON }, fetcher: () => Promise.reject(new Error("down")) })).macEnabled,
    ).toBe(true);
  });
});
