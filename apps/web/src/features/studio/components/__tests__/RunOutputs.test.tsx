import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { RunOutputs } from "@/features/studio/components/RunOutputs";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const outputs = [
  { key: "image", label: "Your image", type: "IMAGE", value: "/api/v1/assets/job-1/image.png" },
  { key: "seed", label: "Seed", type: "TEXT", value: "20261103" },
] as const;

const renderOutputs = () => render(<RunOutputs outputs={outputs} />);

describe("RunOutputs — a result whose file is gone", () => {
  it("shows the image while it loads", () => {
    renderOutputs();
    expect(screen.getByAltText("Your image")).toBeInTheDocument();
    expect(screen.queryByText("This result is no longer available")).not.toBeInTheDocument();
  });

  it("says the result is no longer available, instead of a broken image, when the asset 404s", () => {
    renderOutputs();
    fireEvent.error(screen.getByAltText("Your image"));
    expect(screen.getByRole("status")).toHaveTextContent("This result is no longer available");
    expect(screen.queryByAltText("Your image")).not.toBeInTheDocument();
    // The run's facts are still there, and there is nothing left to download.
    expect(screen.getByText("20261103")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /download/i })).not.toBeInTheDocument();
  });
});
