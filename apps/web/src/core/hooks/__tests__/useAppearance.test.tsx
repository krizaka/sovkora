import "@testing-library/jest-dom";
import { act, renderHook } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { toAppearance, useAppearance } from "@/core/hooks/useAppearance";

const wrapper = ({ children }: { children: React.ReactNode }) => <ThemeProvider>{children}</ThemeProvider>;

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: true,
      media: query,
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    }),
  });
});

beforeEach(() => {
  localStorage.clear();
  document.documentElement.className = "";
});

describe("useAppearance (the organisation's theme mechanism)", () => {
  it("follows the system by default", () => {
    const { result } = renderHook(() => useAppearance(), { wrapper });
    expect(result.current.appearance).toBe("system");
  });

  it("applies light on html.light and dark by removing it — no .dark class", () => {
    const { result } = renderHook(() => useAppearance(), { wrapper });
    act(() => result.current.setAppearance("light"));
    expect(document.documentElement).toHaveClass("light");
    expect(result.current.appearance).toBe("light");
    act(() => result.current.setAppearance("dark"));
    expect(document.documentElement).not.toHaveClass("light");
    expect(document.documentElement).not.toHaveClass("dark");
    expect(localStorage.getItem("kz-theme")).toBe("dark");
  });

  it("applies a named theme as html.theme-<name>, with its mode, and clears it for a mode", () => {
    const { result } = renderHook(() => useAppearance(), { wrapper });
    act(() => result.current.setAppearance("solarized"));
    expect(document.documentElement).toHaveClass("theme-solarized", "light");
    expect(result.current.appearance).toBe("solarized");
    act(() => result.current.setAppearance("cyberpunk"));
    expect(document.documentElement).toHaveClass("theme-cyberpunk");
    expect(document.documentElement).not.toHaveClass("theme-solarized", "light");
    act(() => result.current.setAppearance("system"));
    expect(document.documentElement).not.toHaveClass("theme-cyberpunk");
    expect(localStorage.getItem("kz-theme-name")).toBeNull();
  });

  it("reads a stored preference, unknown values as system", () => {
    expect(toAppearance("krizaka")).toBe("krizaka");
    expect(toAppearance("dark")).toBe("dark");
    expect(toAppearance("neon")).toBe("system");
    expect(toAppearance(undefined)).toBe("system");
  });
});
