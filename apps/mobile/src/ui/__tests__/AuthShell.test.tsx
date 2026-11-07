import React from "react";
import { render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import en from "../../i18n/en.json";
import fr from "../../i18n/fr.json";
import { LocaleProvider } from "../../i18n";
import { AuthShell } from "../AuthShell";

// orazaka#516: the sign-in says Sovkora is in development (like Yomizaka and Akinaza) and carries the duck, Sovkora's mascot.
describe("AuthShell", () => {
  it("says the product is in development, from the catalogue", async () => {
    await render(
      <LocaleProvider>
        <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
          <AuthShell title="Sign in" subtitle="to Sovkora">
            <></>
          </AuthShell>
        </ThemeProvider>
      </LocaleProvider>,
    );
    expect(screen.getByTestId("development-status")).toHaveTextContent(en.auth.development);
  });

  it("has the line in both languages, never « ready » wording", () => {
    expect(en.auth.development).toMatch(/^In development/);
    expect(fr.auth.development).toMatch(/^En développement/);
  });
});
