import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { JobsGallery } from "@/app/jobs/JobsGallery";
import type { Job } from "@/core/types/jobs.types";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const prompt = "A heron standing in the shallow water at dawn, mist over the reeds, long exposure, ".repeat(4).trim();

const image: Job = {
  id: "job-image-1",
  userId: "u",
  featureKey: "orazaka.core.media.image",
  status: "COMPLETED",
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:02:00Z",
  payload: { prompt, model: "flux" },
  result: { url: "/heron.png" },
};

describe("JobsGallery", () => {
  it("labels a picture with a short result label, never the whole prompt", () => {
    render(<JobsGallery jobs={[image]} onOpen={jest.fn()} />);
    const img = screen.getByRole("img");
    const alt = img.getAttribute("alt") ?? "";
    expect(alt).toBe("Result of Image Generation");
    expect(alt.length).toBeLessThanOrEqual(120);
    expect(alt).not.toBe(prompt);
  });

  it("shows the file as expired when it no longer loads, and keeps the prompt in the card", () => {
    const { container } = render(<JobsGallery jobs={[image]} onOpen={jest.fn()} />);
    fireEvent.error(container.querySelector("img")!);
    expect(screen.getByText("File expired")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText(prompt)).toBeInTheDocument();
  });
});
