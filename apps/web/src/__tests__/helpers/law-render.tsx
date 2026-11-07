/**
 * Renders Law → Proposals (ADR-089 §8) the way the console does — the kit's theme, the console's language — over a fake
 * law API that records every call (method, path, query, body). Every test runs in both themes and both languages.
 */
import { fireEvent, render, screen, within } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { ThemeProvider } from "@krizaka/ui/theme";
import { lawCopy, type LawCopy } from "@/console/core/copy/law";
import { ConsoleLocaleProvider, type ConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import { LawScreen } from "@/console/features/law/components/LawScreen";
import { HASH, proposal } from "@/__tests__/helpers/law-fixtures";

export const MATRIX = [
  ["en", "dark"],
  ["en", "light"],
  ["fr", "dark"],
  ["fr", "light"],
] as const satisfies ReadonlyArray<readonly [ConsoleLocale, "dark" | "light"]>;

export interface Reply {
  status?: number;
  body?: unknown;
}
export type Route = (url: URL, body: unknown) => Reply;

export interface Call {
  method: string;
  path: string;
  query: URLSearchParams;
  body: unknown;
}

const BASE = "/api/v1/admin/law";
export const calls: Call[] = [];
let routes: Record<string, Route> = {};

export const APPROVED_ID = "33333333-3333-4333-8333-333333333333";

/** What the service answers by default: a queue of one proposal, an approved one for the kind, the corpus and the demand. */
export function defaultRoutes(): Record<string, Route> {
  return {
    "GET /proposals": (url) => ({
      body:
        url.searchParams.get("status") === "APPROVED"
          ? [proposal({ id: APPROVED_ID, status: "APPROVED" })]
          : [proposal()],
    }),
    "GET /kinds/qc-visit-notice/golden": () => ({
      body: {
        kindId: "qc-visit-notice",
        required: 3,
        documents: 4,
        compliant: 1,
        defective: 2,
        blank: 1,
        met: true,
        cases: [
          { name: "fr-compliant", form: "FILLED" },
          { name: "fr-blank", form: "BLANK" },
        ],
      },
    }),
    "GET /instruments": () => ({
      body: [
        {
          jurisdiction: "QC",
          code: "CCQ-1991",
          titleFr: "Code civil du Québec",
          titleEn: "Civil Code of Québec",
          sourceKey: "donnees-quebec",
          articles: 3170,
          versions: 3204,
        },
      ],
    }),
    "GET /imports": () => ({
      body: [
        {
          id: "44444444-4444-4444-8444-444444444444",
          sourceKey: "donnees-quebec",
          mode: "MANUAL",
          datasetUrl: "https://www.donneesquebec.ca/recherche/dataset/ccq.xml",
          datasetSha256: HASH,
          consolidatedTo: "2026-09-01",
          importedBy: "Eric",
          startedAt: "2026-10-20T10:00:00Z",
          finishedAt: "2026-10-20T10:02:00Z",
          articlesNew: 3,
          articlesChanged: 1,
          articlesUnchanged: 3166,
          status: "SUCCEEDED",
        },
      ],
    }),
    "GET /demand": () => ({
      body: [
        {
          jurisdiction: "QC",
          kindId: "qc-sublet-notice",
          count: 12,
          firstSeen: "2026-10-01T09:00:00Z",
          lastSeen: "2026-10-24T16:00:00Z",
        },
        {
          jurisdiction: "QC",
          kindId: "UNKNOWN",
          count: 3,
          firstSeen: "2026-10-02T09:00:00Z",
          lastSeen: "2026-10-23T16:00:00Z",
        },
      ],
    }),
  };
}

/** Replaces or adds routes of the fake service (`"POST /proposals/<id>/approve"`). */
export function answer(more: Record<string, Route>) {
  routes = { ...routes, ...more };
}

function readBody(body: BodyInit | null | undefined): unknown {
  if (body instanceof FormData) return body;
  return typeof body === "string" ? JSON.parse(body) : undefined;
}

export function installLawApi() {
  calls.length = 0;
  routes = defaultRoutes();
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "https://admin.orazaka.test");
    const method = init?.method ?? "GET";
    const path = url.pathname.replace(BASE, "");
    const body = readBody(init?.body);
    calls.push({ method, path, query: url.searchParams, body });
    const route = routes[`${method} ${path}`];
    const reply = route ? route(url, body) : { status: 404, body: { code: "proposal-not-found" } };
    const status = reply.status ?? 200;
    return { ok: status < 400, status, json: async () => reply.body } as Response;
  }) as typeof fetch;
}

export const callsTo = (method: string, path: string) => calls.filter((c) => c.method === method && c.path === path);

export function renderLaw(locale: ConsoleLocale, theme: "dark" | "light", search = ""): LawCopy {
  globalThis.history.replaceState(null, "", `/law${search}`);
  render(
    <ThemeProvider defaultMode={theme}>
      <ConsoleLocaleProvider locale={locale}>
        <LawScreen />
      </ConsoleLocaleProvider>
    </ThemeProvider>,
    { wrapper: queryWrapper() },
  );
  return lawCopy[locale];
}

/** Types an ISO date into the kit's day · month · year boxes of the date field named `legend`. */
export function typeDate(t: LawCopy, legend: string, iso: string) {
  const [year, month, day] = iso.split("-");
  const field = within(screen.getByRole("group", { name: legend }));
  fireEvent.change(field.getByLabelText(t.dateParts.year), { target: { value: year } });
  fireEvent.change(field.getByLabelText(t.dateParts.month), { target: { value: month } });
  fireEvent.change(field.getByLabelText(t.dateParts.day), { target: { value: day } });
}

export function stubMatchMedia() {
  Object.defineProperty(globalThis, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: query.includes("reduce"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      onchange: null,
      dispatchEvent: () => false,
    }),
  });
}

/** The theme the page is painted in: the kit puts `light` on `<html>` in light mode. */
export const paintedTheme = () => (document.documentElement.classList.contains("light") ? "light" : "dark");
