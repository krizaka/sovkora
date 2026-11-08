import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { FeatureToolsScreen } from "@/console/features/engine/components/FeatureToolsScreen";
import { featureToolsCopy } from "@/console/core/copy/featureTools";
import { queryWrapper } from "@/core/testing/queryWrapper";

const en = featureToolsCopy.en;
const fr = featureToolsCopy.fr;

type Row = { featureKey: string; maxAttempts: number; isDefault: boolean };
let rows: Row[] = [];

/** A stub of the admin endpoints that behaves like the server: it stores, removes and refuses outside 1 to 5. */
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
      return reply(200, []);
    }
    if (url.endsWith("/feature-tool-bounds")) {
      return reply(200, { min: 1, max: 32, bounds: [] });
    }
    if (url.endsWith("/feature-structured-bounds")) {
      return reply(200, { min: 1, max: 5, bounds: rows });
    }
    const key = decodeURIComponent(url.split("/feature-structured-bounds/")[1]);
    if (method === "PUT") {
      const n = (JSON.parse(init!.body as string) as { maxAttempts: number }).maxAttempts;
      if (n < 1 || n > 5) {
        return reply(400, {
          code: "invalid-request",
          detail: "maxAttempts must be 1 to 5",
        });
      }
      rows = [
        ...rows.filter((r) => r.featureKey !== key),
        { featureKey: key, maxAttempts: n, isDefault: key === "*" },
      ].sort((a, b) => a.featureKey.localeCompare(b.featureKey));
      return reply(200, {
        featureKey: key,
        maxAttempts: n,
        isDefault: key === "*",
      });
    }
    rows = rows.filter((r) => r.featureKey !== key);
    return reply(204, null);
  }) as unknown as typeof fetch;
  return calls;
}

const row = (key: string) => within(document.querySelector(`[data-structured-bound="${key}"]`) as HTMLElement);

beforeEach(() => {
  rows = [
    { featureKey: "*", maxAttempts: 2, isDefault: true },
    { featureKey: "agent.code.issue-to-pr", maxAttempts: 4, isDefault: false },
  ];
});

describe("FeatureToolsScreen: the structured-output bound (#172)", () => {
  it("lists the default row and the feature's own bound in their own section", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    expect(
      await screen.findByRole("heading", {
        name: en.structuredTitle,
        level: 2,
      }),
    ).toBeTruthy();
    expect(await screen.findByText(en.structuredDefaultRow)).toBeTruthy();
    expect((row("agent.code.issue-to-pr").getByLabelText(en.answers) as HTMLInputElement).value).toBe("4");
    expect((row("*").getByLabelText(en.answers) as HTMLInputElement).value).toBe("2");
    expect(row("*").queryByRole("button", { name: en.remove })).toBeNull();
  });

  it("saves a new number of answers and says it applies on the next call", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.structuredDefaultRow);
    const input = row("agent.code.issue-to-pr").getByLabelText(en.answers) as HTMLInputElement;
    fireEvent.change(input, { target: { value: "3" } });
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    expect(await screen.findByText(en.savedAnswers)).toBeTruthy();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.url.endsWith("/feature-structured-bounds/agent.code.issue-to-pr")).toBe(true);
    expect(JSON.parse(put!.body!)).toEqual({ maxAttempts: 3 });
  });

  it("refuses a number outside the range before any request is sent", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.structuredDefaultRow);
    const input = row("agent.code.issue-to-pr").getByLabelText(en.answers) as HTMLInputElement;
    fireEvent.change(input, { target: { value: "9" } });
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.save }));
    expect(await screen.findByText("Enter a whole number of answers from 1 to 5.")).toBeTruthy();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);
  });

  it("returns a feature to the default, and adds the bound of a feature that has none", async () => {
    const calls = mockServer();
    render(<FeatureToolsScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText(en.structuredDefaultRow);
    fireEvent.click(row("agent.code.issue-to-pr").getByRole("button", { name: en.remove }));
    await waitFor(() => expect(document.querySelector('[data-structured-bound="agent.code.issue-to-pr"]')).toBeNull());
    expect(
      calls.some((c) => c.method === "DELETE" && c.url.endsWith("/feature-structured-bounds/agent.code.issue-to-pr")),
    ).toBe(true);

    fireEvent.change(await screen.findByLabelText(en.structuredFeatureKey), {
      target: { value: "agent.other" },
    });
    fireEvent.change(screen.getAllByLabelText(en.answers).at(-1) as HTMLInputElement, { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: en.addStructured }));
    await waitFor(() => expect(document.querySelector('[data-structured-bound="agent.other"]')).not.toBeNull());
  });

  it("speaks French", async () => {
    mockServer();
    render(<FeatureToolsScreen locale="fr" />, { wrapper: queryWrapper() });
    expect(
      await screen.findByRole("heading", {
        name: fr.structuredTitle,
        level: 2,
      }),
    ).toBeTruthy();
    expect(await screen.findByText(fr.structuredDefaultRow)).toBeTruthy();
  });
});
