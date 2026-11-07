import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { Greeting, dayPeriod, greetingParts } from "@/core/components/Greeting";

let dictionary: { welcome: string; greeting: Record<string, string> };
jest.mock("@/core/hooks/useAuth", () => ({ useAuth: () => ({ user: { name: "Eric Morel" } }) }));
jest.mock("@/core/context/LocaleContext", () => ({ useTranslation: () => ({ t: { dashboard: dictionary } }) }));

const EN = {
  welcome: "Welcome back",
  greeting: { morning: "Good morning, {name}", afternoon: "Good afternoon, {name}", evening: "Good evening, {name}" },
};
const FR = {
  welcome: "Bon retour",
  greeting: { morning: "Bonjour {name}", afternoon: "Bon après-midi {name}", evening: "Bonsoir {name}" },
};

describe("Greeting", () => {
  afterEach(() => jest.useRealTimers());

  it("names the part of the day", () => {
    expect(dayPeriod(8)).toBe("morning");
    expect(dayPeriod(12)).toBe("afternoon");
    expect(dayPeriod(17)).toBe("afternoon");
    expect(dayPeriod(18)).toBe("evening");
  });

  it("splits a template around the name", () => {
    expect(greetingParts("Good evening, {name}", "Eric")).toEqual(["Good evening, ", "Eric", ""]);
    expect(greetingParts("Bonsoir {name}", "Eric")).toEqual(["Bonsoir ", "Eric", ""]);
    expect(greetingParts("Hello", "Eric")).toEqual(["Hello", "", ""]);
  });

  it.each([
    [EN, "Good evening, Eric."],
    [FR, "Bonsoir Eric."],
  ])("greets the first name in the viewer's language (%#)", (d, text) => {
    dictionary = d;
    jest.useFakeTimers().setSystemTime(new Date(2026, 9, 9, 20, 30));
    const { container } = render(<Greeting suffix="." />);
    expect(container.textContent).toBe(text);
    expect(screen.getByText("Eric")).toHaveClass("text-accent");
  });
});
