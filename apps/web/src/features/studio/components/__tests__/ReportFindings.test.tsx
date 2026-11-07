import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { asReport, ReportFindings } from "@/features/studio/components/ReportFindings";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const report = {
  source: "Loi n° 89-462",
  summary: { byVerdict: { PASS: 1, FAIL: 1 } },
  findings: [
    {
      ruleId: "R-1",
      title: "Parties identified",
      verdict: "PASS",
      explanation: "The landlord is named.",
      citation: "art. 3",
    },
    {
      ruleId: "R-5",
      title: "Deposit capped",
      verdict: "FAIL",
      explanation: "Two months.",
      evidenceSpan: { quote: "deposit of 1 960 €" },
      citation: "art. 22",
    },
  ],
};

describe("ReportFindings", () => {
  it("reads a rules report and nothing else", () => {
    expect(asReport(JSON.stringify(report))).not.toBeNull();
    expect(asReport("plain text")).toBeNull();
    expect(asReport(JSON.stringify({ findings: [{ ruleId: "x" }] }))).toBeNull();
  });

  it("puts the failures first, with their passage and citation", () => {
    render(<ReportFindings report={report} />);
    const items = screen.getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("Deposit capped");
    expect(items[0]).toHaveTextContent("« deposit of 1 960 € »");
    expect(items[0]).toHaveTextContent("art. 22");
    expect(screen.getByText("Points to fix: 1")).toBeInTheDocument();
  });
});

// The real report of a Compliance Check run on dev (a Quebec lease, contract 2.0): `citation` is an object. Rendering it
// as a child used to crash the whole run page (React error #31).
// eslint-disable-next-line @typescript-eslint/no-require-imports -- a JSON fixture
const lease = require("./fixtures/lease-report-2.0.json");

describe("ReportFindings — contract 2.0 (the lease run)", () => {
  it("renders the report instead of crashing on the object citation", () => {
    const parsed = asReport(JSON.stringify(lease));
    expect(parsed).not.toBeNull();
    render(<ReportFindings report={parsed!} />);
    expect(screen.getByText("Blocking points to fix: 3")).toBeInTheDocument();
    expect(screen.getByText("Residential lease")).toBeInTheDocument();
    expect(screen.getByText("2026-11-04")).toBeInTheDocument();
    // The reference of the citation, not the object.
    expect(screen.getAllByText(/C\.c\.Q\., art\. 1895/).length).toBeGreaterThan(0);
  });

  it("puts the blocking failures first, with their severity", () => {
    render(<ReportFindings report={asReport(JSON.stringify(lease))!} />);
    const first = screen.getAllByRole("listitem")[0];
    expect(first).toHaveTextContent("Blocking");
    expect(first).toHaveTextContent("QC-LEASE-");
  });

  it("folds the rules that do not apply and says the unpinned notice once", () => {
    render(<ReportFindings report={asReport(JSON.stringify(lease))!} />);
    expect(screen.getByText("Rules that do not apply (8)")).toBeInTheDocument();
    expect(screen.getAllByText(/Unpinned citation/)).toHaveLength(1);
    expect(screen.getByText(/still await a lawyer's review/)).toBeInTheDocument();
  });
});
