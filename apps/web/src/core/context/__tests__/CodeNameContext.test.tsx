import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { CodeNameProvider, useCodeName } from "@/core/context/CodeNameContext";
import { translations } from "@/core/context/translations";

let locale: "en" | "fr" = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ locale, t: jest.requireActual("@/core/context/translations").translations[locale] }),
}));

function Probe() {
  return <p data-testid="line">{useCodeName() ?? "none"}</p>;
}

describe("the dev code name line (« code name: orazaka »)", () => {
  it("is worded in the visitor's language on a dev deployment", () => {
    locale = "en";
    render(
      <CodeNameProvider dev>
        <Probe />
      </CodeNameProvider>,
    );
    expect(screen.getByTestId("line")).toHaveTextContent("code name: orazaka");
    locale = "fr";
    render(
      <CodeNameProvider dev>
        <Probe />
      </CodeNameProvider>,
    );
    expect(screen.getAllByTestId("line")[1]).toHaveTextContent("nom de code : orazaka");
  });

  it("is absent in production, and without a provider", () => {
    render(
      <CodeNameProvider dev={false}>
        <Probe />
      </CodeNameProvider>,
    );
    expect(screen.getByTestId("line")).toHaveTextContent("none");
  });

  it("keeps the code name out of the catalogues (they carry the {code} placeholder)", () => {
    expect(translations.en.landing.stage.codeName).toBe("code name: {code}");
    expect(translations.fr.landing.stage.codeName).toBe("nom de code : {code}");
  });
});
