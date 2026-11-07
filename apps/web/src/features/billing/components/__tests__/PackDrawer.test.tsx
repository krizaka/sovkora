import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import type { PackSummary } from "@krizaka/orazaka-shared";
import { PackDrawer, type PackDrawerProps } from "@/features/billing/components/PackDrawer";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const pack = (over: Partial<PackSummary> = {}): PackSummary => ({
  packKey: "data-analytics",
  categoryKey: "data-analytics",
  label: "Data & analytics",
  tagline: "Your numbers, read for you.",
  iconKey: "chart",
  heroAssetId: null,
  regulatoryClass: "STANDARD",
  kind: "VERTICAL",
  access: "BUYABLE",
  studioKeys: ["creator-suggestions", "video-understanding"],
  priceCents: 4900,
  includedCredits: 2000,
  ...over,
});
const studios = new Map([
  ["creator-suggestions", { label: "Creator suggestions", iconKey: "chart" }],
  ["video-understanding", { label: "Video understanding", iconKey: "video" }],
]);

function open(props: Partial<PackDrawerProps> = {}) {
  const handlers = { onAdd: jest.fn(), onRemove: jest.fn(), onClose: jest.fn() };
  render(
    <PackDrawer
      pack={pack()}
      owned={false}
      pending={false}
      holdingsUnavailable={false}
      studios={studios}
      {...handlers}
      {...props}
    />,
  );
  return handlers;
}

describe("PackDrawer", () => {
  it("says what the pack costs and every Studio it adds, each a link", () => {
    open();
    expect(screen.getByRole("dialog", { name: "Data & analytics" })).toBeInTheDocument();
    expect(screen.getByText("CA$49.00")).toBeInTheDocument();
    expect(screen.getByText("2,000 credits included")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Creator suggestions" })).toHaveAttribute(
      "href",
      "/studios/creator-suggestions",
    );
    expect(screen.getByRole("link", { name: "Video understanding" })).toBeInTheDocument();
  });

  it("adds a pack in one action", () => {
    const { onAdd } = open();
    fireEvent.click(screen.getByRole("button", { name: "Add to my account" }));
    expect(onAdd).toHaveBeenCalledWith("data-analytics");
  });

  it("offers to remove a pack already held", () => {
    const { onRemove } = open({ owned: true });
    expect(screen.getByText("In your account")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove from my account" }));
    expect(onRemove).toHaveBeenCalledWith("data-analytics");
  });

  it("offers nothing to buy for a pack the plan includes, or one billing could not price", () => {
    open({ pack: pack({ access: "INCLUDED", kind: "TOOLKIT", priceCents: 0 }) });
    expect(screen.getByText("Included in your plan")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /my account/ })).not.toBeInTheDocument();
  });

  it("never quotes a price billing did not give", () => {
    open({ pack: pack({ access: "UNAVAILABLE", priceCents: null }) });
    expect(screen.getByText("Price not available right now")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /my account/ })).not.toBeInTheDocument();
  });

  it("turns adding off while billing cannot say what you hold", () => {
    open({ holdingsUnavailable: true });
    expect(screen.queryByRole("button", { name: "Add to my account" })).not.toBeInTheDocument();
  });

  it("stays closed without a pack", () => {
    open({ pack: null });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
