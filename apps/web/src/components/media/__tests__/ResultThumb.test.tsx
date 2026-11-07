import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { ResultThumb } from "@/components/media/ResultThumb";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

describe("ResultThumb", () => {
  it("shows the designed placeholder when the image fails to load, never a broken image", () => {
    const { container } = render(<ResultThumb kind="image" src="/gone.png" alt="Result of Image Generation" />);
    fireEvent.error(container.querySelector("img")!);
    expect(screen.getByText("File expired")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("shows the placeholder when the video fails to load", () => {
    const { container } = render(<ResultThumb kind="video" src="/gone.mp4" alt="Result of Video Generation" />);
    fireEvent.error(container.querySelector("video")!);
    expect(screen.getByText("File expired")).toBeInTheDocument();
    expect(container.querySelector("video")).toBeNull();
  });

  it("keeps the short label as the alt of a picture that loads", () => {
    render(<ResultThumb kind="image" src="/ok.png" alt="Result of Image Generation" />);
    expect(screen.getByRole("img", { name: "Result of Image Generation" })).toBeInTheDocument();
  });

  it("the compact row thumbnail names the placeholder for assistive technology", () => {
    const { container } = render(<ResultThumb kind="image" src="/gone.png" alt="Result of Image Generation" compact />);
    fireEvent.error(container.querySelector("img")!);
    expect(screen.getByRole("img", { name: "File expired" })).toBeInTheDocument();
  });
});
