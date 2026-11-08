import "@testing-library/jest-dom";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { CapabilityOptions } from "@/features/chat-session/components/CapabilityOptions";
import type { ComposerStudio } from "@krizaka/orazaka-shared";

jest.mock("next-auth/react", () => ({
  getSession: jest.fn().mockResolvedValue({ user: { id: "u1" } }),
}));

const t = {
  chat: {
    optModel: "Model",
    optAuto: "Auto",
    optVoice: "Voice",
    optSize: "Size",
    optDuration: "Duration",
    attachToAnalyze: "Attach a file to analyze",
  },
} as never;

const imageGen: ComposerStudio = {
  studioKey: "image-generation",
  label: "Image Generation",
  iconKey: "image",
  version: "1.0.0",
  capabilityKey: "orazaka.core.media.image",
  inputKey: "prompt",
  inputKind: "TEXT",
  promptKey: "prompt",
  available: true,
  lockedReason: "NONE",
};

const imageAnalysis: ComposerStudio = {
  studioKey: "image-analysis",
  label: "Image Analysis",
  iconKey: "vision",
  version: "1.1.0",
  capabilityKey: "orazaka.core.media.vision",
  inputKey: "assetId",
  inputKind: "ASSET",
  promptKey: "prompt",
  available: true,
  lockedReason: "NONE",
};

const renderOpts = (studio: ComposerStudio, overrides = {}) =>
  render(
    <CapabilityOptions
      studio={studio}
      options={{}}
      onChange={jest.fn()}
      attachment={null}
      onAttach={jest.fn()}
      t={t}
      {...overrides}
    />,
    { wrapper: queryWrapper() },
  );

afterEach(() => jest.restoreAllMocks());

describe("CapabilityOptions", () => {
  test("loads catalog models filtered by the capability category", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue([
        { modelName: "sdxl-turbo-gguf", modelLabel: "SDXL Turbo", category: "image" },
        { modelName: "whisper-base", modelLabel: "Whisper", category: "audio" },
      ]),
    } as unknown as Response);

    renderOpts(imageGen);
    await waitFor(() => expect(screen.getByRole("option", { name: "SDXL Turbo" })).toBeInTheDocument());
    // The audio model is filtered out (wrong category).
    expect(screen.queryByRole("option", { name: "Whisper" })).not.toBeInTheDocument();
    // Image generation also exposes a Size field.
    expect(screen.getByText("Size")).toBeInTheDocument();
  });

  test("falls back to preset models when the catalog is unavailable", async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false } as Response);
    renderOpts(imageGen);
    await waitFor(() => expect(screen.getByRole("option", { name: "sdxl-turbo-gguf" })).toBeInTheDocument());
  });

  test("calls onChange when a model is selected", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: jest
        .fn()
        .mockResolvedValue([{ modelName: "sdxl-turbo-gguf", modelLabel: "SDXL Turbo", category: "image" }]),
    } as unknown as Response);
    const onChange = jest.fn();
    renderOpts(imageGen, { onChange });
    await waitFor(() => expect(screen.getByRole("option", { name: "SDXL Turbo" })).toBeInTheDocument());
    fireEvent.change(screen.getAllByRole("combobox")[0], {
      target: { value: "sdxl-turbo-gguf" },
    });
    expect(onChange).toHaveBeenCalledWith({ model: "sdxl-turbo-gguf" });
  });

  test("surfaces an attach affordance for analysis capabilities", async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false } as Response);
    renderOpts(imageAnalysis);
    expect(screen.getByText("Attach a file to analyze")).toBeInTheDocument();
  });

  test("shows the models of the capability now selected, never those of one it replaced (slow answer last)", async () => {
    let answerImage!: (r: Response) => void;
    const slowImage = new Promise<Response>((resolve) => (answerImage = resolve));
    const catalog = (category: string, modelName: string) =>
      ({ ok: true, json: async () => [{ modelName, modelLabel: modelName, category }] }) as unknown as Response;
    global.fetch = jest.fn().mockReturnValueOnce(slowImage).mockResolvedValue(catalog("video", "fast-video"));
    const video: ComposerStudio = { ...imageGen, capabilityKey: "orazaka.core.media.video" };
    const view = renderOpts(imageGen);
    view.rerender(
      <CapabilityOptions
        studio={video}
        options={{}}
        onChange={jest.fn()}
        attachment={null}
        onAttach={jest.fn()}
        t={t}
      />,
    );
    await waitFor(() => expect(screen.getByRole("option", { name: "fast-video" })).toBeInTheDocument());
    answerImage(catalog("image", "slow-image"));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole("option", { name: "slow-image" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: "fast-video" })).toBeInTheDocument();
  });
});
