import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { StudioAnalyticsScreen } from "@/console/features/studio/components/StudioAnalyticsScreen";
import { studiosCopy } from "@/console/core/copy/studios";
import { queryWrapper } from "@/core/testing/queryWrapper";

const ROW = {
  studioKey: "lease-check",
  label: "Lease check",
  installations: 4,
  runs: 10,
  failed: 2,
  successRate: 0.8,
  estimatedCredits: 3,
  installedButUnused: false,
};

function mockFetch(body: unknown) {
  global.fetch = jest.fn(async () => ({
    ok: true,
    status: 200,
    statusText: "",
    json: async () => body,
    text: async () => JSON.stringify(body),
  })) as unknown as typeof fetch;
}

describe("StudioAnalyticsScreen", () => {
  it.each(["en", "fr"] as const)("writes the table in %s", async (locale) => {
    mockFetch([ROW]);
    render(<StudioAnalyticsScreen locale={locale} />, { wrapper: queryWrapper() });
    await waitFor(() => expect(screen.getAllByText(studiosCopy[locale].analytics.runs).length).toBeGreaterThan(0));
    expect(document.body.textContent).toContain(`(2 ${studiosCopy[locale].analytics.failures})`);
  });

  it("has the same words in both languages, and none of the French ones in English", () => {
    expect(Object.keys(studiosCopy.en.analytics)).toEqual(Object.keys(studiosCopy.fr.analytics));
    expect(studiosCopy.en.analytics.runs).toBe("Runs");
    expect(studiosCopy.en.page.lead).not.toMatch(/Un Studio/);
  });
});
