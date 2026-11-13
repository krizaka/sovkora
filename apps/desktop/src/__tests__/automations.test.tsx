import { render, screen, waitFor, fireEvent, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createQueryClient, QueryClientProvider } from "@krizaka/query";
import { ThemeProvider } from "@krizaka/ui/theme";
import type { Overview, Run, Snapshot } from "../lib/api";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async () => () => undefined) }));

import { I18nProvider } from "../lib/i18n";
import { AutomationsPanel } from "../components/automations/AutomationsPanel";

const snap: Snapshot = {
  settings: { mode: "local", remoteUrl: "https://www.orazaka.com", workspaceDir: null, watched: [], locale: "en", limits: {} },
  roots: ["/Users/eric/Documents/Inbox"],
  sidecar: true,
  autostart: false,
  probe: { web: false, edge: false, state: "stopped" },
  appVersion: "0.2.0",
};

const usage = { model: "qwen2.5:7b", promptTokens: 240, completionTokens: 60, durationMs: 4200, credits: 0 };
const defaults = { version: 1, policy: { maxFilesPerRun: 10, maxTokensPerRun: 60000, maxRunsPerDay: 3, maxCharsPerDocument: 24000 }, model: { baseUrl: "http://localhost:11434/v1", model: "qwen2.5:7b" }, time: "08:00", dryRun: false };
const automation = {
  id: "a1",
  name: "Morning PDF summaries",
  recipe: "summarise-new-pdfs" as const,
  schedule: { time: "08:00", days: [], catchUp: "once" as const },
  folder: "/Users/eric/Documents/Inbox",
  model: defaults.model,
  policy: defaults.policy,
  dryRun: false,
  enabled: true,
  createdAt: "2026-10-16T07:00:00",
  lastSlot: null,
  nextRun: "2026-10-17T08:00:00",
  lastRun: null,
};
const runSummary = { id: "r1", jobId: "job-1", automationId: "a1", automationName: "Morning PDF summaries", trigger: "schedule" as const, status: "awaiting-approval" as const, dryRun: false, startedAt: "2026-10-16T08:00:20", finishedAt: null, written: 0, steps: 6, usage };
const run: Run = {
  ...runSummary,
  featureKey: "automation.local.summarise-new-pdfs",
  written: [],
  error: null,
  steps: [
    { seq: 1, kind: "tool", name: "filesystem.list_directory", detail: "2 PDF(s) in the folder, 2 without a summary (at most 10 per run)", status: "ok", at: "2026-10-16T08:00:21", durationMs: 40 },
    { seq: 2, kind: "approval", name: "approval", detail: "write 2 note(s): waiting for your approval", status: "waiting", at: "2026-10-16T08:00:30", durationMs: 0 },
  ],
};
let overview: Overview;

const wrap = (ui: React.ReactNode) => (
  <QueryClientProvider client={createQueryClient({ maxRetries: 0 })}>
    <ThemeProvider defaultMode="dark"><I18nProvider locale="en">{ui}</I18nProvider></ThemeProvider>
  </QueryClientProvider>
);

beforeEach(() => {
  overview = {
    automations: [automation],
    runs: [runSummary],
    approvals: [
      {
        id: "ap1", runId: "r1", automationId: "a1", automationName: "Morning PDF summaries", server: "filesystem", tool: "write_file",
        scope: "/Users/eric/Documents/Inbox",
        writes: [{ path: "/Users/eric/Documents/Inbox/lease-2026.summary.md", preview: "# lease-2026 — summary\n- Rent: 1,250 $" }],
        createdAt: "2026-10-16T08:00:30", expiresAt: "2026-10-17T08:00:30", state: "pending",
      },
    ],
    grants: [],
    halted: false,
    defaults,
    running: [],
  };
  invoke.mockReset();
  invoke.mockImplementation(async (cmd: string) => {
    if (cmd === "automations_overview") return overview;
    if (cmd === "automation_run") return run;
    if (cmd === "audit_log") return { entries: [{ seq: 1, at: "2026-10-16T07:00:00", event: "automation.created", data: { automation: "a1" }, prev: "0", hash: "abcdef0123456789" }], verification: { ok: true, entries: 1, brokenAt: null } };
    if (cmd === "models_list") return ["qwen2.5:7b", "llama3:8b"];
    return undefined;
  });
});

describe("AutomationsPanel", () => {
  it("shows the approval with the exact note it would write, and nothing is decided without a click", async () => {
    render(wrap(<AutomationsPanel snap={snap} />));
    const approval = await screen.findByTestId("approval");
    expect(within(approval).getByText("lease-2026.summary.md")).toBeInTheDocument();
    expect(within(approval).getByText(/Rent: 1,250/)).toBeInTheDocument();
    expect(screen.getByText("1 waiting")).toBeInTheDocument();
    expect(invoke.mock.calls.some((c) => c[0] === "approval_decide")).toBe(false);
  });

  it.each([
    ["Approve once", "once"],
    ["Always for this folder", "always"],
    ["Deny", "deny"],
  ])("%s sends the decision %s", async (label, decision) => {
    render(wrap(<AutomationsPanel snap={snap} />));
    fireEvent.click(await screen.findByRole("button", { name: label }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("approval_decide", { id: "ap1", decision }));
  });

  it("the kill switch stops every agent, and says so", async () => {
    render(wrap(<AutomationsPanel snap={snap} />));
    fireEvent.click(await screen.findByRole("button", { name: "Stop all agents" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("kill_switch_set", { halted: true }));
    overview = { ...overview, halted: true };
    fireEvent.click(screen.getByRole("button", { name: "Stop all agents" }));
    expect(await screen.findByText("All agents are stopped on this Mac")).toBeInTheDocument();
  });

  it("opens a run's trace step by step", async () => {
    render(wrap(<AutomationsPanel snap={snap} />));
    fireEvent.click(await screen.findByRole("button", { name: "Step by step" }));
    expect(await screen.findByText(/filesystem\.list_directory · 2 PDF\(s\)/)).toBeInTheDocument();
    expect(screen.getByText(/automation\.local\.summarise-new-pdfs/)).toBeInTheDocument();
    expect(invoke).toHaveBeenCalledWith("automation_run", { id: "r1" });
  });

  it("creates the morning automation from the template on a shared folder", async () => {
    overview = { ...overview, automations: [], approvals: [], runs: [] };
    render(wrap(<AutomationsPanel snap={snap} />));
    fireEvent.click(await screen.findByRole("button", { name: "Summarise new PDFs every morning" }));
    await screen.findByTestId("new-automation");
    fireEvent.click(screen.getByRole("button", { name: "Create the automation" }));
    await waitFor(() =>
      expect(invoke).toHaveBeenCalledWith("automation_create", {
        input: { name: "Morning PDF summaries", folder: "/Users/eric/Documents/Inbox", time: "08:00", days: [], model: "qwen2.5:7b", baseUrl: "http://localhost:11434/v1", dryRun: false },
      }),
    );
  });

  it("run now and pause call the allow-listed commands with the automation's id", async () => {
    overview = { ...overview, approvals: [] };
    render(wrap(<AutomationsPanel snap={snap} />));
    const card = await screen.findByTestId("automation");
    fireEvent.click(within(card).getByRole("button", { name: "Run now" }));
    fireEvent.click(within(card).getByRole("button", { name: "Pause" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("automation_run_now", { id: "a1" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("automation_set_enabled", { id: "a1", enabled: false }));
  });

  it("shows that the audit chain verifies", async () => {
    render(wrap(<AutomationsPanel snap={snap} />));
    expect(await screen.findByText("Chain verified · 1 entries")).toBeInTheDocument();
  });
});
