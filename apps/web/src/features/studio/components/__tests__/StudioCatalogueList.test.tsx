import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { StudioSummary } from "@krizaka/orazaka-shared";
import { buildRows, groupByTrade } from "@/features/studio/catalogue/catalogue";
import type { CataloguePack } from "@/features/studio/catalogue/catalogue.types";
import { StudioCatalogueList } from "@/features/studio/components/StudioCatalogueList";

let locale: "en" | "fr" = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations[locale], locale }),
}));

const studio = (key: string, over: Partial<StudioSummary>): StudioSummary => ({
  studioKey: key,
  label: key,
  tagline: null,
  profession: "general",
  iconKey: "studio",
  heroAssetId: null,
  pricing: "INCLUDED",
  kind: "TOOLKIT",
  latestVersion: "1.0.0",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
  ...over,
});
const packs: CataloguePack[] = [
  {
    packKey: "orazaka-media",
    label: "Media",
    access: "INCLUDED",
    owned: false,
    priceCents: 0,
    studioKeys: ["image-generation"],
  },
  {
    packKey: "data-analytics",
    label: "Data & analytics",
    access: "BUYABLE",
    owned: false,
    priceCents: 4900,
    studioKeys: ["creator-suggestions"],
  },
];
const catalogue = [
  studio("image-generation", { label: "Image generation", tagline: "A picture from a sentence." }),
  studio("creator-suggestions", {
    label: "Creator suggestions",
    profession: "creative",
    kind: "VERTICAL",
    pricing: "PAID",
    locked: true,
    lockedReason: "REQUIRES_PURCHASE",
    packKey: "data-analytics",
  }),
  studio("media-safety", {
    label: "Media safety",
    kind: "VERTICAL",
    pricing: "FREE",
    locked: true,
    lockedReason: "REQUIRES_PLAN",
  }),
  studio("document-authenticity", {
    label: "Document authenticity",
    profession: "legal",
    kind: "VERTICAL",
    pricing: "PAID",
    locked: true,
    lockedReason: "UNKNOWN",
  }),
];

function renderList(onGetPack = jest.fn()) {
  const label = (trade: string) => ({ general: "General", creative: "Creative", legal: "Legal" })[trade] ?? trade;
  const groups = groupByTrade(buildRows(catalogue, [], packs, label), label);
  render(<StudioCatalogueList groups={groups} onGetPack={onGetPack} plansHref="/packs" />);
  return onGetPack;
}

describe("StudioCatalogueList", () => {
  beforeEach(() => {
    locale = "en";
  });

  it("puts each Studio under its trade's heading, one list per trade", () => {
    renderList();
    expect(screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent)).toEqual([
      "Creative1",
      "Legal1",
      "General2",
    ]);
    expect(screen.getByRole("list", { name: "Studios in General" })).toBeInTheDocument();
  });

  it("offers Run on a ready Studio, says it comes with the plan, and links to the Studio", () => {
    renderList();
    const run = screen.getByRole("link", { name: "Run Image generation" });
    expect(run).toHaveAttribute("href", "/studios/image-generation");
    expect(screen.getByText(/Included in your plan · A picture from a sentence\./)).toBeInTheDocument();
  });

  it("offers the pack that opens a locked Studio, with its price, and opens it in place", () => {
    const onGetPack = renderList();
    expect(screen.getByText(/Data & analytics pack, CA\$49\.00/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Get the pack for Creator suggestions" }));
    expect(onGetPack).toHaveBeenCalledWith("data-analytics");
  });

  it("sends a Studio of another plan to the plans, and offers nothing when billing could not check", () => {
    renderList();
    expect(screen.getByRole("link", { name: "See the plans that include Media safety" })).toHaveAttribute(
      "href",
      "/packs",
    );
    const legal = screen.getByRole("list", { name: "Studios in Legal" });
    expect(within(legal).queryByRole("button")).not.toBeInTheDocument();
    expect(within(legal).getByText(/We could not check it right now/)).toBeInTheDocument();
  });

  it("shows each state in words", () => {
    renderList();
    for (const word of ["Ready", "Needs a pack", "Not on your plan", "Not checked"])
      expect(screen.getByText(word)).toBeInTheDocument();
  });

  it("speaks French when the reader does", () => {
    locale = "fr";
    renderList();
    expect(screen.getByRole("link", { name: "Lancer Image generation" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Obtenir le pack de Creator suggestions" })).toBeInTheDocument();
    expect(screen.getByText("Hors de votre offre")).toBeInTheDocument();
  });
});
