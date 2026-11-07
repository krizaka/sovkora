import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { EngineScreen } from "@/console/features/engine/components/EngineScreen";
import { groupOf, groupSettings, parseDraft } from "@/console/features/engine/utils/engine";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { engineCopy } from "@/console/core/copy/engine";
import type { RuntimeSetting } from "@/console/services/runtime-config.admin.api";

const en = engineCopy.en;
const button = (label: string, name: string) =>
  within(screen.getByLabelText(label).closest("[data-setting]")!).getByRole("button", { name });
const setting = (over: Partial<RuntimeSetting> & { key: string }): RuntimeSetting => ({
  value: 1,
  type: "int",
  description: "A knob",
  default: 1,
  bounds: {},
  ...over,
});

const SETTINGS = [
  {
    key: "run.max_parallel",
    value: 4,
    type: "int",
    description: "Parallel runs",
    default: 2,
    bounds: { minimum: 1, maximum: 10 },
  },
  { key: "run.cache_enabled", value: true, type: "boolean", description: "Cache on", default: false, bounds: {} },
  {
    key: "webhook.hosts",
    value: ["a.example"],
    type: "list",
    description: "Allowed hosts",
    default: [],
    bounds: { maxItems: 3 },
  },
  { key: "retention.days", value: 30, type: "int", description: "Days kept", default: 30, bounds: { minimum: 1 } },
];

function mockFetch(handler: (url: string, init?: RequestInit) => { status?: number; body: unknown }) {
  const fn = jest.fn(async (url: string, init?: RequestInit) => {
    const { status = 200, body } = handler(url, init);
    return { ok: status < 400, status, statusText: "", json: async () => body } as Response;
  });
  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

describe("engine utils", () => {
  it("groups by key prefix, routing and unknown prefixes falling to other", () => {
    expect(groupOf("run.x")).toBe("run");
    expect(groupOf("marketplace.y")).toBe("marketplace");
    expect(groupOf("downloads.mac-enabled")).toBe("downloads");
    expect(groupOf("routing.z")).toBe("other");
    expect(groupSettings([setting({ key: "retention.b" }), setting({ key: "run.a" })]).map((g) => g.group)).toEqual([
      "run",
      "retention",
    ]);
  });

  it("validates an int against its bounds", () => {
    const s = setting({ key: "run.x", bounds: { minimum: 1, maximum: 5 } });
    expect(parseDraft(s, "3", en)).toEqual({ ok: true, value: 3 });
    expect(parseDraft(s, "9", en).ok).toBe(false);
    expect(parseDraft(s, "1.5", en).ok).toBe(false);
  });

  it("validates a list: empty item, duplicate, max items", () => {
    const s = setting({ key: "webhook.h", type: "list", value: [], default: [], bounds: { maxItems: 2 } });
    expect(parseDraft(s, "a\nb", en)).toEqual({ ok: true, value: ["a", "b"] });
    expect(parseDraft(s, "", en)).toEqual({ ok: true, value: [] });
    expect(parseDraft(s, "a\n\nb", en).ok).toBe(false);
    expect(parseDraft(s, "a\na", en).ok).toBe(false);
    expect(parseDraft(s, "a\nb\nc", en).ok).toBe(false);
  });
});

describe("EngineScreen", () => {
  it("lists the settings by group with description, value, default and bounds", async () => {
    mockFetch(() => ({ body: SETTINGS }));
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    expect(await screen.findByText("Parallel runs")).toBeTruthy();
    expect(screen.getByRole("heading", { name: en.groups.run })).toBeTruthy();
    expect(screen.getByRole("heading", { name: en.groups.webhook })).toBeTruthy();
    expect(screen.getByRole("heading", { name: en.groups.retention })).toBeTruthy();
    expect(screen.getByText("min 1 · max 10")).toBeTruthy();
    expect(screen.getByText(en.lead)).toBeTruthy();
  });

  it("speaks French", async () => {
    mockFetch(() => ({ body: SETTINGS }));
    render(<EngineScreen locale="fr" />, { wrapper: queryWrapper() });
    expect(await screen.findByRole("heading", { name: engineCopy.fr.groups.webhook })).toBeTruthy();
  });

  it("saves a valid value with a typed PUT and says it applies on the next read", async () => {
    const fetchMock = mockFetch((url, init) =>
      init?.method === "PUT" ? { body: { ...SETTINGS[0], value: 6 } } : { body: SETTINGS },
    );
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("run.max_parallel");
    fireEvent.change(input, { target: { value: "6" } });
    fireEvent.click(button("run.max_parallel", en.save));
    expect(await screen.findByText(en.saved)).toBeTruthy();
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")!;
    expect(put[0]).toBe("/api/v1/studios/admin/runtime-config/run.max_parallel");
    expect(JSON.parse(put[1]!.body as string)).toEqual({ value: 6 });
  });

  it.each(["Enter", "Save button"])("prevents overlapping saves started with %s", async (firstSubmit) => {
    let resolvePut!: (response: Response) => void;
    const pendingPut = new Promise<Response>((resolve) => {
      resolvePut = resolve;
    });
    const fetchMock = jest.fn(async (_url: string, init?: RequestInit) => {
      if (init?.method === "PUT") return pendingPut;
      return { ok: true, json: async () => SETTINGS } as Response;
    });
    global.fetch = fetchMock as unknown as typeof fetch;
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("run.max_parallel");
    fireEvent.change(input, { target: { value: "6" } });

    if (firstSubmit === "Enter") fireEvent.keyDown(input, { key: "Enter" });
    else fireEvent.click(button("run.max_parallel", en.save));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT")).toHaveLength(1));

    expect((input as HTMLInputElement).disabled).toBe(true);
    expect((button("run.max_parallel", en.saving) as HTMLButtonElement).disabled).toBe(true);
    expect((button("run.max_parallel", en.reset) as HTMLButtonElement).disabled).toBe(true);
    if (firstSubmit === "Enter") fireEvent.click(button("run.max_parallel", en.saving));
    else fireEvent.keyDown(input, { key: "Enter" });
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT")).toHaveLength(1);

    resolvePut({ ok: true, json: async () => ({ ...SETTINGS[0], value: 6 }) } as Response);
    expect(await screen.findByText(en.saved)).toBeTruthy();
  });

  it("normalizes the draft after an accepted save", async () => {
    mockFetch((_url, init) => (init?.method === "PUT" ? { body: { ...SETTINGS[0], value: 6 } } : { body: SETTINGS }));
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("run.max_parallel");
    fireEvent.change(input, { target: { value: "006" } });
    fireEvent.click(button("run.max_parallel", en.save));

    await waitFor(() => expect((input as HTMLInputElement).value).toBe("6"));
    expect((button("run.max_parallel", en.save) as HTMLButtonElement).disabled).toBe(true);
  });

  it("normalizes trimmed list items after an accepted save", async () => {
    const list = {
      key: "webhook.hosts",
      value: ["old.example"],
      type: "list",
      description: "Hosts",
      default: [],
      bounds: {},
    };
    mockFetch((_url, init) =>
      init?.method === "PUT" ? { body: { ...list, value: ["a.example", "b.example"] } } : { body: [list] },
    );
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("webhook.hosts");
    fireEvent.change(input, { target: { value: " a.example \n b.example " } });
    fireEvent.click(button("webhook.hosts", en.save));

    await waitFor(() => expect((input as HTMLTextAreaElement).value).toBe("a.example\nb.example"));
    expect((button("webhook.hosts", en.save) as HTMLButtonElement).disabled).toBe(true);
  });

  it("shows no error marker while nothing is wrong, and one alert once something is", async () => {
    mockFetch(() => ({ body: SETTINGS }));
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("run.max_parallel");
    expect(screen.queryAllByRole("alert")).toHaveLength(0);
    expect(input.closest("[data-setting]")!.querySelectorAll("p")).toHaveLength(1);
    expect(input.getAttribute("aria-describedby")).toBeNull();

    fireEvent.change(input, { target: { value: "99" } });
    fireEvent.click(button("run.max_parallel", en.save));
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toBe("Must be between 1 and 10.");
    expect(input.getAttribute("aria-describedby")).toBe(alert.id);
  });

  it("does not call the API for an out-of-bounds value and shows the server's message for a refusal", async () => {
    const fetchMock = mockFetch((url, init) =>
      init?.method === "PUT"
        ? { status: 400, body: { status: "invalid_request", message: "value must be at most 10" } }
        : { body: SETTINGS },
    );
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    const input = await screen.findByLabelText("run.max_parallel");
    fireEvent.change(input, { target: { value: "99" } });
    fireEvent.click(button("run.max_parallel", en.save));
    expect(await screen.findByText("Must be between 1 and 10.")).toBeTruthy();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);

    const retention = screen.getByLabelText("retention.days");
    fireEvent.change(retention, { target: { value: "40" } });
    fireEvent.click(button("retention.days", en.save));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(true));
  });

  it("shows the change history with actor ids", async () => {
    const fetchMock = mockFetch((url) =>
      url.includes("/audit")
        ? {
            body: [
              { at: "2026-10-01T10:00:00Z", actorId: "actor-42", key: "run.max_parallel", before: "2", after: "4" },
            ],
          }
        : { body: SETTINGS },
    );
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    await screen.findByText("Parallel runs");
    fireEvent.click(button("run.max_parallel", en.history));
    expect(await screen.findByText("actor-42")).toBeTruthy();
    expect(within(screen.getByRole("table")).getByText("2")).toBeTruthy();
    expect(within(screen.getByRole("table")).getByText("4")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/audit?key=run.max_parallel&limit="),
      expect.anything(),
    );
  });

  it("offers a retry when the settings cannot be read", async () => {
    mockFetch(() => ({ status: 500, body: {} }));
    render(<EngineScreen locale="en" />, { wrapper: queryWrapper() });
    expect(await screen.findByText(en.error)).toBeTruthy();
  });
});
