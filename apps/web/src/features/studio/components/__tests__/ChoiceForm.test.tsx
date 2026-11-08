import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { RunForm } from "@/features/studio/components/RunForm";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});

const videoSchema = JSON.stringify({
  type: "object",
  properties: {
    aspect: {
      type: "string",
      enum: ["9:16", "16:9", "1:1"],
      default: "9:16",
      title: "Format",
      description: "9:16 : la vidéo verticale.",
      "x-i18n": {
        en: {
          title: "Format",
          description: "9:16 is vertical video.",
          options: { "9:16": "Vertical 9:16", "16:9": "Wide 16:9", "1:1": "Square 1:1" },
        },
        fr: {
          title: "Format",
          description: "9:16 : la vidéo verticale.",
          options: { "9:16": "Vertical 9:16", "16:9": "Large 16:9", "1:1": "Carré 1:1" },
        },
      },
    },
    model: {
      type: "string",
      title: "Modèle",
      "x-options": { source: "models", category: "video", facts: { ltx: ["1216×704", "5 s"], wan: ["832×480"] } },
      "x-i18n": {
        en: {
          title: "Model",
          description: "Auto picks the best model that is running.",
          options: {
            ltx: { label: "LTX-Video · fast", hint: "Image or text to video in about 15 s." },
            wan: { label: "Wan 2.2 · cloud", hint: "Text to video, hosted." },
          },
        },
        fr: {
          title: "Modèle",
          description: "Auto choisit le meilleur modèle disponible.",
          options: {
            ltx: { label: "LTX-Video · rapide", hint: "Image ou texte vers vidéo en 15 s." },
            wan: { label: "Wan 2.2 · infonuagique", hint: "Texte vers vidéo, hébergé." },
          },
        },
      },
      default: "wan",
      oneOf: [
        {
          const: "wan",
          title: "Wan 2.2 T2V A14B (serverless)",
          description: "A long catalogue paragraph.",
          "x-meta": { runsOn: "cloud", speed: "slow", typicalSeconds: 131, priceCents: 60, available: true },
        },
        {
          const: "ltx",
          title: "LTX-Video 0.9.8",
          description: "Another one.",
          "x-meta": { runsOn: "local", typicalSeconds: 15, available: true },
        },
      ],
    },
  },
});

describe("RunForm — the video Studio's model is a populated, short, readable list", () => {
  const setupVideo = () => {
    const onSubmit = jest.fn();
    render(
      <RunForm
        studioKey="video-generation"
        inputSchema={videoSchema}
        estimatedCredits={3}
        isSubmitting={false}
        onSubmit={onSubmit}
      />,
    );
    return { onSubmit };
  };

  it("renders N options with a short label and a one-line description each, and a short helper", () => {
    setupVideo();
    expect(screen.getByText("Auto picks the best model that is running.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("combobox", { name: "Model" }));
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options).toHaveLength(2);
    expect(options[0]).toHaveTextContent("Wan 2.2 · cloud");
    expect(options[1]).toHaveTextContent("LTX-Video · fast");
    expect(options[1]).toHaveTextContent("Image or text to video in about 15 s.");
  });

  it("shows the chips of the selected model only, and changes them with the choice", () => {
    const { onSubmit } = setupVideo();
    const chips = () => screen.getByRole("list", { name: /About this model/ });
    expect(chips()).toHaveAccessibleName("About this model: Wan 2.2 · cloud"); // the default that will run
    expect(chips()).toHaveTextContent("Cloud");
    expect(chips()).toHaveTextContent("about 131 s");
    expect(chips()).toHaveTextContent("$0.60 per run");
    expect(chips()).toHaveTextContent("832×480");
    fireEvent.click(screen.getByRole("combobox", { name: "Model" }));
    fireEvent.click(screen.getByRole("option", { name: /LTX-Video · fast/ }));
    expect(chips()).toHaveTextContent("1216×704");
    expect(chips()).not.toHaveTextContent("832×480");
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ aspect: "9:16", model: "ltx" });
  });

  it("compares every model in a disclosure, from the catalogue's facts", () => {
    setupVideo();
    const compare = screen.getByText("Compare models").closest("details")!;
    expect(compare).toHaveTextContent("LTX-Video · fast");
    expect(compare).toHaveTextContent("Wan 2.2 · cloud");
    expect(compare).toHaveTextContent("1216×704");
    expect(compare).toHaveTextContent("832×480");
  });

  it("names the format's choices in English from the pack", () => {
    setupVideo();
    const aspect = screen.getByLabelText("Format") as HTMLSelectElement;
    expect([...aspect.options].map((o) => o.textContent)).toEqual(["Vertical 9:16", "Wide 16:9", "Square 1:1"]);
    expect(screen.getByText("9:16 is vertical video.")).toBeInTheDocument();
  });

  it("an enum with no value is the same visible error, not an empty select", () => {
    const error = jest.spyOn(console, "error").mockImplementation(() => undefined);
    render(
      <RunForm
        studioKey="image-generation"
        inputSchema={JSON.stringify({
          type: "object",
          properties: { size: { type: "string", enum: [], title: "Format" } },
        })}
        estimatedCredits={3}
        isSubmitting={false}
        onSubmit={jest.fn()}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("No option available: contact the administrator.");
    expect(screen.queryByRole("combobox")).toBeNull();
    error.mockRestore();
  });
});

describe("RunForm — a number is a number input with the schema's bounds", () => {
  it("renders an integer as a bounded number input, sending its digits", () => {
    const onSubmit = jest.fn();
    render(
      <RunForm
        studioKey="video-generation"
        inputSchema={JSON.stringify({
          type: "object",
          properties: {
            durationSeconds: { type: "integer", minimum: 1, maximum: 20, default: 5, title: "Duration (seconds)" },
          },
        })}
        estimatedCredits={3}
        isSubmitting={false}
        onSubmit={onSubmit}
      />,
    );
    const duration = screen.getByLabelText("Duration (seconds)") as HTMLInputElement;
    expect([duration.type, duration.min, duration.max, duration.placeholder]).toEqual(["number", "1", "20", "5"]);
    fireEvent.change(duration, { target: { value: "8" } });
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ durationSeconds: "8" });
  });
});

describe("RunForm — the video prompt's guidance is in the page's language, in a line", () => {
  it("shows the English helper, not the blueprint's French paragraph", () => {
    render(
      <RunForm
        studioKey="video-generation"
        inputSchema={JSON.stringify({
          type: "object",
          required: ["prompt"],
          properties: {
            prompt: {
              type: "string",
              title: "Ce qui doit se passer à l'écran",
              description: "Décrivez le plan. ".repeat(20),
            },
          },
        })}
        estimatedCredits={3}
        isSubmitting={false}
        onSubmit={jest.fn()}
      />,
    );
    const help = screen.getByText(/Describe the shot/);
    expect(help.textContent!.length).toBeLessThanOrEqual(140);
    expect(screen.queryByText(/Décrivez le plan/)).toBeNull();
  });
});
