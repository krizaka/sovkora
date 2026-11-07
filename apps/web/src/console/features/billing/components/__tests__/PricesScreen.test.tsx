import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { PricesScreen } from "@/console/features/billing/components/PricesScreen";
import { pricesCopy } from "@/console/core/copy/prices";
import { queryWrapper } from "@/core/testing/queryWrapper";

const ENTRY = {
  versionId: "v1",
  key: "orazaka.business",
  unit: "year",
  amountCents: 290000,
  currency: "CAD",
  taxes: "plus-taxes",
  availability: "preparing",
  limits: {},
  effectiveFrom: "2026-10-01T00:00:00Z",
};

function mockFetch(handler: (url: string) => { status?: number; body: unknown }) {
  const fn = jest.fn(async (url: string) => {
    const { status = 200, body } = handler(url);
    return { ok: status < 400, status, statusText: "", json: async () => body, text: async () => "" } as Response;
  });
  global.fetch = fn as unknown as typeof fetch;
  return fn;
}

describe("PricesScreen", () => {
  it("reads the catalogue and its history from billing, and shows the entry from the service's cents", async () => {
    const fetchMock = mockFetch((url) => ({ body: url.endsWith("/history") ? [ENTRY] : [ENTRY] }));
    render(<PricesScreen locale="en" />, { wrapper: queryWrapper() });
    await waitFor(() => expect(document.body.textContent).toContain("business"));
    const urls = fetchMock.mock.calls.map((c) => c[0]);
    expect(urls).toContain("/api/v1/billing/prices/admin");
    expect(urls).toContain("/api/v1/billing/prices/admin/orazaka.business/history");
    expect(document.body.textContent).toContain("2,900.00");
    expect(document.body.textContent).toMatch(/2[\s,.]?900/);
  });

  it("says so, with a retry, when billing does not answer", async () => {
    mockFetch(() => ({ status: 503, body: {} }));
    render(<PricesScreen locale="fr" />, { wrapper: queryWrapper() });
    await waitFor(() => expect(document.body.textContent).toContain(pricesCopy.fr.error));
    expect(screen.getByRole("button", { name: pricesCopy.fr.retry })).toBeTruthy();
  });

  it("types no figure: the words hold no amount of money (prices are data)", () => {
    expect(JSON.stringify(pricesCopy)).not.toMatch(/[$€£]|\d{2,}\s?(CAD|USD|EUR)/);
  });
});
