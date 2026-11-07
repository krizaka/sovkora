import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { RunForm } from "@/features/studio/components/RunForm";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});
jest.mock("@/services/media.api", () => ({ MediaApi: { uploadMedia: jest.fn() } }));

const imageSchema = JSON.stringify({
  type: "object",
  required: ["prompt"],
  properties: {
    prompt: { type: "string", title: "Ce que vous voulez voir" },
    size: { type: "string", enum: ["1024x1024", "512x512"], default: "1024x1024", title: "Format" },
    negativePrompt: { type: "string", title: "À éviter", "x-advanced": true },
    seed: { type: "string", title: "Graine", "x-control": "seed", "x-advanced": true },
    model: {
      type: "string",
      title: "Modèle",
      "x-advanced": true,
      "x-options": { source: "models", category: "image" },
      default: "sd-hosted",
      oneOf: [
        {
          const: "sd-hosted",
          title: "Stable Diffusion 3.5 Large",
          description: "Hosted by a provider.",
          "x-meta": { runsOn: "cloud", speed: "fast", typicalSeconds: 8, priceCents: 8, available: true },
        },
        {
          const: "flux",
          title: "FLUX.2 [klein] 4B",
          "x-meta": {
            runsOn: "local",
            speed: "balanced",
            typicalSeconds: 62,
            available: false,
            unavailableReason: "FLUX.2 is not running",
          },
        },
        {
          const: "z-image",
          title: "Z-Image Turbo",
          "x-meta": { runsOn: "local", speed: "slow", typicalSeconds: 157, available: true },
        },
      ],
    },
  },
});

const setupImage = (schemaText = imageSchema) => {
  const onSubmit = jest.fn();
  const view = render(
    <RunForm
      studioKey="image-generation"
      inputSchema={schemaText}
      estimatedCredits={3}
      isSubmitting={false}
      onSubmit={onSubmit}
    />,
  );
  return { onSubmit, ...view };
};
const openMore = () => fireEvent.click(screen.getByText("More options"));

describe("RunForm — closed sets are chosen from a list, never typed", () => {
  it("keeps the model, the seed and the things to avoid behind « More options »", () => {
    setupImage();
    expect(screen.getByRole("combobox", { name: /Modèle|Model/, hidden: true })).not.toBeVisible();
    openMore();
    expect(screen.getByRole("combobox", { name: /Modèle|Model/ })).toBeVisible();
  });

  it("lists the catalogue's models with what a person needs to choose: place, speed, time, price", () => {
    setupImage();
    openMore();
    fireEvent.click(screen.getByRole("combobox", { name: /Modèle|Model/ }));
    // The option says what it is in a line; the facts are chips, for the model in view only.
    expect(screen.getByRole("option", { name: /Stable Diffusion 3.5 Large/ })).toHaveTextContent(
      "Hosted by a provider.",
    );
    expect(screen.getByRole("list", { name: /About this model/ })).not.toHaveTextContent("Free"); // the model in view: cloud, priced
  });

  it("shows a model whose engine is off, but does not let it be chosen, and says why", () => {
    setupImage();
    openMore();
    fireEvent.click(screen.getByRole("combobox", { name: /Modèle|Model/ }));
    const off = screen.getByRole("option", { name: /FLUX.2/ });
    expect(off).toHaveAttribute("aria-disabled", "true");
    expect(off).toHaveTextContent("FLUX.2 is not running");
  });

  it("starts on « Automatic » naming the model that will run, and sends no model", () => {
    const { onSubmit } = setupImage();
    openMore();
    expect(screen.getByRole("combobox", { name: /Modèle|Model/ })).toHaveAttribute(
      "placeholder",
      "Automatic · Stable Diffusion 3.5 Large",
    );
    fireEvent.change(screen.getByLabelText(/What you want to see/), { target: { value: "a fox" } });
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox", size: "1024x1024" });
  });

  it("sends the id of the model that was picked from the list", () => {
    const { onSubmit } = setupImage();
    openMore();
    fireEvent.click(screen.getByRole("combobox", { name: /Modèle|Model/ }));
    fireEvent.click(screen.getByRole("option", { name: /Z-Image Turbo/ }));
    fireEvent.change(screen.getByLabelText(/What you want to see/), { target: { value: "a fox" } });
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox", size: "1024x1024", model: "z-image" });
  });

  it("offers the format as a list with readable names, not raw values", () => {
    setupImage();
    const size = screen.getByLabelText("Format") as HTMLSelectElement;
    expect([...size.options].map((o) => o.textContent)).toEqual(["Square · 1024×1024", "Quick preview · 512×512"]);
  });

  it("has a seed that is random by default and sends nothing", () => {
    const { onSubmit } = setupImage();
    openMore();
    expect(screen.getByRole("switch", { name: "Random" })).toBeChecked();
    expect(screen.queryByPlaceholderText("Seed number")).toBeNull();
    fireEvent.change(screen.getByLabelText(/What you want to see/), { target: { value: "a fox" } });
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox", size: "1024x1024" });
  });

  it("sends the seed as digits once random is turned off", () => {
    const { onSubmit } = setupImage();
    openMore();
    fireEvent.click(screen.getByRole("switch", { name: "Random" }));
    fireEvent.change(screen.getByPlaceholderText("Seed number"), { target: { value: "42x7" } });
    expect(screen.getByPlaceholderText("Seed number")).toHaveValue("427");
    fireEvent.change(screen.getByLabelText(/What you want to see/), { target: { value: "a fox" } });
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox", size: "1024x1024", seed: "427" });
  });

  it("when the list could not be loaded, the model says so out loud — never a blank select or a text box", () => {
    const unloaded = JSON.stringify({
      type: "object",
      properties: {
        prompt: { type: "string" },
        model: {
          type: "string",
          title: "Modèle",
          "x-advanced": true,
          "x-options": { source: "models", category: "image" },
        },
      },
    });
    const error = jest.spyOn(console, "error").mockImplementation(() => undefined);
    const { container } = setupImage(unloaded);
    openMore();
    expect(screen.getByRole("alert")).toHaveTextContent("No model available: contact the administrator.");
    expect(screen.getByRole("alert")).toHaveTextContent("The list could not be loaded");
    expect(error).toHaveBeenCalledWith(expect.stringContaining("« model »"));
    expect(container.querySelector("select")).toBeNull();
    expect(container.querySelectorAll('input[type="text"]')).toHaveLength(1); // the prompt only
    error.mockRestore();
  });

  it("an empty catalogue list is the same visible error, and is logged", () => {
    const empty = JSON.stringify({
      type: "object",
      properties: {
        prompt: { type: "string" },
        model: { type: "string", title: "Model", "x-options": { source: "models", category: "chat" }, oneOf: [] },
      },
    });
    const error = jest.spyOn(console, "error").mockImplementation(() => undefined);
    setupImage(empty);
    expect(screen.getByRole("alert")).toHaveTextContent("No model available: contact the administrator.");
    expect(screen.queryByRole("combobox")).toBeNull();
    expect(error).toHaveBeenCalled();
    error.mockRestore();
  });
});
