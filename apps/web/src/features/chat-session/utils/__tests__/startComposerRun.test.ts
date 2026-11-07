import type { ComposerStudio } from "@krizaka/orazaka-shared";
import { startComposerRun } from "@/features/chat-session/utils/startComposerRun";
import { StudioApi } from "@/services/studio.api";

jest.mock("@/services/studio.api", () => ({
  StudioApi: { startStudioRun: jest.fn().mockResolvedValue({ id: "run-1" }) },
}));

const startStudioRun = StudioApi.startStudioRun as jest.Mock;

const studio = (overrides: Partial<ComposerStudio>): ComposerStudio => ({
  studioKey: "image-generation",
  label: "Image",
  iconKey: "image",
  version: "1.0.0",
  capabilityKey: "orazaka.core.media.image",
  inputKey: "prompt",
  inputKind: "TEXT",
  promptKey: "prompt",
  available: true,
  lockedReason: "NONE",
  ...overrides,
});

/**
 * What the composer sends, and where it sends it (ADR-068 §4).
 *
 * The deleted `executeFeature` compiled a payload template the server had handed it and
 * POSTed to a URI the server had handed it. These tests pin the replacement: a Studio key
 * and inputs assembled from what the Studio DECLARED it wants — never from its name.
 */
describe("startComposerRun", () => {
  afterEach(() => jest.clearAllMocks());

  it("starts a run of the Studio, never a call to a capability endpoint", async () => {
    await startComposerRun({ studio: studio({}), prompt: "un atelier au petit matin" });

    expect(startStudioRun).toHaveBeenCalledWith("image-generation", {
      prompt: "un atelier au petit matin",
    });
  });

  it("puts the attachment in the input the Studio says holds one", async () => {
    await startComposerRun({
      studio: studio({
        studioKey: "audio-analysis",
        inputKey: "assetId",
        inputKind: "ASSET",
        promptKey: null,
      }),
      prompt: "ignored: this schema has nowhere to put prose",
      assetId: "a1b2c3d4-0000-0000-0000-0000000000a2",
    });

    expect(startStudioRun).toHaveBeenCalledWith("audio-analysis", {
      assetId: "a1b2c3d4-0000-0000-0000-0000000000a2",
    });
  });

  it("keeps the question a user typed about an attachment, where the schema declares it", async () => {
    await startComposerRun({
      studio: studio({
        studioKey: "image-analysis",
        inputKey: "assetId",
        inputKind: "ASSET",
        promptKey: "prompt",
      }),
      prompt: "combien de fenêtres ?",
      assetId: "asset-1",
    });

    expect(startStudioRun).toHaveBeenCalledWith("image-analysis", {
      assetId: "asset-1",
      prompt: "combien de fenêtres ?",
    });
  });

  it("sends an optional input only when the user chose one", async () => {
    await startComposerRun({
      studio: studio({}),
      prompt: "une façade",
      options: { size: "1024x1536", model: "", voice: undefined },
    });

    // An empty string is not "use your default" to a JSON Schema: it is a value, and
    // it would fail validation or override the catalogue's model with nothing.
    expect(startStudioRun).toHaveBeenCalledWith("image-generation", {
      prompt: "une façade",
      size: "1024x1536",
    });
  });
});
