import "@testing-library/jest-dom";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { fill } from "@/core/context/fill";
import type { LawCopy } from "@/console/core/copy/law";
import {
  APPROVED_ID,
  MATRIX,
  answer,
  callsTo,
  installLawApi,
  renderLaw,
  stubMatchMedia,
  typeDate,
} from "@/__tests__/helpers/law-render";

// ADR-089 §5 and §8 (orazaka-web-client#275): a kind's golden cases and the promotion of its approved rules — the packs
// pull request's link, and every refusal of the service in words.
const PROMOTE = "/kinds/qc-visit-notice/promote";
const PR_URL = "https://github.com/krizaka/orazaka-packs/pull/131";
const CONFLICTS = [
  "proposal-not-approved",
  "already-in-review",
  "kind-mismatch",
  "duplicate-rule",
  "golden-minimum-not-met",
  "law-version-mismatch",
  "quote-too-long",
] as const;

beforeAll(stubMatchMedia);
beforeEach(() => {
  localStorage.clear();
  installLawApi();
});

async function promoteForm(t: LawCopy) {
  const region = within(await screen.findByRole("region", { name: fill(t.kind.promote, { kind: "qc-visit-notice" }) }));
  fireEvent.click(await region.findByRole("checkbox", { name: fill(t.kind.selectRow, { name: "QC-VISIT-001" }) }));
  typeDate(t, t.kind.effectiveFrom, "2026-11-01");
  return region;
}

describe.each(MATRIX)("Law → Proposals, promotion (%s, %s theme)", (locale, theme) => {
  it("shows the kind's golden cases against the minimum", async () => {
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    const golden = within(
      await screen.findByRole("region", { name: fill(t.kind.golden, { kind: "qc-visit-notice" }) }),
    );
    expect(golden.getByText(t.kind.met)).toBeInTheDocument();
    expect(
      golden.getByText(fill(t.kind.goldenSummary, { documents: 4, compliant: 1, defective: 2, blank: 1, required: 3 })),
    ).toBeInTheDocument();
    expect(golden.getByText("fr-blank")).toBeInTheDocument();
  });

  it("promotes the approved rules chosen with the date: POST /kinds/{kindId}/promote, then the pull request's link", async () => {
    answer({
      [`POST ${PROMOTE}`]: () => ({
        body: {
          prUrl: PR_URL,
          branch: "law/qc-visit-notice/2026-11-01",
          rulesetPath: "document-validation/rulesets/qc-visit-notice/2026-11-01.json",
          revision: 1,
          proposalIds: [APPROVED_ID],
        },
      }),
    });
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    const region = await promoteForm(t);
    fireEvent.change(region.getByLabelText(t.kind.revisionNote), {
      target: { value: "First promotion, reviewed by Eric" },
    });
    fireEvent.click(region.getByRole("button", { name: t.kind.submit }));
    await waitFor(() => expect(callsTo("POST", PROMOTE)).toHaveLength(1));
    expect(callsTo("POST", PROMOTE)[0].body).toEqual({
      proposalIds: [APPROVED_ID],
      effectiveFrom: "2026-11-01",
      revisionNote: "First promotion, reviewed by Eric",
    });
    expect(await region.findByRole("link", { name: t.kind.openPr })).toHaveAttribute("href", PR_URL);
    expect(
      region.getByText(
        fill(t.kind.opened, { revision: 1, path: "document-validation/rulesets/qc-visit-notice/2026-11-01.json" }),
      ),
    ).toBeInTheDocument();
  });

  it("sends nothing until an approved rule is chosen", async () => {
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    const region = within(
      await screen.findByRole("region", { name: fill(t.kind.promote, { kind: "qc-visit-notice" }) }),
    );
    await region.findByRole("checkbox", { name: fill(t.kind.selectRow, { name: "QC-VISIT-001" }) });
    typeDate(t, t.kind.effectiveFrom, "2026-11-01");
    fireEvent.click(region.getByRole("button", { name: t.kind.submit }));
    expect(await region.findByText(t.kind.nothingSelected)).toBeInTheDocument();
    expect(callsTo("POST", PROMOTE)).toHaveLength(0);
  });

  it("words every 409 of the promotion by its code", async () => {
    let code: string = CONFLICTS[0];
    answer({
      [`POST ${PROMOTE}`]: () => ({ status: 409, body: { type: "about:blank", title: "Conflict", status: 409, code } }),
    });
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    const region = await promoteForm(t);
    for (const next of CONFLICTS) {
      code = next;
      fireEvent.click(region.getByRole("button", { name: t.kind.submit }));
      expect(await region.findByText(t.refusals[next])).toBeInTheDocument();
    }
    expect(callsTo("POST", PROMOTE)).toHaveLength(CONFLICTS.length);
  });

  it("says promotion is not configured (503 law-promotion-off), for the golden cases and the promotion alike", async () => {
    answer({
      "GET /kinds/qc-visit-notice/golden": () => ({ status: 503, body: { code: "law-promotion-off" } }),
      [`POST ${PROMOTE}`]: () => ({ status: 503, body: { code: "law-promotion-off" } }),
    });
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    expect(await screen.findByText(t.refusals["law-promotion-off"])).toBeInTheDocument();
    const region = await promoteForm(t);
    fireEvent.click(region.getByRole("button", { name: t.kind.submit }));
    expect(await region.findByText(t.refusals["law-promotion-off"])).toBeInTheDocument();
  });

  it("refuses a date that is not one before anything is sent", async () => {
    const t = renderLaw(locale, theme, "?kind=qc-visit-notice");
    const region = within(
      await screen.findByRole("region", { name: fill(t.kind.promote, { kind: "qc-visit-notice" }) }),
    );
    fireEvent.click(await region.findByRole("checkbox", { name: fill(t.kind.selectRow, { name: "QC-VISIT-001" }) }));
    fireEvent.click(region.getByRole("button", { name: t.kind.submit }));
    await waitFor(() => expect(region.getAllByText(t.kind.invalidDate).length).toBeGreaterThan(0));
    expect(callsTo("POST", PROMOTE)).toHaveLength(0);
  });
});
