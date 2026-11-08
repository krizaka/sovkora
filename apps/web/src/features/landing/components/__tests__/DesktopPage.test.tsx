import "@testing-library/jest-dom";
import { mockReducedMotion } from "@/features/landing/utils/reduced-motion.testkit";
import { render, screen, within } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { DesktopPage } from "@/features/landing/components/DesktopPage";
import { RELEASES_URL } from "@/features/landing/utils/release";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

beforeAll(mockReducedMotion);

const SHA = "fb8a1570c2f1032bf6029e3eebaf711d77b2f6dfe7c706cd6d592b3bea8d834b";
const release = {
  tag: "desktop-v9.9.9-test",
  version: "9.9.9-test",
  prerelease: true,
  publishedAt: "2026-10-14T00:00:00Z",
  pageUrl: "https://github.com/krizaka/orazaka/releases/tag/desktop-v9.9.9-test",
  dmg: { name: "Sovkora_9.9.9_aarch64.dmg", url: "https://example.test/Sovkora_9.9.9_aarch64.dmg", bytes: 6271937 },
  shaUrl: "https://example.test/Sovkora_9.9.9_aarch64.dmg.sha256",
  sha256: SHA,
};

function renderPage(locale: Locale, rel: typeof release | null, downloadEnabled = true) {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={{ login: false, signup: false, github: false, google: false }}>
      <ThemeProvider>
        <DesktopPage release={rel} downloadEnabled={downloadEnabled} />
      </ThemeProvider>
    </AuthFlagsProvider>,
  );
}

describe("DesktopPage with the Mac download switched off", () => {
  it.each(["en", "fr"] as const)(
    "disables the button, says it is in testing, carries no file link and no checksum (%s)",
    (locale) => {
      const { container } = renderPage(locale, release, false);
      const d = translations[locale].desktop.download;
      expect(screen.getByRole("button", { name: d.button })).toBeDisabled();
      expect(screen.getByText(d.disabled)).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: d.button })).toBeNull();
      expect(container.innerHTML).not.toContain("example.test");
      expect(container.innerHTML).not.toContain("/desktop/download");
      expect(container.innerHTML).not.toContain(SHA);
      expect(screen.queryByTestId("desktop-sha")).toBeNull();
      expect(screen.queryByRole("link", { name: d.releases })).toBeNull();
    },
  );
});

describe("DesktopPage", () => {
  it("puts one Download first, its facts quiet, the version read from the release — nothing hard-coded", () => {
    renderPage("en", release);
    const d = translations.en.desktop;
    const downloads = screen.getAllByRole("link", { name: d.download.button });
    expect(downloads).toHaveLength(1);
    expect(downloads[0]).toHaveAttribute("href", "/desktop/download");
    expect(screen.getByText(/Apple Silicon · macOS 14\+ · 6\.3 MB/)).toBeInTheDocument();
    expect(screen.getByTestId("desktop-version")).toHaveTextContent("9.9.9-test");
  });

  it("keeps the SHA-256 in a « Verify the download » disclosure, with a copy button", () => {
    renderPage("en", release);
    const sha = screen.getByTestId("desktop-sha");
    expect(sha).toHaveTextContent(SHA);
    const details = sha.closest("details") as HTMLElement;
    expect(details).not.toBeNull();
    expect(details.hasAttribute("open")).toBe(false);
    expect(within(details).getByText(translations.en.desktop.download.verify).tagName).toBe("SUMMARY");
    expect(within(details).getByRole("button", { name: translations.en.desktop.download.shaCopy })).toBeInTheDocument();
  });

  it("shows the app in a macOS window above the fold", () => {
    const { container } = renderPage("en", release);
    const frame = container.querySelector('[data-device="desktop"]') as HTMLElement;
    expect(within(frame).getByRole("img", { name: translations.en.desktop.hero.captureAlt })).toBeInTheDocument();
  });

  it("says it is a test build, then the first launch in three numbered steps, the quarantine fix folded in a bash block", () => {
    const { container } = renderPage("en", release);
    const d = translations.en.desktop;
    expect(screen.getByRole("note")).toHaveTextContent(d.download.testBuild);
    const steps = within(document.getElementById("first-launch")!).getAllByRole("listitem");
    expect(steps.map((li) => li.querySelector("h3")?.textContent)).toEqual(d.firstLaunch.steps.map((s) => s.title));
    const damaged = within(container).getByText(d.firstLaunch.damaged.summary).closest("details") as HTMLElement;
    expect(damaged.hasAttribute("open")).toBe(false);
    expect(damaged).toHaveTextContent("xattr -dr com.apple.quarantine /Applications/Sovkora.app");
  });

  it("explains the two modes and every service « Start » brings up, without jargon", () => {
    const { container } = renderPage("en", release);
    const d = translations.en.desktop;
    for (const m of d.modes.items) expect(screen.getByRole("heading", { name: m.title })).toBeInTheDocument();
    const services = container.querySelector(`dl[aria-label="${d.services.title}"]`)!;
    expect([...services.querySelectorAll("dt")].map((dt) => dt.textContent)).toEqual(
      d.services.rows.map((r) => r.name),
    );
    expect(container.textContent).not.toMatch(/sagas|Metal acceleration|cryptographic|tok\/s|HIPAA/i);
  });

  it("tells what to do when something goes wrong: doctor and logs, as bash", () => {
    const { container } = renderPage("en", release);
    expect(container.textContent).toContain("orazaka doctor");
    expect(container.textContent).toContain("orazaka logs --service router");
  });

  it("makes no claim about Windows or Linux builds, and lists the requirements", () => {
    const { container } = renderPage("en", release);
    expect(container.textContent).toMatch(/No Windows or Linux build/i);
    expect(container.textContent).not.toMatch(/Download for Windows/i);
    expect(container.textContent).toMatch(/Node\.js 22/);
  });

  it("links the releases page when the latest build cannot be read — no guessed URL, no button", () => {
    renderPage("en", null);
    const d = translations.en.desktop.download;
    expect(screen.queryByRole("link", { name: d.button })).toBeNull();
    expect(screen.getByRole("link", { name: d.releases })).toHaveAttribute("href", RELEASES_URL);
  });

  it("speaks French when the visitor chose it", () => {
    renderPage("fr", release);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(translations.fr.desktop.hero.title);
    expect(screen.getByRole("note")).toHaveTextContent(translations.fr.desktop.download.testBuild);
  });

  it("has the same shape in English and French", () => {
    const keys = (v: unknown): unknown =>
      Array.isArray(v)
        ? v.map(keys)
        : v && typeof v === "object"
          ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, keys(x)]))
          : typeof v;
    expect(keys(translations.fr.desktop)).toEqual(keys(translations.en.desktop));
  });
});
