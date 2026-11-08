import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { TenantProvider, useTenant, accentMap } from "@/core/context/TenantContext";

jest.mock("@/core/hooks/useSettings", () => ({
  useSettings: () => ({
    settings: {
      themeAccent: "emerald",
      themeName: "TestTenant",
      themeTagline: "Test Tagline",
      themeLayout: "compact",
      tenantId: "test-1",
      theme: "dark",
    },
    isLoading: false,
  }),
}));

const mockSetAppearance = jest.fn();
jest.mock("@/core/hooks/useAppearance", () => ({
  ...jest.requireActual("@/core/hooks/useAppearance"),
  useAppearance: () => ({ appearance: "system", setAppearance: mockSetAppearance }),
}));

function Consumer() {
  const { config, accentClasses } = useTenant();
  return (
    <div>
      <span data-testid="name">{config.displayName}</span>
      <span data-testid="accent">{config.accentClass}</span>
      <span data-testid="tagline">{config.tagline}</span>
      <span data-testid="gradient">{accentClasses.accentGradient}</span>
    </div>
  );
}

describe("TenantContext", () => {
  it("provides tenant config to consumers", () => {
    render(
      <TenantProvider>
        <Consumer />
      </TenantProvider>,
    );
    expect(screen.getByTestId("name")).toHaveTextContent("TestTenant");
    expect(screen.getByTestId("accent")).toHaveTextContent("emerald");
    expect(screen.getByTestId("tagline")).toHaveTextContent("Test Tagline");
    expect(screen.getByTestId("gradient")).toHaveTextContent("from-success to-success");
  });

  it("applies the stored theme preference through the organisation's mechanism", () => {
    render(
      <TenantProvider>
        <Consumer />
      </TenantProvider>,
    );
    expect(mockSetAppearance).toHaveBeenCalledWith("dark");
  });

  it("throws when useTenant is used outside provider", () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation();
    expect(() => render(<Consumer />)).toThrow("useTenant must be used within a TenantProvider");
    consoleError.mockRestore();
  });
});

describe("accentMap", () => {
  it("contains expected accent keys", () => {
    expect(accentMap).toHaveProperty("rose");
    expect(accentMap).toHaveProperty("emerald");
    expect(accentMap).toHaveProperty("amber");
    expect(accentMap).toHaveProperty("zinc");
  });

  it("has all required CSS utility fields", () => {
    const keys = ["text", "bg", "hoverBg", "border", "ring", "bgSoft", "textBright", "accentGradient"];
    for (const accent of Object.values(accentMap)) {
      for (const key of keys) {
        expect(accent).toHaveProperty(key);
      }
    }
  });
});
