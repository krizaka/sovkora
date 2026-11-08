import React from "react";
import { render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AppNavigator } from "../navigation/AppNavigator";
import { AuthProvider } from "../core/AuthContext";
import { answerFetch } from "./test-api";
import login from "../__fixtures__/api/auth-login.local-demo.json";
import studios from "../__fixtures__/api/studios.local-demo.json";
import jobs from "../__fixtures__/api/jobs.local-demo.json";
import profile from "../__fixtures__/api/profile.local-demo.json";

// Dev preview `mobile.ux-review` (orazaka-mobile-client#37): the shape of the phone's navigation is a tested contract —
// about five tabs at the thumb, the web's words in the web's order (docs/ux/information-architecture.md § 3 of the
// Sovkora workspace, docs/UX_DECISIONS.md), Studios first because the app opens on what you can run.
const mockKeychain = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  getItemAsync: async (key: string) => mockKeychain.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => void mockKeychain.set(key, value),
  deleteItemAsync: async (key: string) => void mockKeychain.delete(key),
}));

// The words of the web rail (orazaka-web-client core/config/app-sections.ts), in its order: Home · Studios · Chat · Jobs ·
// Packs · Usage · Settings. The phone keeps the four that are about doing, and never renames one.
const WEB_ORDER = ["Home", "Studios", "Chat", "Jobs", "Packs", "Usage", "Settings"];

describe("the phone's tab bar", () => {
  beforeEach(() => {
    mockKeychain.clear();
    mockKeychain.set("orazaka.session", JSON.stringify(login));
    answerFetch({ "/api/v1/studios": studios, "/api/v1/jobs": jobs, "/api/v1/profile": profile });
  });

  it("holds four tabs, Studios first, in the web's words and order", async () => {
    await render(
      <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
        <AuthProvider>
          <AppNavigator />
        </AuthProvider>
      </ThemeProvider>,
    );
    await screen.findByText("Vitrine Artisan");
    const tabs = screen.getAllByRole("tab");
    expect(tabs.length).toBeLessThanOrEqual(5);
    expect(tabs).toHaveLength(4);
    const labels = ["Studios", "Chat", "Jobs", "Settings"];
    // Each tab is found by its word, and sits at its place in the bar: Studios first.
    labels.forEach((word, i) => expect(screen.getByRole("tab", { name: new RegExp(word) })).toBe(tabs[i]));
    // Same words, same relative order as the web rail.
    const positions = labels.map((w) => WEB_ORDER.indexOf(w));
    expect(positions.every((p) => p >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });
});
