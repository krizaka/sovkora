import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { AppearancePicker } from "@/features/profile/components/AppearancePicker";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      settings: {
        themeMode: "Theme Mode (theme)",
        themeChangesInstant: "Changes apply instantly",
        themeSystem: "System",
        themeLight: "Light",
        themeDark: "Dark",
        themeElectric: "Electric",
        themeCustom: "Custom",
        themeCyberpunk: "Cyberpunk",
        themeSolarized: "Solarized",
        themeSystemDesc: "Follows OS",
        themeLightDesc: "Bright mode",
        themeDarkDesc: "Dark mode",
        themeElectricDesc: "Blue",
        themeCustomDesc: "Custom dark",
        themeCyberpunkDesc: "Neon style",
        themeSolarizedDesc: "Warm tones",
        themeClickToApply: "Click to apply",
        themeApplied: "applied",
      },
    },
    locale: "en",
  }),
}));

describe("AppearancePicker", () => {
  const onThemeChange = jest.fn();

  afterEach(() => jest.clearAllMocks());

  it("is a radio group named by its heading", () => {
    render(<AppearancePicker theme="system" onThemeChange={onThemeChange} />);
    expect(screen.getByRole("radiogroup", { name: "Theme Mode" })).toBeInTheDocument();
    expect(screen.getByText("Changes apply instantly")).toBeInTheDocument();
  });

  it("offers every appearance as a radio", () => {
    render(<AppearancePicker theme="system" onThemeChange={onThemeChange} />);
    for (const name of ["System", "Light", "Dark", "Electric", "Custom", "Cyberpunk", "Solarized"]) {
      expect(screen.getByRole("radio", { name: new RegExp(name) })).toBeInTheDocument();
    }
  });

  it("checks the active appearance", () => {
    render(<AppearancePicker theme="dark" onThemeChange={onThemeChange} />);
    expect(screen.getByRole("radio", { name: /^Dark/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /^Light/ })).not.toBeChecked();
  });

  it("calls onThemeChange when an appearance is chosen", () => {
    render(<AppearancePicker theme="system" onThemeChange={onThemeChange} />);
    fireEvent.click(screen.getByRole("radio", { name: /Cyberpunk/ }));
    expect(onThemeChange).toHaveBeenCalledWith("cyberpunk");
  });

  it("does not call onThemeChange for the active appearance", () => {
    render(<AppearancePicker theme="dark" onThemeChange={onThemeChange} />);
    fireEvent.click(screen.getByRole("radio", { name: /^Dark/ }));
    expect(onThemeChange).not.toHaveBeenCalled();
  });

  it("confirms the choice in a status line", () => {
    jest.useFakeTimers();
    render(<AppearancePicker theme="system" onThemeChange={onThemeChange} />);
    fireEvent.click(screen.getByRole("radio", { name: /^Dark/ }));
    expect(screen.getByRole("status")).toHaveTextContent("Dark applied");
    jest.useRealTimers();
  });
});
