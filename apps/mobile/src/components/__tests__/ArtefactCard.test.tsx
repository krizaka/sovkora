import React from "react";
import { render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { ArtefactCard } from "../ArtefactCard";
import { Markdown } from "../Markdown";

const mockPlayer = { loop: false, play: jest.fn() };
const mockSources: unknown[] = [];
jest.mock("expo-video", () => {
  const { View } = jest.requireActual("react-native");
  return {
    useVideoPlayer: (source: unknown, setup: (p: unknown) => void) => {
      mockSources.push(source);
      setup(mockPlayer);
      return mockPlayer;
    },
    VideoView: (props: object) => <View testID="video-view" {...props} />,
  };
});

const wrap = (ui: React.ReactElement) =>
  render(
    <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
      {ui}
    </ThemeProvider>,
  );

describe("Markdown", () => {
  it("shows no literal ** and keeps the words", async () => {
    await wrap(<Markdown text={"# Prospect\n\n**Nom :** Atelier Morel\n\n- chaud\n- à rappeler"} />);
    expect(screen.getByText("Prospect")).toBeTruthy();
    expect(screen.queryByText(/\*\*/)).toBeNull();
    expect(screen.getByText("Nom :")).toBeTruthy();
    expect(screen.getByText("à rappeler")).toBeTruthy();
  });
});

describe("ArtefactCard", () => {
  it("plays a VIDEO artefact with the Bearer on the request, started, with native controls", async () => {
    mockSources.length = 0;
    await wrap(
      <ArtefactCard
        token="tok"
        artefact={{ key: "clip", label: "Clip", type: "VIDEO", value: "/api/v1/assets/j1/video.mp4" }}
      />,
    );
    expect(screen.getByTestId("video-view").props.nativeControls).toBe(true);
    expect(mockPlayer.play).toHaveBeenCalled();
    expect(mockSources[0]).toEqual({
      uri: "https://www.orazaka.com/edge/api/v1/assets/j1/video.mp4",
      headers: { Authorization: "Bearer tok" },
    });
    // The path is not printed as text any more.
    expect(screen.queryByText("/api/v1/assets/j1/video.mp4")).toBeNull();
  });

  it("shows an IMAGE artefact as a picture", async () => {
    await wrap(
      <ArtefactCard
        token="tok"
        artefact={{ key: "img", label: "Photo", type: "IMAGE", value: "/api/v1/assets/j2/lamp.png" }}
      />,
    );
    expect(screen.getByLabelText("Photo").props.source).toEqual({
      uri: "https://www.orazaka.com/edge/api/v1/assets/j2/lamp.png",
      headers: { Authorization: "Bearer tok" },
    });
  });

  it("draws a rules report as cards, failures first, and a model's text as Markdown with its facts apart", async () => {
    const report = JSON.stringify({
      findings: [
        { ruleId: "R1", title: "Durée", verdict: "PASS" },
        { ruleId: "R2", title: "Dépôt", verdict: "FAIL", explanation: "trop élevé" },
      ],
    });
    await wrap(<ArtefactCard token={null} artefact={{ key: "r", label: "Constats", type: "TEXT", value: report }} />);
    expect(screen.getByText("Dépôt")).toBeTruthy();
    expect(screen.getByText("trop élevé")).toBeTruthy();

    await wrap(
      <ArtefactCard
        token={null}
        artefact={{
          key: "t",
          label: "Note",
          type: "TEXT",
          value: JSON.stringify({ content: "**Nom :** Morel", provider: "ollama" }),
        }}
      />,
    );
    expect(screen.getByText("Nom :")).toBeTruthy();
    expect(screen.getByText("Provider · On your server")).toBeTruthy();
  });
});
