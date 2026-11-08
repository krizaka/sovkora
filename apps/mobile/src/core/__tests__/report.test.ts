import { asReport, asTextResult } from "../report";

const report = JSON.stringify({
  source: "bail.pdf",
  summary: { byVerdict: { PASS: 1, FAIL: 1 } },
  findings: [
    { ruleId: "R1", title: "Durée", verdict: "PASS", explanation: "ok" },
    {
      ruleId: "R2",
      title: "Dépôt",
      verdict: "FAIL",
      explanation: "trop élevé",
      evidenceSpan: { quote: "3 mois" },
      citation: "C.c.Q. 1904",
    },
  ],
});

describe("asReport", () => {
  it("reads a rules report, failures first, with quote and citation", () => {
    const r = asReport(report);
    expect(r?.findings.map((f) => f.id)).toEqual(["R2", "R1"]);
    expect(r?.findings[0]).toMatchObject({ tone: "danger", quote: "3 mois", citation: "C.c.Q. 1904" });
    expect(r?.counts).toEqual([
      ["PASS", 1],
      ["FAIL", 1],
    ]);
    expect(r?.source).toBe("bail.pdf");
  });

  it("reads the typed findings of orazaka#124 (status, observation)", () => {
    const r = asReport(
      JSON.stringify({
        findings: [{ id: "PDF-META-002", status: "inconsistent", title: "Dates", observation: "créé après modifié" }],
      }),
    );
    expect(r?.findings[0]).toMatchObject({ tone: "danger", explanation: "créé après modifié" });
  });

  it("is null for text, other JSON and findings without a verdict", () => {
    expect(asReport("**Nom :** x")).toBeNull();
    expect(asReport('{"a":1}')).toBeNull();
    expect(asReport('{"findings":[{"title":"x"}]}')).toBeNull();
  });
});

describe("asTextResult", () => {
  it("keeps plain text as is", () => {
    expect(asTextResult("**Nom :** x")).toEqual({ content: "**Nom :** x", facts: [] });
  });
  it("splits a step's metadata from its words", () => {
    const r = asTextResult(JSON.stringify({ content: "Bonjour", provider: "ollama", durationMs: 2500, junk: 1 }));
    expect(r.content).toBe("Bonjour");
    expect(r.facts).toEqual([
      ["provider", "ollama"],
      ["durationMs", "2.5 s"],
    ]);
  });
});
