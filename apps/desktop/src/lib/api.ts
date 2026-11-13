import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

export type Settings = {
  mode: "local" | "remote";
  remoteUrl: string;
  workspaceDir: string | null;
  watched: string[];
  locale: "en" | "fr";
  /** What the files panel lists, previews and sends; a limit left out is the kit's default (megabytes, kilobytes). */
  limits: FileLimits;
};
export type FileLimits = {
  maxEntries?: number | null;
  maxTextPreviewKb?: number | null;
  maxImagePreviewMb?: number | null;
  maxPdfPreviewMb?: number | null;
  maxSendMb?: number | null;
};
export type Probe = { web: boolean; edge: boolean; state: "running" | "partial" | "stopped" };
export type Snapshot = {
  settings: Settings;
  roots: string[];
  sidecar: boolean;
  autostart: boolean;
  probe: Probe;
  appVersion: string;
};
export type Entry = {
  name: string;
  path: string;
  isDir: boolean;
  size: number;
  modified: number;
  kind: string;
};
export type Listing = { entries: Entry[]; truncated: boolean };
export type Preview = {
  name: string;
  size: number;
  mime: string;
  kind: string;
  text: string | null;
  data: string | null;
};
export type CliCall = { command: "version" | "status" | "start" | "stop" | "doctor" | "logs"; mode?: "dev" | "full"; service?: string; tail?: number };
export type CliResult = { code: number | null; ok: boolean; timedOut: boolean; lines: string[] };
export type FileAdded = { root: string; entry: Entry };
export type Dropped = { files: Entry[]; folders: string[] };
export type ServiceStatus = { name: string; port: number; status: "running" | "stopped" | "unknown"; pid: number | null; category: string };

// ── Automations (ADR-079) ──
export type RunStatus = "running" | "awaiting-approval" | "succeeded" | "failed" | "cancelled" | "halted" | "skipped-budget";
export type StepKind = "tool" | "model" | "approval" | "note";
export type StepStatus = "ok" | "error" | "skipped" | "dry-run" | "waiting";
export type Decision = "once" | "always" | "deny";
export type Policy = { maxFilesPerRun: number; maxTokensPerRun: number; maxRunsPerDay: number; maxCharsPerDocument: number };
export type ModelTarget = { baseUrl: string; model: string };
export type Usage = { model: string; promptTokens: number; completionTokens: number; durationMs: number; credits: number };
export type Automation = {
  id: string;
  name: string;
  recipe: "summarise-new-pdfs";
  schedule: { time: string; days: number[]; catchUp: "once" | "skip" };
  folder: string;
  model: ModelTarget;
  policy: Policy;
  dryRun: boolean;
  enabled: boolean;
  createdAt: string;
  lastSlot: string | null;
};
export type RunSummary = {
  id: string;
  jobId: string;
  automationId: string;
  automationName: string;
  trigger: "schedule" | "manual";
  status: RunStatus;
  dryRun: boolean;
  startedAt: string;
  finishedAt: string | null;
  written: number;
  steps: number;
  usage: Usage;
};
export type AutomationView = Automation & { nextRun: string | null; lastRun: RunSummary | null };
export type Step = { seq: number; kind: StepKind; name: string; detail: string; status: StepStatus; at: string; durationMs: number };
export type Run = Omit<RunSummary, "written" | "steps"> & { featureKey: string; steps: Step[]; written: string[]; error: string | null };
export type Approval = {
  id: string;
  runId: string;
  automationId: string;
  automationName: string;
  server: string;
  tool: string;
  scope: string;
  writes: { path: string; preview: string }[];
  createdAt: string;
  expiresAt: string;
  state: "pending" | "approved" | "denied" | "expired";
};
export type Grant = { id: string; automationId: string; server: string; tool: string; scope: string; createdAt: string };
export type Defaults = { version: number; policy: Policy; model: ModelTarget; time: string; dryRun: boolean };
export type Overview = {
  automations: AutomationView[];
  runs: RunSummary[];
  approvals: Approval[];
  grants: Grant[];
  halted: boolean;
  defaults: Defaults;
  running: string[];
};
export type NewAutomation = { name: string; folder: string; time: string; days: number[]; model: string; baseUrl: string; dryRun: boolean };
export type AuditEntry = { seq: number; at: string; event: string; data: Record<string, unknown>; prev: string; hash: string };
export type AuditView = { entries: AuditEntry[]; verification: { ok: boolean; entries: number; brokenAt: number | null } };

export const automationsApi = {
  overview: () => invoke<Overview>("automations_overview"),
  create: (input: NewAutomation) => invoke<Automation>("automation_create", { input }),
  setEnabled: (id: string, enabled: boolean) => invoke<void>("automation_set_enabled", { id, enabled }),
  setDryRun: (id: string, dryRun: boolean) => invoke<void>("automation_set_dry_run", { id, dryRun }),
  remove: (id: string) => invoke<void>("automation_delete", { id }),
  runNow: (id: string) => invoke<string>("automation_run_now", { id }),
  run: (id: string) => invoke<Run>("automation_run", { id }),
  decide: (id: string, decision: Decision) => invoke<RunSummary>("approval_decide", { id, decision }),
  revokeGrant: (id: string) => invoke<void>("grant_revoke", { id }),
  setHalted: (halted: boolean) => invoke<void>("kill_switch_set", { halted }),
  audit: (limit = 200) => invoke<AuditView>("audit_log", { limit }),
  models: (baseUrl: string) => invoke<string[]>("models_list", { baseUrl }),
};

/** The last segment of a path ("Inbox" for /Users/eric/Documents/Inbox). */
export function baseName(path: string): string {
  const parts = path.split("/").filter(Boolean);
  return parts[parts.length - 1] ?? path;
}

/** A local date-time stamp (`2026-10-17T08:00:00`) shown as "Fri 08:00" in the catalogue's date locale. */
export function slotLabel(stamp: string | null, dateLocale: string): string | null {
  if (!stamp) return null;
  const d = new Date(stamp);
  if (Number.isNaN(d.getTime())) return stamp;
  return d.toLocaleString(dateLocale, { weekday: "short", hour: "2-digit", minute: "2-digit" });
}

export type CodeRepoInfo = { root: string; branch: string; status: string[]; visible: number; hidden: number };
export type CodeHunk = { id: number; oldStart: number; oldLines: string[]; newLines: string[]; execSurface: boolean; addsSecret: boolean };
export type CodeFileProposal = { path: string; baseHash: string | null; hunks: CodeHunk[] };
export type CodeDecision = { file: CodeFileProposal; approved: number[] };
export type CodeApplied = { path: string; written: boolean; hunksApplied: number };
export type CodeCheck = { name: string; argv: string[]; cwd: string; timeoutSecs: number; network: boolean; env: string[] };
export type CodeCheckGrant = { id: string; repo: string; check: CodeCheck; argvHash: string; createdAt: string; revokedAt: string | null };
export type CodeChecksOverview = { suggested: CodeCheck[]; confirmed: CodeCheckGrant[]; needsApproval: boolean };
export type CodeCheckRun = { name: string; exitCode: number | null; durationMs: number; timedOut: boolean; argvHash: string; output: string; truncated: boolean };
export type GithubDeviceCode = { user_code: string; verification_uri: string; expires_in: number; interval: number };
export type GithubStatus = { connected: boolean; pending: GithubDeviceCode | null; failure: string | null };
export const githubApi = {
  status: () => invoke<GithubStatus>("github_status"),
  signIn: () => invoke<GithubDeviceCode>("github_sign_in"),
  signOut: () => invoke<void>("github_sign_out"),
};

export const codeApi = {
  open: (path: string) => invoke<CodeRepoInfo>("code_open", { path }),
  propose: (path: string, newText: string) => invoke<CodeFileProposal>("code_propose", { path, newText }),
  apply: (runId: string, decisions: CodeDecision[]) => invoke<CodeApplied[]>("code_apply", { runId, decisions }),
  undo: (runId: string, paths: string[]) => invoke<string[]>("code_undo", { runId, paths }),
  checks: () => invoke<CodeChecksOverview>("code_checks_overview"),
  confirmCheck: (check: CodeCheck) => invoke<CodeCheckGrant>("code_check_confirm", { check }),
  revokeCheck: (id: string) => invoke<boolean>("code_check_revoke", { id }),
  runCheck: (name: string, approved: boolean) => invoke<CodeCheckRun>("code_check_run", { name, approved }),
  openInEditor: (editor: "vs-code" | "xcode", path: string, line: number) => invoke<void>("code_open_in_editor", { editor, path, line }),
};

export const api = {
  snapshot: () => invoke<Snapshot>("get_snapshot"),
  probe: () => invoke<Probe>("probe_stack"),
  saveSettings: (settings: Settings) => invoke<Settings>("save_settings", { settings }),
  cli: (call: CliCall) => invoke<CliResult>("cli_run", { call }),
  pickFolder: () => invoke<string | null>("pick_folder"),
  grantDropped: (path: string) => invoke<string>("grant_dropped", { path }),
  revokeFolder: (path: string) => invoke<string[]>("revoke_folder", { path }),
  revokeAll: () => invoke<void>("revoke_all"),
  listDir: (path: string) => invoke<Listing>("list_dir", { path }),
  preview: (path: string) => invoke<Preview>("preview_file", { path }),
  reveal: (path: string) => invoke<void>("reveal_in_finder", { path }),
  watch: (path: string, enabled: boolean) => invoke<string[]>("watch_set", { path, enabled }),
  openWorkspace: () => invoke<string>("open_workspace"),
  sendFile: (path: string) => invoke<void>("workspace_send_file", { path }),
  setAutostart: (enabled: boolean) => invoke<boolean>("autostart_set", { enabled }),
};

export const on = <T,>(event: string, cb: (payload: T) => void) => listen<T>(event, (e) => cb(e.payload));

export function errorMessage(e: unknown): string {
  if (typeof e === "string") return e;
  if (e instanceof Error) return e.message;
  return JSON.stringify(e);
}

/** Parses the JSON array printed by `orazaka status --json`, ignoring any log noise around it. */
export function parseServices(lines: string[]): ServiceStatus[] {
  const text = lines.join("\n");
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start < 0 || end < start) return [];
  try {
    const parsed = JSON.parse(text.slice(start, end + 1));
    return Array.isArray(parsed) ? (parsed as ServiceStatus[]) : [];
  } catch {
    return [];
  }
}

export function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

export function parentOf(path: string): string {
  const i = path.lastIndexOf("/");
  return i <= 0 ? "/" : path.slice(0, i);
}

export function isInside(root: string, path: string): boolean {
  return path === root || path.startsWith(root.endsWith("/") ? root : `${root}/`);
}

/** The stack's state from the services `orazaka status` reported: all running, none, or some. */
export function stackState(services: ServiceStatus[], fallback: Probe["state"]): Probe["state"] {
  if (services.length === 0) return fallback;
  const running = services.filter((s) => s.status === "running").length;
  if (running === 0) return "stopped";
  return running === services.length ? "running" : "partial";
}
