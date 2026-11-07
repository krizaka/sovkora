import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { FeatureToolsScreen } from "@/console/features/engine/components/FeatureToolsScreen";
import { featureToolsCopy } from "@/console/core/copy/featureTools";
import { queryWrapper } from "@/core/testing/queryWrapper";

const en = featureToolsCopy.en;
const fr = featureToolsCopy.fr;
const RANGE = { min: 1, max: 32 };

let bounds = [
  { featureKey: "*", maxToolRounds: 8, isDefault: true },
  { featureKey: "agent.code.issue-to-pr", maxToolRounds: 3, isDefault: false },
];
const ROUTES = [
  {
    featureKey: "agent.code.issue-to-pr",
    models: ["qwen2.5-coder:7b", "other-model"],
  },
];

/** A stub of the admin endpoints that behaves like the server: it stores, removes and refuses outside the range. */
function mockServer() {
  const calls: { url: string; method: string; body?: string }[] = [];
  global.fetch = jest.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push({ url, method, body: init?.body as string | undefined });
    const reply = (status: number, body: unknown) =>
      ({
        ok: status < 400,
        status,
        statusText: "",
        json: async () => body,
      }) as Response;
    if (url.endsWith("/feature-routes")) {
      return reply(200, ROUTES);
    }
    if (url.endsWith("/feature-structured-bounds")) {
      return reply(200, { min: 1, max: 5, bounds: [] });
    }
    if (url.endsWith("/feature-tool-bounds")) {
      return reply(200, { ...RANGE, bounds });
    }
    const key = decodeURIComponent(url.split("/feature-tool-bounds/")[1]);
    if (method === "PUT") {
      const n = (JSON.parse(init!.body as string) as { maxToolRounds: number }).maxToolRounds;
      if (n < 1 || n > 32) {
        return reply(400, {
          code: "invalid-request",
          detail: "maxToolRounds must be 1 to 32",
        });
      }
      bounds = [
        ...bounds.filter((b) => b.featureKey !== key),
        { featureKey: key, maxToolRounds: n, isDefault: key === "*" },
      ].sort((a, b) => a.featureKey.localeCompare(b.featureKey));
      return reply(200, {
        featureKey: key,
        maxToolRounds: n,
        isDefault: key === "*",
      });
    }
    bounds = bounds.filter((b) => b.featureKey !== key);
    return reply(204, null);
  }) as unknown as typeof fetch;
  return calls;
}

const row = (key: string) => within(document.querySelector(`[data-bound="${key}"]`) as HTMLElement);

beforeEach(() => {
  bounds = [
    { featureKey: "*", maxToolRounds: 8, isDefault: true },
    {
      featureKey: "agent.code.issue-to-pr",
      maxToolRounds: 3,
      isDefault: false,
    },
  ];
});

describe("FeatureToolsScreen", () => {
  it("lists the default row, the feature's own bound and the routes with the best model first", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    expect(await screen.findByText(en.defaultRow)).toBeTruthy();
    expect((row("agent.code.issue-to-pr").getByLabelText(en.rounds) as HTMLInputElement).value).toBe("3");
    expect((row("*").getByLabelText(en.rounds) as HTMLInputElement).value).toBe("8");
    expect(row("*").queryByRole("button", { name: en.remove })).toBeNull();
    const route = document.querySelector('[data-route="agent.code.issue-to-pr"]') as HTMLElement;
    expect(
      within(route)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual(["qwen2.5-coder:7b", "other-model"]);
  });

  it("saves a new bound with a PUT and shows the saved line", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    const input = (await screen.findAllByLabelText(en.rounds))[1] as HTMLInputElement;
    fireEvent.change(input, { target: { value: "5" } });
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    await waitFor(() =>
      expect(
        calls.some(
          (c) =>
            c.method === "PUT" &&
            c.url.endsWith("/feature-tool-bounds/agent.code.issue-to-pr") &&
            c.body === '{"maxToolRounds":5}',
        ),
      ).toBe(true),
    );
    expect(await screen.findByText(en.saved)).toBeTruthy();
  });

  it("refuses a value outside the range before any request is sent", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    const input = (await screen.findAllByLabelText(en.rounds))[1] as HTMLInputElement;
    fireEvent.change(input, { target: { value: "99" } });
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    expect(await screen.findByText("Enter a whole number from 1 to 32.")).toBeTruthy();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);
  });

  it("shows the server's refusal when it answers 400", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    // 32 passes the screen's check; the stub's server then refuses it by changing its rule.
    const base = global.fetch;
    global.fetch = jest.fn(async (url: string, init?: RequestInit) =>
      init?.method === "PUT"
        ? ({
            ok: false,
            status: 400,
            statusText: "",
            json: async () => ({ detail: "maxToolRounds must be 1 to 32" }),
          } as Response)
        : base(url, init),
    ) as unknown as typeof fetch;
    const input = (await screen.findAllByLabelText(en.rounds))[1] as HTMLInputElement;
    fireEvent.change(input, { target: { value: "32" } });
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    expect(await screen.findByText("maxToolRounds must be 1 to 32")).toBeTruthy();
  });

  it("removes a feature's own bound with a DELETE and the row goes away", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.defaultRow);
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.remove }));
    await waitFor(() => expect(document.querySelector('[data-bound="agent.code.issue-to-pr"]')).toBeNull());
    expect(
      calls.some((c) => c.method === "DELETE" && c.url.endsWith("/feature-tool-bounds/agent.code.issue-to-pr")),
    ).toBe(true);
  });

  it("adds the bound of a feature that has none", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    fireEvent.change(await screen.findByLabelText(en.featureKey), {
      target: { value: "agent.other" },
    });
    const rounds = screen.getAllByLabelText(en.rounds).at(-1) as HTMLInputElement;
    fireEvent.change(rounds, { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: en.add }));
    await waitFor(() => expect(document.querySelector('[data-bound="agent.other"]')).not.toBeNull());
    expect(calls.some((c) => c.method === "PUT" && c.url.endsWith("/feature-tool-bounds/agent.other"))).toBe(true);
  });

  it("speaks French", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="fr" />, { wrapper: queryWrapper() });
    expect(await screen.findByRole("heading", { name: fr.title, level: 1 })).toBeTruthy();
    expect(await screen.findByText(fr.defaultRow)).toBeTruthy();
  });

  it("says so, with a retry, when the bounds cannot be read", async () => {
    global.fetch = jest.fn(
      async () =>
        ({
          ok: false,
          status: 503,
          statusText: "",
          json: async () => ({}),
        }) as Response,
    ) as unknown as typeof fetch;
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    expect(await screen.findByText(en.error)).toBeTruthy();
    expect(screen.getByRole("button", { name: en.retry })).toBeTruthy();
  });
});
