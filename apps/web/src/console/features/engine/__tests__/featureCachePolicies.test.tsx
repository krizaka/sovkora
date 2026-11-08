import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { FeatureToolsScreen } from "@/console/features/engine/components/FeatureToolsScreen";
import { featureToolsCopy } from "@/console/core/copy/featureTools";
import { queryWrapper } from "@/core/testing/queryWrapper";

const en = featureToolsCopy.en;
const fr = featureToolsCopy.fr;
const STRATEGIES = ["NONE", "SYSTEM_AND_TOOLS", "SYSTEM_ONLY"];

type Row = { featureKey: string; strategy: string; isDefault: boolean };
let rows: Row[] = [];

/** A stub of the admin endpoints that behaves like the server: it stores, removes and refuses an unknown strategy. */
function mockServer() {
  const calls: { url: string; method: string; body?: string }[] = [];
  global.fetch = jest.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push({ url, method, body: init?.body as string | undefined });
    const reply = (status: number, body: unknown) =>
      ({ ok: status < 400, status, statusText: "", json: async () => body }) as Response;
    if (url.endsWith("/feature-cache-policies")) {
      return reply(200, { strategies: STRATEGIES, policies: rows });
    }
    if (!url.includes("/feature-cache-policies/")) {
      return reply(200, url.endsWith("/feature-routes") ? [] : { min: 1, max: 32, bounds: [] });
    }
    const key = decodeURIComponent(url.split("/feature-cache-policies/")[1]);
    if (method === "PUT") {
      const strategy = (JSON.parse(init!.body as string) as { strategy: string }).strategy;
      if (!STRATEGIES.includes(strategy)) {
        return reply(400, { code: "invalid-request", detail: "strategy is unknown" });
      }
      rows = [...rows.filter((r) => r.featureKey !== key), { featureKey: key, strategy, isDefault: key === "*" }].sort(
        (a, b) => a.featureKey.localeCompare(b.featureKey),
      );
      return reply(200, { featureKey: key, strategy, isDefault: key === "*" });
    }
    rows = rows.filter((r) => r.featureKey !== key);
    return reply(204, null);
  }) as unknown as typeof fetch;
  return calls;
}

const row = (key: string) => within(document.querySelector(`[data-cache-policy="${key}"]`) as HTMLElement);

beforeEach(() => {
  rows = [
    { featureKey: "*", strategy: "SYSTEM_ONLY", isDefault: true },
    { featureKey: "agent.code.issue-to-pr", strategy: "SYSTEM_AND_TOOLS", isDefault: false },
  ];
});

describe("FeatureToolsScreen: the prompt-cache policy (#173)", () => {
  it("lists the default row and the feature's own policy with the chosen strategy", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    expect(await screen.findByRole("heading", { name: en.cacheTitle, level: 2 })).toBeTruthy();
    await screen.findByText(en.cacheDefaultRow);
    const own = row("agent.code.issue-to-pr").getByRole("radio", { name: en.cacheSYSTEM_AND_TOOLS });
    expect(own.getAttribute("aria-checked")).toBe("true");
    expect(row("*").getByRole("radio", { name: en.cacheSYSTEM_ONLY }).getAttribute("aria-checked")).toBe("true");
    expect(row("*").queryByRole("button", { name: en.remove })).toBeNull();
  });

  it("saves another strategy and says it applies on the next turn", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.cacheDefaultRow);
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("radio", { name: en.cacheNONE }));
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    expect(await screen.findByText(en.cacheSaved)).toBeTruthy();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.url.endsWith("/feature-cache-policies/agent.code.issue-to-pr")).toBe(true);
    expect(JSON.parse(put!.body!)).toEqual({ strategy: "NONE" });
  });

  it("returns a feature to the default, and adds the policy of a feature that has none", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.cacheDefaultRow);
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.remove }));
    await waitFor(() => expect(document.querySelector('[data-cache-policy="agent.code.issue-to-pr"]')).toBeNull());
    expect(
      calls.some((c) => c.method === "DELETE" && c.url.endsWith("/feature-cache-policies/agent.code.issue-to-pr")),
    ).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: en.cacheAdd }));
    expect(await screen.findByText(en.invalidKey)).toBeTruthy();
    fireEvent.change(screen.getAllByLabelText(en.featureKey).at(-1) as HTMLInputElement, {
      target: { value: "agent.other" },
    });
    fireEvent.click(screen.getByRole("button", { name: en.cacheAdd }));
    expect(await screen.findByText(en.cacheChoose)).toBeTruthy();
    fireEvent.click(document.getElementById("cache-new-NONE") as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: en.cacheAdd }));
    await waitFor(() => expect(document.querySelector('[data-cache-policy="agent.other"]')).not.toBeNull());
  });

  it("speaks French", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="fr" />, { wrapper: queryWrapper() });
    expect(await screen.findByRole("heading", { name: fr.cacheTitle, level: 2 })).toBeTruthy();
    expect(row("*").getByRole("radio", { name: fr.cacheSYSTEM_ONLY })).toBeTruthy();
  });
});
