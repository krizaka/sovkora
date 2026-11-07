import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { LandingFilm } from "@/features/landing/components/LandingFilm";
import { StudioOutputs } from "@/features/landing/components/StudioOutputs";
import { NO_FILM_FACTS, filmFacts } from "@/features/landing/utils/film-records";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

beforeAll(() => {
  // jsdom has no matchMedia: reduced motion, so the poster and the first key frame stand.
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: query.includes("reduce"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }),
  });
});

const facts = filmFacts({
  models: [
    { role: "keyframes", name: "FLUX.2 [klein] 4B" },
    { role: "shots", name: "Wan 2.2 I2V A14B" },
  ],
  shots: [
    { id: "s2-01-a14b", runId: "run-a", model: "m", gpu: "NVIDIA H200", gpuSeconds: 1, gpuCostUsd: 0.25 },
    { id: "s5-01-a14br", runId: "run-b", model: "m", gpu: "NVIDIA H200", gpuSeconds: 1, gpuCostUsd: 0.25 },
  ],
});

describe("« Made with Sovkora » on the home (krizaka-internal#138)", () => {
  it.each(["en", "fr"] as const)("%s: the film with controls, and the facts summed from the records", (locale) => {
    mockLocale = locale;
    render(<LandingFilm facts={facts} />);
    const section = screen.getByTestId("landing-film");
    expect(screen.getByText(translations[locale].landing.film.title)).toBeInTheDocument();
    expect(within(section).getByTestId("film-player")).toHaveAttribute("controls");
    const rows = within(section).getByTestId("film-facts");
    expect(rows).toHaveTextContent("NVIDIA H200");
    expect(rows).toHaveTextContent("Wan 2.2 I2V A14B");
    expect(rows).toHaveTextContent(/0[.,]50/);
    expect(within(section).getByText("run-b")).toBeInTheDocument();
  });

  it("without readable records it shows the film and no figure", () => {
    mockLocale = "en";
    render(<LandingFilm facts={NO_FILM_FACTS} />);
    expect(screen.getByTestId("film-player")).toBeInTheDocument();
    expect(screen.queryByTestId("film-facts")).toBeNull();
  });

  it("the Studios card shows the first key frame still under reduced motion, naming its Studio and model", () => {
    mockLocale = "en";
    render(<StudioOutputs facts={facts} />);
    expect(screen.getByTestId("studio-outputs")).toHaveTextContent("Image Studio · FLUX.2 [klein] 4B");
  });
});
