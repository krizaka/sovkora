import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { BlueprintEditor } from "@/console/features/studio/components/BlueprintEditor";
import { BlueprintVersionTable } from "@/console/features/studio/components/BlueprintVersionTable";
import { studiosCopy } from "@/console/core/copy/studios";
import { queryWrapper } from "@/core/testing/queryWrapper";

const VERSION = {
  version: "1.0.0",
  status: "DRAFT" as const,
  estimatedCredits: 10,
  changelog: "first",
  publishedAt: null,
};

describe("StudioBuilderScreen i18n — builder section (krizaka-internal#2093)", () => {
  it("has the same builder keys in both languages", () => {
    expect(Object.keys(studiosCopy.en.builder)).toEqual(Object.keys(studiosCopy.fr.builder));
  });

  it("none of the French builder labels appear in English", () => {
    expect(studiosCopy.en.builder.saveDraft).toBe("Save draft");
    expect(studiosCopy.en.builder.publish).toBe("Publish");
    expect(studiosCopy.en.builder.definition).toBe("Definition (steps + outputs)");
    expect(studiosCopy.en.builder.loading).not.toMatch(/Chargement/);
    expect(studiosCopy.en.builder.empty).not.toMatch(/Aucun/);
  });

  it.each(["en", "fr"] as const)("BlueprintEditor renders its labels in %s", (locale) => {
    render(<BlueprintEditor studioKey="x" featureKeys={[]} onSaved={() => {}} locale={locale} />, {
      wrapper: queryWrapper(),
    });
    expect(screen.getByText(studiosCopy[locale].builder.saveDraft)).toBeInTheDocument();
    expect(screen.getByText(studiosCopy[locale].builder.definition)).toBeInTheDocument();
  });

  it.each(["en", "fr"] as const)("BlueprintVersionTable renders its labels in %s", (locale) => {
    render(<BlueprintVersionTable studioKey="x" versions={[VERSION]} onChanged={() => {}} locale={locale} />, {
      wrapper: queryWrapper(),
    });
    expect(screen.getByText(studiosCopy[locale].builder.versionCol)).toBeInTheDocument();
    expect(screen.getByText(studiosCopy[locale].builder.publish)).toBeInTheDocument();
  });
});
