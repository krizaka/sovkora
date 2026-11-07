import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AuthProvider } from "../../core/AuthContext";
import { StudioRunScreen } from "../StudioRunScreen";
import { answerFetch } from "../../__tests__/test-api";
import mockLogin from "../../__fixtures__/api/auth-login.local-demo.json";

jest.mock("expo-secure-store", () => ({
  getItemAsync: async () => JSON.stringify(mockLogin),
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

// The picker is the kit's (tested there) and needs the device's photo library: a stand-in that "picks" one photo.
jest.mock("@krizaka/ui/native/media-picker", () => {
  const { Pressable, Text } = jest.requireActual("react-native");
  const photo = { uri: "file:///photos/me.png", kind: "image", width: 800, height: 600, fileName: "me.png" };
  return {
    MediaPicker: ({
      value,
      onValueChange,
      labels,
    }: {
      value: unknown[];
      onValueChange: (m: unknown[]) => void;
      labels: { add: string; remove: () => string };
    }) => (
      <>
        <Pressable accessibilityRole="button" onPress={() => onValueChange([photo])}>
          <Text>{labels.add}</Text>
        </Pressable>
        {value.length > 0 && (
          <Pressable accessibilityRole="button" onPress={() => onValueChange([])}>
            <Text>{labels.remove()}</Text>
          </Pressable>
        )}
      </>
    ),
  };
});

const inputSchema = JSON.stringify({
  required: ["prompt"],
  properties: {
    prompt: { type: "string", title: "Prompt" },
    aspect: { type: "string", title: "Format", enum: ["9:16", "16:9"], default: "16:9" },
    seed: { type: "integer", title: "Seed" },
  },
});

const finished = {
  id: "run-1",
  installationId: "inst-1",
  studioKey: "video-generation",
  blueprintVersion: "1.3.0",
  status: "SUCCEEDED",
  startedAt: "2026-10-15T10:00:00Z",
  finishedAt: "2026-10-15T10:01:00Z",
  errorMessage: null,
  steps: [{ stepId: "render", ordinal: 0, status: "SUCCEEDED" }],
  outputs: [
    { key: "clip", label: "Clip", type: "VIDEO", value: "/api/v1/assets/job-1/video.mp4" },
    { key: "note", label: "Note", type: "TEXT", value: "**Plan :** une lampe" },
  ],
};

describe("StudioRunScreen — start, then the result as the phone should show it", () => {
  it("offers the enum as a choice, sends it with the seed as a number, then plays the clip and renders the note", async () => {
    const calls = answerFetch({
      "/api/v1/studios/video-generation": { inputSchema },
      "/api/v1/studios/installations/inst-1/runs": finished,
    });
    const route = { params: { installationId: "inst-1", label: "Vidéo", studioKey: "video-generation" } };
    await render(
      <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
        <AuthProvider>
          <StudioRunScreen route={route as never} navigation={{} as never} />
        </AuthProvider>
      </ThemeProvider>,
    );
    await fireEvent.changeText((await screen.findAllByDisplayValue(""))[0]!, "une lampe en chêne");
    await fireEvent.press(screen.getByText("Vertical 9:16"));
    await fireEvent.press(screen.getByText("Launch"));

    expect(await screen.findByLabelText("Clip")).toBeTruthy();
    expect(screen.getByText("Plan :")).toBeTruthy();
    expect(screen.queryByText(/\*\*/)).toBeNull();
    const start = calls.find((c) => c.method === "POST");
    expect(start?.body).toEqual({ inputs: { prompt: "une lampe en chêne", aspect: "9:16" } });
  });

  it("starts a TOOLKIT Studio (no installation) on its own key", async () => {
    const calls = answerFetch({
      "/api/v1/studios/video-generation": { inputSchema },
      "/api/v1/studios/video-generation/runs": finished,
    });
    const route = { params: { installationId: null, label: "Vidéo", studioKey: "video-generation" } };
    await render(
      <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
        <AuthProvider>
          <StudioRunScreen route={route as never} navigation={{} as never} />
        </AuthProvider>
      </ThemeProvider>,
    );
    await fireEvent.changeText((await screen.findAllByDisplayValue(""))[0]!, "une lampe");
    await fireEvent.press(screen.getByText("Launch"));
    expect(await screen.findByLabelText("Clip")).toBeTruthy();
    expect(calls.find((c) => c.method === "POST")?.path).toBe("/api/v1/studios/video-generation/runs");
  });

  it("picks a photo for the starting image, uploads it, and starts the run with its asset id — never a typed id", async () => {
    const schema = JSON.stringify({
      required: ["prompt"],
      properties: {
        prompt: { type: "string", title: "Prompt" },
        image: { type: "string", format: "asset:image", title: "Image d'amorce (facultatif)" },
      },
    });
    const calls = answerFetch({
      "/api/v1/studios/video-generation/runs": finished,
      "/api/v1/studios/video-generation": { inputSchema: schema },
      "/api/v1/media/upload": { assetId: "asset-9" },
    });
    const route = { params: { installationId: null, label: "Vidéo", studioKey: "video-generation" } };
    await render(
      <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
        <AuthProvider>
          <StudioRunScreen route={route as never} navigation={{} as never} />
        </AuthProvider>
      </ThemeProvider>,
    );
    await fireEvent.changeText((await screen.findAllByDisplayValue(""))[0]!, "une lampe");
    await fireEvent.press(screen.getByText("More options"));
    expect(screen.getByText("Starting image (optional)")).toBeTruthy();
    expect(screen.queryByTestId("field-image-input")).toBeNull(); // no text field for the asset id
    await fireEvent.press(screen.getByText("Add a photo"));
    expect(await screen.findByText("Remove the photo")).toBeTruthy();
    // The run waits for the photo to be sent: pressing Launch before the upload ends does nothing (slow CI raced it).
    await waitFor(() => expect(calls.some((c) => c.path === "/api/v1/media/upload")).toBe(true));
    await waitFor(() => expect(screen.getByTestId("run-launch").props.accessibilityState?.disabled).toBeFalsy());
    await fireEvent.press(screen.getByText("Launch"));
    expect(await screen.findByLabelText("Clip", {}, { timeout: 5000 })).toBeTruthy();
    expect(calls.some((c) => c.path === "/api/v1/media/upload" && c.method === "POST")).toBe(true);
    expect(calls.find((c) => c.path.endsWith("/runs") && c.method === "POST")?.body).toEqual({
      inputs: { prompt: "une lampe", image: "asset-9" },
    });
  });

  describe("closed sets are picked from a list, never typed", () => {
    const imageSchema = JSON.stringify({
      required: ["prompt"],
      properties: {
        prompt: { type: "string", title: "Prompt" },
        model: {
          type: "string",
          title: "Model",
          "x-advanced": true,
          "x-options": { source: "models", category: "image" },
          default: "sd-hosted",
          oneOf: [
            {
              const: "sd-hosted",
              title: "Stable Diffusion 3.5 Large",
              description: "Hosted.",
              "x-meta": { runsOn: "cloud", speed: "fast", typicalSeconds: 8, priceCents: 8, available: true },
            },
            {
              const: "z-image",
              title: "Z-Image Turbo",
              "x-meta": { runsOn: "local", speed: "slow", typicalSeconds: 157, available: true },
            },
            {
              const: "flux",
              title: "FLUX.2 [klein] 4B",
              "x-meta": { runsOn: "local", available: false, unavailableReason: "FLUX.2 is not running" },
            },
          ],
        },
        seed: { type: "string", title: "Seed", "x-control": "seed", "x-advanced": true },
      },
    });

    const open = async (
      calls = answerFetch({
        "/api/v1/studios/image-generation": { inputSchema: imageSchema },
        "/api/v1/studios/image-generation/runs": finished,
      }),
    ) => {
      const route = { params: { installationId: null, label: "Image", studioKey: "image-generation" } };
      await render(
        <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
          <AuthProvider>
            <StudioRunScreen route={route as never} navigation={{} as never} />
          </AuthProvider>
        </ThemeProvider>,
      );
      await fireEvent.changeText((await screen.findAllByDisplayValue(""))[0]!, "a fox");
      await fireEvent.press(screen.getByText("More options"));
      return calls;
    };

    it("shows the model as a picker set to automatic, naming the model that will run, and sends none", async () => {
      const calls = await open();
      expect(screen.getByText("Automatic · Stable Diffusion 3.5 Large")).toBeTruthy();
      await fireEvent.press(screen.getByText("Launch"));
      expect(await screen.findByLabelText("Clip")).toBeTruthy();
      expect(calls.find((c) => c.method === "POST")?.body).toEqual({ inputs: { prompt: "a fox" } });
    });

    it("opens the list with place, speed, time and price, then sends the model that was picked", async () => {
      const calls = await open();
      await fireEvent.press(screen.getByText("Automatic · Stable Diffusion 3.5 Large"));
      expect(await screen.findByText("Cloud · Fast · about 8 s · $0.08 per run")).toBeTruthy();
      expect(screen.getByText("Local · Slow · about 157 s · Free")).toBeTruthy();
      expect(screen.getByText("FLUX.2 is not running")).toBeTruthy();
      await fireEvent.press(screen.getByText("Z-Image Turbo"));
      await fireEvent.press(screen.getByText("Launch"));
      expect(await screen.findByLabelText("Clip")).toBeTruthy();
      expect(calls.find((c) => c.method === "POST")?.body).toEqual({ inputs: { prompt: "a fox", model: "z-image" } });
    });

    it("has a seed that is random by default, and a number once random is turned off", async () => {
      const calls = await open();
      expect(screen.getByLabelText("Random")).toBeTruthy();
      await fireEvent.press(screen.getByLabelText("Random"));
      await fireEvent.changeText(screen.getByPlaceholderText("Seed number"), "42x7");
      await fireEvent.press(screen.getByText("Launch"));
      expect(await screen.findByLabelText("Clip")).toBeTruthy();
      expect(calls.find((c) => c.method === "POST")?.body).toEqual({ inputs: { prompt: "a fox", seed: "427" } });
    });
  });
});
