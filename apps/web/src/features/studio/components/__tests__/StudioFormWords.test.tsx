import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { RunForm } from "@/features/studio/components/RunForm";

let mockLocale: "en" | "fr" = "en";
jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale }) };
});

const schema = JSON.stringify({
  type: "object",
  required: ["question"],
  properties: {
    question: {
      type: "string",
      format: "prose",
      maxLength: 20000,
      title: "Votre question",
      description: "Ce que vous voulez voir.",
      "x-i18n": {
        en: {
          title: "Your question",
          description: "What you want to see.",
          placeholder: "Which video format works best for me?",
        },
        fr: {
          title: "Votre question",
          description: "Ce que vous voulez voir.",
          placeholder: "Quel format vidéo marche le mieux pour moi ?",
        },
      },
    },
    title: {
      type: "string",
      maxLength: 200,
      title: "Votre titre",
      "x-i18n": {
        en: { title: "Your title", placeholder: "My title" },
        fr: { title: "Votre titre", placeholder: "Mon titre" },
      },
    },
    model: {
      type: "string",
      title: "Modèle",
      "x-options": { source: "models", category: "video" },
      "x-i18n": {
        en: { title: "Model", description: "Auto picks a model that runs." },
        fr: { title: "Modèle", description: "Auto choisit un modèle disponible." },
      },
      default: "wan",
      oneOf: [
        { const: "wan", title: "Wan hosted", "x-meta": { runsOn: "cloud", available: true } },
        {
          const: "ltx",
          title: "LTX-Video",
          "x-meta": {
            runsOn: "local",
            available: false,
            unavailableReason: "LTX-Video needs a GPU, and none is online right now",
            unavailableCode: "gpu_offline",
          },
        },
        {
          const: "wan-local",
          title: "Wan local",
          "x-meta": {
            runsOn: "local",
            available: false,
            unavailableReason: "Wan local is not available (a sentence the server wrote)",
            unavailableCode: "worker_offline",
          },
        },
        {
          const: "odd",
          title: "Odd",
          "x-meta": {
            runsOn: "local",
            available: false,
            unavailableReason: "A reason from a newer server",
            unavailableCode: "something_new",
          },
        },
      ],
    },
  },
});

const setup = () =>
  render(
    <RunForm
      studioKey="data-driven-ui"
      inputSchema={schema}
      estimatedCredits={1}
      isSubmitting={false}
      onSubmit={jest.fn()}
    />,
  );

describe.each([
  [
    "en",
    "Your question",
    "Which video format works best for me?",
    "Your title",
    "My title",
    "GPU offline",
    "Engine offline",
  ],
  [
    "fr",
    "Votre question",
    "Quel format vidéo marche le mieux pour moi ?",
    "Votre titre",
    "Mon titre",
    "GPU hors ligne",
    "Moteur hors ligne",
  ],
] as const)("a Studio form in %s", (locale, question, questionHint, title, titleHint, gpuOffline, workerOffline) => {
  beforeEach(() => {
    mockLocale = locale;
  });

  it("shows a free-text field's title and placeholder in the page's language, from the pack (x-i18n)", () => {
    setup();
    expect(screen.getByLabelText(new RegExp(question))).toHaveAttribute("placeholder", questionHint);
    expect(screen.getByLabelText(new RegExp(title))).toHaveAttribute("placeholder", titleHint);
  });

  it("greys out a model that cannot run, with a short reason in the page's language (never the server's English)", () => {
    setup();
    fireEvent.click(screen.getByRole("combobox", { name: locale === "en" ? "Model" : "Modèle" }));
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    const byName = (name: string) => options.find((option) => option.textContent?.includes(name))!;
    expect(byName("LTX-Video")).toHaveAttribute("aria-disabled", "true");
    expect(byName("LTX-Video")).toHaveTextContent(gpuOffline);
    expect(byName("LTX-Video")).not.toHaveTextContent("needs a GPU");
    expect(byName("Wan local")).toHaveTextContent(workerOffline);
    expect(byName("Odd")).toHaveTextContent("A reason from a newer server"); // an unknown code falls back to the server's words
    expect(byName("Wan hosted")).not.toHaveAttribute("aria-disabled", "true");
  });
});
