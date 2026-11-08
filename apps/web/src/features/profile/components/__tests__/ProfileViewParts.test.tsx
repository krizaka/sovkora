import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { CopyableField } from "@/features/profile/components/ProfileViewParts";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: { a11y: jest.requireActual("@/core/context/translations.automation").a11y.en },
    locale: "en",
  }),
}));

jest.mock("@krizaka/orazaka-design-system", () => ({
  Icon: ({ name }: { readonly name: string }) => <span data-testid="mock-icon">{name}</span>,
}));

Object.assign(navigator, { clipboard: { writeText: jest.fn().mockResolvedValue(undefined) } });

describe("CopyableField (an id in mono with one copy icon)", () => {
  it("renders the value in small mono", () => {
    render(<CopyableField value="abc-123" />);
    const value = screen.getByText("abc-123");
    expect(value.className).toContain("font-mono");
    expect(value.className).toContain("text-xs");
  });

  it("has exactly one copy action, and copies the value", () => {
    render(<CopyableField value="secret-token" />);
    expect(screen.getAllByLabelText("Copy")).toHaveLength(1);
    fireEvent.click(screen.getByLabelText("Copy"));
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("secret-token");
  });
});
