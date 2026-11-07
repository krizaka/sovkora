import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { RunForm } from "@/features/studio/components/RunForm";

let mockLocale: "en" | "fr" = "en";
jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale }) };
});

// Run 6567e02a: the hosted Wan makes 16:9 only and the form's default was 9:16.
const schema = JSON.stringify({
  type: "object",
  required: ["prompt"],
  properties: {
    prompt: { type: "string", title: "Prompt", maxLength: 2000 },
    aspect: {
      type: "string",
      enum: ["9:16", "16:9", "1:1"],
      default: "9:16",
      title: "Format",
      "x-i18n": {
        en: { title: "Format", options: { "9:16": "Vertical 9:16", "16:9": "Wide 16:9", "1:1": "Square 1:1" } },
        fr: { title: "Format", options: { "9:16": "Vertical 9:16", "16:9": "Large 16:9", "1:1": "Carré 1:1" } },
      },
    },
    model: {
      type: "string",
      title: "Model",
      default: "wan-hosted",
      "x-options": {
        source: "models",
        category: "video",
        aspectField: "aspect",
        aspects: { "wan-hosted": ["16:9"], "wan-gpu": ["9:16", "16:9", "1:1"] },
      },
      "x-i18n": {
        en: {
          title: "Model",
          options: { "wan-hosted": { label: "Wan hosted", hint: "h" }, "wan-gpu": { label: "Wan GPU", hint: "g" } },
        },
        fr: {
          title: "Modèle",
          options: { "wan-hosted": { label: "Wan hébergé", hint: "h" }, "wan-gpu": { label: "Wan GPU", hint: "g" } },
        },
      },
      oneOf: [
        { const: "wan-hosted", title: "Wan hosted", "x-meta": { runsOn: "cloud", available: true } },
        { const: "wan-gpu", title: "Wan GPU", "x-meta": { runsOn: "local", available: true } },
      ],
    },
  },
});

const setup = () => {
  const onSubmit = jest.fn();
  render(
    <RunForm
      studioKey="test-studio"
      inputSchema={schema}
      estimatedCredits={1}
      isSubmitting={false}
      onSubmit={onSubmit}
    />,
  );
  return onSubmit;
};

describe.each([
  ["en", "Format", "Wide 16:9", "not for this model"],
  ["fr", "Format", "Large 16:9", "pas pour ce modèle"],
] as const)("a video form in %s", (locale, formatLabel, wide, reason) => {
  beforeEach(() => {
    mockLocale = locale;
  });

  it("offers only the selected model's aspects: the others are greyed out with a short reason, the default is the model's own", () => {
    const onSubmit = setup();
    const aspect = screen.getByLabelText(new RegExp(formatLabel)) as HTMLSelectElement;
    expect(aspect.value).toBe("16:9"); // the hosted Wan's: never the blueprint's 9:16
    const options = within(aspect).getAllByRole("option") as HTMLOptionElement[];
    const byValue = (v: string) => options.find((o) => o.value === v)!;
    expect(byValue("9:16")).toBeDisabled();
    expect(byValue("9:16")).toHaveTextContent(reason);
    expect(byValue("1:1")).toBeDisabled();
    expect(byValue("16:9")).not.toBeDisabled();
    expect(byValue("16:9")).toHaveTextContent(wide);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "a cabin" } });
    fireEvent.click(screen.getByRole("button", { name: /Run|Lancer/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ aspect: "16:9", prompt: "a cabin" }));
  });

  it("a model that makes every aspect enables them all, and the person's own choice is kept", () => {
    setup();
    fireEvent.click(screen.getByRole("combobox", { name: /Model|Modèle/ }));
    fireEvent.click(screen.getByRole("option", { name: /Wan GPU/ }));
    const aspect = screen.getByLabelText(new RegExp(formatLabel)) as HTMLSelectElement;
    const options = within(aspect).getAllByRole("option") as HTMLOptionElement[];
    expect(options.every((o) => !o.disabled)).toBe(true);
    expect(aspect.value).toBe("9:16"); // the blueprint's default, now supported
    fireEvent.change(aspect, { target: { value: "1:1" } });
    expect(aspect.value).toBe("1:1");
  });
});
