import { renderHook, waitFor } from "@testing-library/react";
import type { PackCategory, PackSummary } from "@krizaka/orazaka-shared";
import { usePacks } from "@/features/billing/hooks/usePacks";
import { BillingApi } from "@/services/billing.api";
import { StudioApi } from "@/services/studio.api";

jest.mock("@/services/billing.api", () => ({
  BillingApi: { fetchMyPacks: jest.fn(), subscribeToPack: jest.fn(), removePack: jest.fn() },
}));
jest.mock("@/services/studio.api", () => ({ StudioApi: { fetchPacks: jest.fn(), fetchPackCategories: jest.fn() } }));

const pack: PackSummary = {
  packKey: "document-validation",
  categoryKey: "business",
  label: "Document Validation",
  tagline: null,
  iconKey: "studio",
  heroAssetId: null,
  regulatoryClass: "SENSITIVE",
  kind: "VERTICAL",
  access: "UNAVAILABLE",
  studioKeys: ["compliance-check"],
  priceCents: null,
  includedCredits: null,
};
const shelf: PackCategory = { categoryKey: "business", label: "Business" } as PackCategory;

describe("usePacks", () => {
  beforeEach(() => {
    jest.mocked(StudioApi.fetchPacks).mockResolvedValue([pack]);
    jest.mocked(StudioApi.fetchPackCategories).mockResolvedValue([shelf]);
  });

  it("keeps the catalogue on screen when billing does not answer (dev has no billing service)", async () => {
    jest.mocked(BillingApi.fetchMyPacks).mockRejectedValue(new Error("504"));
    const { result } = renderHook(() => usePacks("en", "could not load"));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.error).toBeNull();
    expect(result.current.packs).toEqual([pack]);
    expect(result.current.categories).toEqual([shelf]);
    expect(result.current.holdingsUnavailable).toBe(true);
    expect(result.current.ownedKeys.size).toBe(0);
  });

  it("still fails loudly when the catalogue itself cannot be read", async () => {
    jest.mocked(StudioApi.fetchPacks).mockRejectedValue(new Error("500"));
    jest.mocked(BillingApi.fetchMyPacks).mockResolvedValue([]);
    const { result } = renderHook(() => usePacks("en", "could not load"));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.error).toBe("could not load");
    expect(result.current.packs).toEqual([]);
  });

  it("reports holdings as available when billing answers", async () => {
    jest.mocked(BillingApi.fetchMyPacks).mockResolvedValue([{ packKey: "document-validation" } as never]);
    const { result } = renderHook(() => usePacks("en", "could not load"));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.holdingsUnavailable).toBe(false);
    expect(result.current.ownedKeys.has("document-validation")).toBe(true);
  });
});
