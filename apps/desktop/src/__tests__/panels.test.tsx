import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createQueryClient, QueryClientProvider } from "@krizaka/query";
import { ThemeProvider } from "@krizaka/ui/theme";
import type { Snapshot } from "../lib/api";
import { snapshotKeys } from "../lib/queries";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/event", () => ({ listen: vi.fn(async () => () => undefined) }));

import { I18nProvider } from "../lib/i18n";
import { FilesPanel } from "../components/FilesPanel";
import { StackPanel } from "../components/StackPanel";

const base: Snapshot = {
  settings: { mode: "remote", remoteUrl: "https://www.orazaka.com", workspaceDir: "/w", watched: [], locale: "en", limits: {} },
  roots: [],
  sidecar: true,
  autostart: false,
  probe: { web: false, edge: false, state: "stopped" },
  appVersion: "0.1.0",
};

const fileEntry = (name: string, kind = "txt") => ({ name, path: `/Users/a/Clients/${name}`, isDir: false, size: 10, modified: 0, kind });

let client = createQueryClient({ maxRetries: 0 });
const wrap = (ui: React.ReactNode, locale: "en" | "fr" = "en") => (
  <QueryClientProvider client={client}>
    <ThemeProvider defaultMode="dark"><I18nProvider locale={locale}>{ui}</I18nProvider></ThemeProvider>
  </QueryClientProvider>
);

beforeEach(() => {
  client = createQueryClient({ maxRetries: 0 });
  invoke.mockReset();
  invoke.mockImplementation(async (cmd: string, args?: { call?: { command: string } }) => {
    if (cmd === "cli_run") {
      const c = args?.call?.command;
      if (c === "version") return { ok: true, code: 0, timedOut: false, lines: ["1.1.0"] };
      if (c === "status") return { ok: true, code: 0, timedOut: false, lines: ['[{"name":"Edge","port":8088,"status":"running","pid":null,"category":"app"}]'] };
      return { ok: true, code: 0, timedOut: false, lines: [] };
    }
    return undefined;
  });
});

describe("StackPanel", () => {
  it("shows the CLI version and the services read from `status --json`", async () => {
    render(wrap(<StackPanel snap={base} goSettings={() => undefined} />));
    expect(await screen.findByText("Edge")).toBeInTheDocument();
    expect(await screen.findByText(/CLI 1\.1\.0/)).toBeInTheDocument();
    expect(invoke).toHaveBeenCalledWith("cli_run", { call: { command: "status" } });
  });

  it("shows the header state from the services, not from the probe", async () => {
    render(wrap(<StackPanel snap={base} goSettings={() => undefined} />));
    // the mocked status reports one running service while the probe says stopped
    expect(await screen.findByText("Running")).toBeInTheDocument();
  });

  it("only ever asks for allow-listed commands", async () => {
    render(wrap(<StackPanel snap={base} goSettings={() => undefined} />));
    await screen.findByText("Edge");
    fireEvent.click(screen.getByRole("button", { name: "Stop" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("cli_run", { call: { command: "stop" } }));
    const commands = invoke.mock.calls.filter((c) => c[0] === "cli_run").map((c) => c[1].call.command);
    expect(new Set(commands)).toEqual(new Set(["version", "status", "stop"]));
  });

  it("explains the missing workspace and disables start", async () => {
    render(wrap(<StackPanel snap={{ ...base, settings: { ...base.settings, workspaceDir: null } }} goSettings={() => undefined} />));
    expect(await screen.findByText(/Choose the Sovkora workspace folder/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start" })).toBeDisabled();
  });

  it("is translated", async () => {
    render(wrap(<StackPanel snap={base} goSettings={() => undefined} />, "fr"));
    expect(await screen.findByText("Pile locale")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Arrêter" })).toBeInTheDocument();
  });
});

describe("FilesPanel", () => {
  it("says plainly that no file is readable when nothing is shared", () => {
    render(wrap(<FilesPanel snap={base} />));
    expect(screen.getByText(/Sovkora cannot read any file on this Mac/)).toBeInTheDocument();
    expect(screen.getByText("0 folder(s) shared")).toBeInTheDocument();
  });

  it("lists the shared folders with a revoke button that calls the backend", async () => {
    const invalidate = vi.spyOn(client, "invalidateQueries");
    render(wrap(<FilesPanel snap={{ ...base, roots: ["/Users/a/Clients"] }} />));
    expect(screen.getByText("/Users/a/Clients")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("revoke_folder", { path: "/Users/a/Clients" }));
    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: snapshotKeys.all }));
  });

  it("opens a folder through the scoped listing command", async () => {
    invoke.mockImplementation(async (cmd: string) =>
      cmd === "list_dir" ? { truncated: false, entries: [{ name: "contract.pdf", path: "/Users/a/Clients/contract.pdf", isDir: false, size: 2048, modified: 0, kind: "pdf" }] } : undefined,
    );
    render(wrap(<FilesPanel snap={{ ...base, roots: ["/Users/a/Clients"] }} />));
    fireEvent.click(screen.getByText("/Users/a/Clients"));
    expect(await screen.findByText("contract.pdf")).toBeInTheDocument();
    expect(invoke).toHaveBeenCalledWith("list_dir", { path: "/Users/a/Clients" });
  });

  it("shows the preview of the file clicked last, whatever the order the answers come back in (race)", async () => {
    let answerA: (v: unknown) => void = () => undefined;
    invoke.mockImplementation(async (cmd: string, args?: { path?: string }) => {
      if (cmd === "list_dir") return { truncated: false, entries: [fileEntry("a.txt"), fileEntry("b.txt")] };
      if (cmd === "preview_file" && args?.path?.endsWith("a.txt")) return new Promise((resolve) => { answerA = resolve; });
      if (cmd === "preview_file") return { name: "b.txt", size: 10, mime: "text/plain", kind: "txt", text: "content of B", data: null };
      return undefined;
    });
    render(wrap(<FilesPanel snap={{ ...base, roots: ["/Users/a/Clients"] }} />));
    fireEvent.click(screen.getByText("/Users/a/Clients"));
    fireEvent.click(await screen.findByText("a.txt"));
    fireEvent.click(screen.getByText("b.txt"));
    expect(await screen.findByText("content of B")).toBeInTheDocument();
    answerA({ name: "a.txt", size: 10, mime: "text/plain", kind: "txt", text: "content of A", data: null });
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.getByText("content of B")).toBeInTheDocument();
    expect(screen.queryByText("content of A")).not.toBeInTheDocument();
  });

  it("says « too large » for a PDF the kit did not embed (its limit, not a size written in the page), « no preview » for a binary", async () => {
    invoke.mockImplementation(async (cmd: string, args?: { path?: string }) => {
      if (cmd === "list_dir") return { truncated: false, entries: [fileEntry("big.pdf", "pdf"), fileEntry("tool.bin", "file")] };
      if (cmd === "preview_file" && args?.path?.endsWith("big.pdf")) return { name: "big.pdf", size: 99_000_000, mime: "application/pdf", kind: "pdf", text: null, data: null };
      if (cmd === "preview_file") return { name: "tool.bin", size: 99_000_000, mime: "application/octet-stream", kind: "file", text: null, data: null };
      return undefined;
    });
    render(wrap(<FilesPanel snap={{ ...base, roots: ["/Users/a/Clients"] }} />));
    fireEvent.click(screen.getByText("/Users/a/Clients"));
    fireEvent.click(await screen.findByText("big.pdf"));
    expect(await screen.findByText("Too large to preview here.")).toBeInTheDocument();
    fireEvent.click(screen.getByText("tool.bin"));
    expect(await screen.findByText("No preview for this kind of file.")).toBeInTheDocument();
  });

  it("falls back to no folder when the shared folder is revoked (derived, no effect)", async () => {
    invoke.mockImplementation(async (cmd: string) => (cmd === "list_dir" ? { truncated: false, entries: [] } : undefined));
    const view = render(wrap(<FilesPanel snap={{ ...base, roots: ["/Users/a/Clients"] }} />));
    fireEvent.click(screen.getByText("/Users/a/Clients"));
    expect(await screen.findByText("This folder is empty.")).toBeInTheDocument();
    view.rerender(wrap(<FilesPanel snap={{ ...base, roots: [] }} />));
    await waitFor(() => expect(screen.queryByText("This folder is empty.")).not.toBeInTheDocument());
  });
});
