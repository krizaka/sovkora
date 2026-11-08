import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { translations } from "@/core/context/translations";
import { LocaleSwitch } from "@/components/LocaleSwitch";

const mockSetLocale = jest.fn();
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations.en, locale: "en", setLocale: mockSetLocale }),
}));
jest.mock("next/navigation", () => ({ usePathname: () => "/pricing" }));

afterEach(() => mockSetLocale.mockReset());

/* The language switch is a row of real links: each has its address, so a keyboard reaches it (Tab) and activates it
   (Enter) as a click; the click sets the locale and stays on the page. */
describe("LocaleSwitch", () => {
  it("gives every language a real link to the same page in that language", () => {
    render(<LocaleSwitch variant="inline" />);
    expect(screen.getByRole("link", { name: "English" })).toHaveAttribute("href", "/en/pricing");
    expect(screen.getByRole("link", { name: "Français" })).toHaveAttribute("href", "/fr/pricing");
  });

  it("is reached by the keyboard, and activating the link sets the locale without leaving the page", () => {
    render(<LocaleSwitch variant="inline" />);
    const french = screen.getByRole("link", { name: "Français" });
    french.focus();
    expect(french).toHaveFocus();

    const activated = fireEvent.click(french);

    expect(activated).toBe(false); // default prevented: no navigation
    expect(mockSetLocale).toHaveBeenCalledWith("fr");
  });
});
