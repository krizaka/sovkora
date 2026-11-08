import "@testing-library/jest-dom";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { formatDate } from "@krizaka/intl/date";
import { fill } from "@/core/context/fill";
import { CONSOLE_INTL } from "@/console/core/providers/ConsoleLocale";
import { OFFICIAL_URL, QUOTE, proposal } from "@/__tests__/helpers/law-fixtures";
import {
  MATRIX,
  answer,
  callsTo,
  installLawApi,
  paintedTheme,
  renderLaw,
  stubMatchMedia,
} from "@/__tests__/helpers/law-render";

// ADR-089 §8 (orazaka-web-client#275): the review queue — what a reviewer reads, and what each decision sends.
const ID = proposal().id;

beforeAll(stubMatchMedia);
beforeEach(() => {
  localStorage.clear();
  installLawApi();
});

async function openProposal(t: ReturnType<typeof renderLaw>) {
  fireEvent.click(await screen.findByRole("button", { name: fill(t.table.expandRow, { name: "QC-VISIT-001" }) }));
  return within(await screen.findByRole("article", { name: "QC-VISIT-001" }));
}

describe.each(MATRIX)("Law → Proposals (%s, %s theme)", (locale, theme) => {
  it("paints in its theme and speaks its language", async () => {
    const t = renderLaw(locale, theme);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(t.title);
    expect(screen.getByRole("tab", { name: t.tabs.proposals })).toHaveAttribute("aria-selected", "true");
    expect(await screen.findByText(t.status.PROPOSED, { selector: "span" })).toBeInTheDocument();
    expect(paintedTheme()).toBe(theme);
  });

  it("opens a proposal on the article quote beside its official URL, the rule, its fixtures and its vet report", async () => {
    const t = renderLaw(locale, theme);
    const detail = await openProposal(t);
    const law = within(detail.getByRole("region", { name: t.detail.law }));
    expect(law.getByText(QUOTE)).toHaveAttribute("lang", "fr");
    expect(
      law.getByRole("link", { name: fill(t.detail.officialText, { citation: "C.c.Q., art. 1931" }) }),
    ).toHaveAttribute("href", OFFICIAL_URL);
    expect(
      within(detail.getByRole("region", { name: t.detail.rule })).getByText("The notice gives the tenant 24 hours"),
    ).toBeInTheDocument();
    const fixtures = within(detail.getByRole("region", { name: t.detail.fixtures }));
    expect(
      within(fixtures.getByRole("region", { name: t.detail.mustPass })).getByText(
        "Visite prévue avec un préavis de 24 heures.",
      ),
    ).toBeInTheDocument();
    expect(
      within(fixtures.getByRole("region", { name: t.detail.mustFail })).getByText("Visite demain matin."),
    ).toBeInTheDocument();
    const asOf = formatDate("2026-10-20", { locale: CONSOLE_INTL[locale], style: "medium", timeZone: "UTC" });
    expect(
      within(detail.getByRole("region", { name: t.detail.vet })).getByText(
        fill(t.detail.vetPassed, { version: "1", date: asOf }),
      ),
    ).toBeInTheDocument();
  });

  it("shows a vet refusal with its findings in words", async () => {
    answer({
      "GET /proposals": () => ({
        body: [
          proposal({
            status: "REJECTED_AUTO",
            vetReport: {
              vetVersion: "1",
              asOf: "2026-10-20",
              passed: false,
              findings: [{ reason: "QUOTE_NOT_IN_ARTICLE", detail: "art. 1931" }],
            },
          }),
        ],
      }),
    });
    const t = renderLaw(locale, theme);
    const detail = await openProposal(t);
    expect(detail.getByText(t.vetReasons.QUOTE_NOT_IN_ARTICLE)).toBeInTheDocument();
    expect(detail.queryByRole("button", { name: t.actions.approve })).not.toBeInTheDocument();
  });

  it("approves with the optional note: POST /proposals/{id}/approve {note}", async () => {
    answer({ [`POST /proposals/${ID}/approve`]: () => ({ body: proposal({ status: "APPROVED" }) }) });
    const t = renderLaw(locale, theme);
    const detail = await openProposal(t);
    fireEvent.change(detail.getByLabelText(t.actions.approveNote), {
      target: { value: "Eric: the quote matches art. 1931" },
    });
    fireEvent.click(detail.getByRole("button", { name: t.actions.approve }));
    await waitFor(() => expect(callsTo("POST", `/proposals/${ID}/approve`)).toHaveLength(1));
    expect(callsTo("POST", `/proposals/${ID}/approve`)[0].body).toEqual({ note: "Eric: the quote matches art. 1931" });
    expect(await screen.findByText(fill(t.actions.approved, { ruleId: "QC-VISIT-001" }))).toBeInTheDocument();
  });

  it("refuses a rejection without a reason, then sends the reason as typed", async () => {
    answer({ [`POST /proposals/${ID}/reject`]: () => ({ body: proposal({ status: "REJECTED" }) }) });
    const t = renderLaw(locale, theme);
    const detail = await openProposal(t);
    fireEvent.change(detail.getByLabelText(t.actions.rejectReason), { target: { value: "   " } });
    fireEvent.click(detail.getByRole("button", { name: t.actions.reject }));
    expect(await screen.findByText(t.actions.reasonRequired)).toBeInTheDocument();
    expect(callsTo("POST", `/proposals/${ID}/reject`)).toHaveLength(0);
    fireEvent.change(detail.getByLabelText(t.actions.rejectReason), {
      target: { value: "Eric: the article is about repairs" },
    });
    fireEvent.click(detail.getByRole("button", { name: t.actions.reject }));
    await waitFor(() => expect(callsTo("POST", `/proposals/${ID}/reject`)).toHaveLength(1));
    expect(callsTo("POST", `/proposals/${ID}/reject`)[0].body).toEqual({
      reason: "Eric: the article is about repairs",
    });
  });

  it("edits the rule: PUT /proposals/{id} with the whole rule, then says how the vet judged the new revision", async () => {
    answer({
      [`PUT /proposals/${ID}`]: (_url, body) => ({
        body: proposal({
          proposalRevision: 2,
          status: "REJECTED_AUTO",
          rule: body as never,
          vetReport: {
            vetVersion: "1",
            asOf: "2026-10-20",
            passed: false,
            findings: [{ reason: "FIXTURES_INDISTINCT" }],
          },
        }),
      }),
    });
    const t = renderLaw(locale, theme);
    const detail = await openProposal(t);
    fireEvent.click(detail.getByRole("button", { name: t.actions.edit }));
    const dialog = within(await screen.findByRole("dialog"));
    fireEvent.change(dialog.getByLabelText(t.editor.title), { target: { value: "A visit needs 24 hours' notice" } });
    fireEvent.click(dialog.getByRole("button", { name: t.actions.save }));
    await waitFor(() => expect(callsTo("PUT", `/proposals/${ID}`)).toHaveLength(1));
    expect(callsTo("PUT", `/proposals/${ID}`)[0].body).toEqual({
      ...proposal().rule,
      title: "A visit needs 24 hours' notice",
    });
    expect(
      await screen.findByText(fill(t.actions.revisedFailed, { revision: 2, ruleId: "QC-VISIT-001" })),
    ).toBeInTheDocument();
  });

  it("filters by status in the URL and asks the service for that status", async () => {
    const t = renderLaw(locale, theme, "?status=APPROVED");
    await waitFor(() =>
      expect(callsTo("GET", "/proposals").some((c) => c.query.get("status") === "APPROVED")).toBe(true),
    );
    expect(screen.getByRole("radio", { name: t.status.APPROVED })).toHaveAttribute("aria-checked", "true");
  });

  it("still lists the queue when a vet-refused row is stored as the model wrote it (null title, unknown severity, no URL)", async () => {
    const raw = proposal({
      id: "77777777-7777-4777-8777-777777777777",
      ruleId: "QC-VISIT-RAW",
      status: "REJECTED_AUTO",
      vetReport: { vetVersion: "1", asOf: "2026-10-20", passed: false, findings: [{ reason: "SCHEMA_INVALID" }] },
    });
    const rule = {
      ...raw.rule,
      ruleId: null,
      title: null,
      severity: "CRITICAL",
      method: null,
      check: { type: "MAYBE_PATTERN", pattern: "x" },
      citation: null,
      law: [{ quote: "un préavis" }],
    } as Record<string, unknown>;
    delete rule.sourceUrl;
    answer({ "GET /proposals": () => ({ body: [{ ...raw, rule }, proposal()] }) });
    const t = renderLaw(locale, theme);
    fireEvent.click(await screen.findByRole("button", { name: fill(t.table.expandRow, { name: "QC-VISIT-RAW" }) }));
    const detail = within(await screen.findByRole("article", { name: "QC-VISIT-RAW" }));
    expect(detail.getByText("un préavis")).toBeInTheDocument();
    expect(detail.getByText(t.vetReasons.SCHEMA_INVALID)).toBeInTheDocument();
    expect(detail.getByText("MAYBE_PATTERN", { exact: false })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "CRITICAL" })).toBeInTheDocument();
    expect(detail.getByText(t.detail.noSource)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: fill(t.table.expandRow, { name: "QC-VISIT-001" }) })).toBeInTheDocument();
  });

  it("says a non-administrator cannot use the screen (403)", async () => {
    answer({ "GET /proposals": () => ({ status: 403, body: {} }) });
    const t = renderLaw(locale, theme);
    expect(await screen.findByText(t.refusals.forbidden)).toBeInTheDocument();
  });
});
