import "@testing-library/jest-dom";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { formatDate } from "@krizaka/intl/date";
import { fill } from "@/core/context/fill";
import { CONSOLE_INTL } from "@/console/core/providers/ConsoleLocale";
import { HASH } from "@/__tests__/helpers/law-fixtures";
import {
  MATRIX,
  answer,
  callsTo,
  installLawApi,
  renderLaw,
  stubMatchMedia,
  typeDate,
} from "@/__tests__/helpers/law-render";

// ADR-089 §8 (orazaka-web-client#275): the Corpus tab (instruments, imports, an article by point in time, the manual
// import) and the Demand tab (kind_demand, and the derivation an administrator starts).
beforeAll(stubMatchMedia);
beforeEach(() => {
  localStorage.clear();
  installLawApi();
});

describe.each(MATRIX)("Law → Corpus and Demand (%s, %s theme)", (locale, theme) => {
  it("Corpus lists the instruments and the imports with their hash and counts", async () => {
    const t = renderLaw(locale, theme, "?tab=corpus");
    expect(screen.getByRole("tab", { name: t.tabs.corpus })).toHaveAttribute("aria-selected", "true");
    const instruments = within(screen.getByRole("region", { name: t.corpus.instruments }));
    expect(await instruments.findByText("CCQ-1991")).toBeInTheDocument();
    expect(
      instruments.getByText(locale === "fr" ? "Code civil du Québec" : "Civil Code of Québec"),
    ).toBeInTheDocument();
    const imports = within(screen.getByRole("region", { name: t.corpus.imports }));
    expect(await imports.findByText(`${HASH.slice(0, 12)}…`)).toBeInTheDocument();
    expect(imports.getByText("3 · 1 · 3166")).toBeInTheDocument();
    expect(imports.getByText(t.corpus.modes.MANUAL)).toBeInTheDocument();
  });

  it("looks up the article in force on a date — metadata only", async () => {
    answer({
      "GET /articles": (url) => ({
        body: {
          jurisdiction: "QC",
          instrument: "CCQ-1991",
          ref: url.searchParams.get("ref"),
          language: "fr",
          asOf: url.searchParams.get("asOf"),
          inForce: {
            inForceFrom: "1994-01-01",
            inForceTo: null,
            contentHash: HASH,
            officialUrl: "https://www.legisquebec.gouv.qc.ca/fr/document/lc/CCQ-1991#se:1931",
            importId: "44444444-4444-4444-8444-444444444444",
            length: 312,
          },
          history: [
            {
              inForceFrom: "1994-01-01",
              inForceTo: null,
              contentHash: HASH,
              officialUrl: "https://www.legisquebec.gouv.qc.ca/fr/document/lc/CCQ-1991#se:1931",
              importId: "44444444-4444-4444-8444-444444444444",
              length: 312,
            },
          ],
        },
      }),
    });
    const t = renderLaw(locale, theme, "?tab=corpus");
    const lookup = within(screen.getByRole("region", { name: t.corpus.lookup }));
    fireEvent.change(lookup.getByLabelText(t.corpus.ref), { target: { value: "1931" } });
    typeDate(t, t.corpus.asOf, "2026-10-25");
    fireEvent.click(lookup.getByRole("button", { name: t.corpus.lookupSubmit }));
    await waitFor(() => expect(callsTo("GET", "/articles")).toHaveLength(1));
    expect(Object.fromEntries(callsTo("GET", "/articles")[0].query)).toEqual({
      jurisdiction: "QC",
      instrument: "CCQ-1991",
      ref: "1931",
      language: "fr",
      asOf: "2026-10-25",
    });
    const day = (iso: string) => formatDate(iso, { locale: CONSOLE_INTL[locale], style: "medium", timeZone: "UTC" });
    expect(
      await lookup.findByText(
        fill(t.corpus.inForce, {
          date: day("2026-10-25"),
          from: day("1994-01-01"),
          length: 312,
          hash: HASH.slice(0, 12),
        }),
      ),
    ).toBeInTheDocument();
    expect(lookup.getAllByRole("link", { name: t.corpus.official })[0]).toHaveAttribute(
      "href",
      "https://www.legisquebec.gouv.qc.ca/fr/document/lc/CCQ-1991#se:1931",
    );
  });

  it("imports a consolidated XML file by hand: POST /imports, multipart, with where it came from", async () => {
    answer({
      "POST /imports": () => ({
        body: {
          unchanged: 3166,
          stored: 4,
          requests: [],
          failures: [],
          imports: ["55555555-5555-4555-8555-555555555555"],
        },
      }),
    });
    const t = renderLaw(locale, theme, "?tab=corpus");
    const manual = within(screen.getByRole("region", { name: t.corpus.manual }));
    const file = new File(["<loi/>"], "ccq-1991.xml", { type: "application/xml" });
    const input = screen
      .getByRole("region", { name: t.corpus.manual })
      .querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.change(manual.getByLabelText(t.corpus.datasetUrl), {
      target: { value: "https://www.donneesquebec.ca/recherche/dataset/ccq.xml" },
    });
    fireEvent.click(manual.getByRole("button", { name: t.corpus.importSubmit }));
    await waitFor(() => expect(callsTo("POST", "/imports")).toHaveLength(1));
    const sent = callsTo("POST", "/imports")[0].body as FormData;
    expect(sent.get("file")).toBe(file);
    expect(sent.get("datasetUrl")).toBe("https://www.donneesquebec.ca/recherche/dataset/ccq.xml");
    expect([sent.get("jurisdiction"), sent.get("instrument"), sent.get("language")]).toEqual(["QC", "CCQ-1991", "fr"]);
    expect(sent.has("sha256")).toBe(false);
    expect(await manual.findByText(fill(t.corpus.imported, { stored: 4, unchanged: 3166 }))).toBeInTheDocument();
  });

  it("Demand counts the kinds asked for, an unknown one in words", async () => {
    const t = renderLaw(locale, theme, "?tab=demand");
    const demand = within(screen.getByRole("region", { name: t.demand.caption }));
    expect(await demand.findByText("qc-sublet-notice")).toBeInTheDocument();
    expect(demand.getByText(t.demand.unknown)).toBeInTheDocument();
    expect(demand.getByText("12")).toBeInTheDocument();
  });

  it("starts a derivation: POST /derivations {jurisdiction, kindId, asOf, language}, then its outcome", async () => {
    answer({
      "POST /derivations": () => ({
        body: {
          runId: "66666666-6666-4666-8666-666666666666",
          outcome: "DERIVED",
          proposals: 4,
          rejected: 1,
          creditsSpent: 800,
        },
      }),
    });
    const t = renderLaw(locale, theme, "?tab=demand");
    const derive = within(screen.getByRole("region", { name: t.demand.derive }));
    fireEvent.change(derive.getByLabelText(t.demand.kindId), { target: { value: "qc-sublet-notice" } });
    typeDate(t, t.demand.asOf, "2026-10-25");
    fireEvent.click(derive.getByRole("button", { name: t.demand.deriveSubmit }));
    await waitFor(() => expect(callsTo("POST", "/derivations")).toHaveLength(1));
    expect(callsTo("POST", "/derivations")[0].body).toEqual({
      jurisdiction: "QC",
      kindId: "qc-sublet-notice",
      asOf: "2026-10-25",
      language: "fr",
    });
    expect(
      await derive.findByText(
        fill(t.demand.derived, {
          runId: "66666666-6666-4666-8666-666666666666",
          outcome: t.demand.outcomes.DERIVED,
          proposals: 4,
          rejected: 1,
          credits: 800,
        }),
      ),
    ).toBeInTheDocument();
  });

  it("says derivation is not configured (503 law-derivation-off)", async () => {
    answer({ "POST /derivations": () => ({ status: 503, body: { code: "law-derivation-off" } }) });
    const t = renderLaw(locale, theme, "?tab=demand");
    const derive = within(screen.getByRole("region", { name: t.demand.derive }));
    fireEvent.change(derive.getByLabelText(t.demand.kindId), { target: { value: "qc-sublet-notice" } });
    typeDate(t, t.demand.asOf, "2026-10-25");
    fireEvent.click(derive.getByRole("button", { name: t.demand.deriveSubmit }));
    expect(await derive.findByText(t.refusals["law-derivation-off"])).toBeInTheDocument();
  });
});
