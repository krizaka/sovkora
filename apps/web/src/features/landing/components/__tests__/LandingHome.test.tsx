import type * as React from "react";
import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import type { AuthFlags } from "@/core/config/auth-flags";
import { LandingPage } from "@/features/landing/components/LandingPage";

let mockLocale: Locale = "en";
const mockSetLocale = jest.fn();
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: mockSetLocale }),
}));

beforeAll(() => {
  // jsdom has no matchMedia; the theme provider and the chat showcase read it (reduced motion, system theme).
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: query.includes("reduce"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      onchange: null,
      dispatchEvent: () => false,
    }),
  });
});

const OPEN: AuthFlags = { login: true, signup: true, github: false, google: false };

function renderLanding(locale: Locale, page: React.ReactNode = <LandingPage />, flags: AuthFlags = OPEN) {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={flags}>
      <ThemeProvider>{page}</ThemeProvider>
    </AuthFlagsProvider>,
  );
}

/** A Section puts its id on its heading: the section is the heading's `section`. */
const sectionOf = (id: string) => document.getElementById(id)!.closest("section") as HTMLElement;
const MEASURED = { ok: 128, all: 143, models: 9, day: "2026-10-17" };
const SECTION_IDS = ["features", "records"] as const;

describe("LandingPage", () => {
  it("states one promise in one line, one primary action and a quiet second one — no pill, no checkmark row", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const h = translations.en.landing.hero;
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(h.title);
    const hero = screen.getByRole("heading", { level: 1 }).closest("header") as HTMLElement;
    const actions = within(hero).getAllByRole("link");
    expect(actions.map((a) => a.textContent?.trim())).toEqual([translations.en.landing.cta.primary, h.secondary]);
    expect(actions[0]).toHaveAttribute("href", "/register");
    expect(actions[1]).toHaveAttribute("href", "#records");
    expect(hero.querySelector("ul, [data-testid='chat-showcase']")).toBeNull();
    expect(document.body.textContent).not.toMatch(/sovereign AI platform|next-gen/i);
  });

  it("shows the real product in the hero: the recorded chat of the page's language and theme, poster first, never a mock chat", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const look = document.documentElement.classList.contains("light") ? "light" : "dark";
    const take = screen.getByTestId("clip-take") as HTMLVideoElement;
    expect(take.muted).toBe(true);
    expect(take.loop).toBe(true);
    expect(take).not.toHaveAttribute("controls");
    expect(take).toHaveAttribute(
      "poster",
      expect.stringMatching(new RegExp(`sites/orazaka/home/take-en-${look}-poster\\.[0-9a-f]{10}\\.webp$`)),
    );
    expect(take.querySelector("source[type='video/webm']")).toHaveAttribute(
      "src",
      expect.stringMatching(new RegExp(`take-en-${look}\\.[0-9a-f]{10}\\.webm$`)),
    );
    expect(take.querySelector("source[type='video/mp4']")).toHaveAttribute(
      "src",
      expect.stringMatching(new RegExp(`take-en-${look}\\.[0-9a-f]{10}\\.mp4$`)),
    );
    expect(screen.getByText(translations.en.clips.take.caption)).toBeInTheDocument();
  });

  it("follows the language: the French page films the French take", () => {
    renderLanding("fr", <LandingPage measured={MEASURED} />);
    expect(screen.getByTestId("clip-take")).toHaveAttribute(
      "poster",
      expect.stringMatching(/take-fr-(dark|light)-poster/),
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(translations.fr.landing.hero.title);
  });

  it("counts its figures from the measured runs it is given, never from a typed number", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const labels = translations.en.landing.figures;
    for (const label of [labels.runs, labels.models, labels.telemetry]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText(labels.runs).closest("div")?.querySelector("dt")).not.toBeNull();
  });

  it("tells the story in order: what it does, then the one proof", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const main = screen.getByRole("main");
    const els = SECTION_IDS.map((id) => document.getElementById(id));
    els.forEach((el) => expect(main.contains(el)).toBe(true));
    const position = (el: HTMLElement) => [...main.querySelectorAll("section, [id]")].indexOf(el);
    const order = els.map((el) => position(el!));
    expect(order).toEqual([...order].sort((a, b) => a - b));
  });

  it("has three capabilities, each a captioned clip of the real app (never a fake chat)", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const features = sectionOf("features");
    const tiles = features.querySelectorAll("ul > li");
    expect(tiles).toHaveLength(3);
    // The real outputs of the film (StudioOutputs) are videos too, but not screen recordings: they have no captions to check.
    const clips = [...features.querySelectorAll<HTMLVideoElement>("video[data-testid^=clip-]")];
    expect(clips.map((v) => v.getAttribute("data-testid"))).toEqual(["clip-studio", "clip-results", "clip-workspace"]);
    clips.forEach((v) => {
      expect(v.getAttribute("poster")).toMatch(
        /sites\/orazaka\/web\/step-(studio|results|workspace)-(dark|light)-poster\.[0-9a-f]{10}\.webp$/,
      );
      expect(v.muted).toBe(true);
      expect(v.loop).toBe(true);
      expect(v.querySelectorAll("track[kind='captions']")).toHaveLength(2);
    });
  });

  it("proves it with three run records — model, where it ran, time, cost and the run id — read from the comparison's data", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    const records = sectionOf("records");
    const items = records.querySelectorAll("ul > li");
    expect(items).toHaveLength(3);
    expect(records).toHaveTextContent("Stable Diffusion 3.5 Large");
    items.forEach((li) => expect(li.querySelector("dd.font-mono")?.getAttribute("title")).toMatch(/^[0-9a-f-]{36}$/));
    expect(
      within(records).getByRole("link", { name: new RegExp(translations.en.landing.records.more) }),
    ).toHaveAttribute("href", "/docs/measured-runs");
    expect(records).toHaveTextContent("2026-10-17");
  });

  it("is small: the sections of the epic #231 are gone (platforms strip, family, comparison, questions)", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    for (const id of ["platforms", "engine", "why", "faq"]) {
      expect(document.getElementById(id)).toBeNull();
    }
    expect(screen.queryByRole("table")).toBeNull();
    expect(document.querySelector('script[type="application/ld+json"]')).toBeNull();
  });

  it("closes with one call: the way in — no « Talk to us »", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />);
    expect(screen.queryByRole("link", { name: /talk to us/i })).toBeNull();
    expect(screen.getByText(translations.en.landing.cta.title)).toBeInTheDocument();
  });

  it("never says the hero's action again at the end (krizaka/orazaka#443): the closing call is the quickstart", () => {
    const { container } = renderLanding("en", <LandingPage measured={MEASURED} />);
    const hero = screen.getByRole("heading", { level: 1 }).closest("header") as HTMLElement;
    const heroHref = within(hero).getAllByRole("link")[0].getAttribute("href");
    const closing = screen.getByRole("link", { name: translations.en.landing.cta.quickstart });
    expect(closing).toHaveAttribute("href", "/docs/101");
    expect(closing.getAttribute("href")).not.toBe(heroHref);
    expect(container.querySelectorAll(`a[href="${heroHref}"]`).length).toBeLessThanOrEqual(3);
  });

  it("speaks French when the visitor chose it", () => {
    renderLanding("fr", <LandingPage measured={MEASURED} />);
    expect(screen.getByText(translations.fr.landing.cta.title)).toBeInTheDocument();
    expect(screen.getByText(translations.fr.landing.features.items.studios.title)).toBeInTheDocument();
  });
});

describe("LandingPage while sign-in and sign-up are closed (the default)", () => {
  const CLOSED: AuthFlags = { login: false, signup: false, github: false, google: false };

  it("hides every sign-up entry point but keeps Sign in visible, leading to /login", () => {
    const { container } = renderLanding("en", <LandingPage measured={MEASURED} />, CLOSED);
    const hrefs = [...container.querySelectorAll("a")].map((a) => a.getAttribute("href"));
    expect(hrefs).not.toContain("/register");
    expect(screen.queryByRole("link", { name: /Get started|Create your workspace/ })).toBeNull();
    const signIn = screen.getAllByRole("link", { name: translations.en.landing.nav.signIn });
    signIn.forEach((a) => expect(a).toHaveAttribute("href", "/login"));
  });

  it("offers the documentation as the one action, and the recorded runs as the quiet one", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />, CLOSED);
    const hero = screen.getByRole("heading", { level: 1 }).closest("header") as HTMLElement;
    const [primary, secondary] = within(hero).getAllByRole("link");
    expect(primary).toHaveTextContent(translations.en.landing.closed.heroPrimary);
    expect(primary).toHaveAttribute("href", "/docs");
    expect(secondary).toHaveAttribute("href", "#records");
  });

  it("keeps the Krizaka signature with the version in the footer", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />, CLOSED);
    const footer = screen.getByTestId("app-version");
    expect(footer).toHaveTextContent("A Krizaka product");
    expect(footer).toHaveTextContent(/v\S+/);
    expect(within(footer).getByRole("link", { name: /A Krizaka product/ })).toHaveAttribute(
      "href",
      "https://www.krizaka.com",
    );
    expect(within(footer).queryByRole("navigation", { name: "Krizaka family" })).toBeNull();
  });

  it("wears the family's bar: no version, the repository, one main action, no family switcher", () => {
    renderLanding("en", <LandingPage measured={MEASURED} />, CLOSED);
    const bar = document.querySelector("header[data-floating]") as HTMLElement;
    expect(screen.queryByRole("navigation", { name: "Krizaka family" })).toBeNull();
    expect(bar).not.toHaveTextContent(/dev · v/);
    expect(within(bar).getByRole("link", { name: translations.en.landing.nav.repo })).toHaveAttribute(
      "href",
      "https://github.com/krizaka/orazaka-web-client",
    );
    expect(within(bar).getAllByRole("link", { name: translations.en.landing.nav.signIn })[0]).toHaveAttribute(
      "href",
      "/login",
    );
    expect(within(bar).queryByRole("link", { name: /talk to us|contact/i })).toBeNull();
    const centre = within(bar).getByRole("navigation", { name: "Sovkora" });
    expect(
      within(centre)
        .getAllByRole("link")
        .map((a) => a.getAttribute("href")),
    ).toEqual(["#features", "/desktop", "/docs", "/pricing", "/open-source"]);
    expect(
      within(centre)
        .getAllByRole("button")
        .map((b) => b.textContent),
    ).toEqual([translations.en.landing.nav.product]);
  });
});
