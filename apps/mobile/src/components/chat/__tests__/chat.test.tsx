import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { ChatMeta } from "../ChatMeta";
import { AssistantMessage } from "../AssistantMessage";
import { PipelineChips } from "../PipelineChips";
import type { CatalogModel } from "../../../core/sovereignty";

const wrap = (ui: React.ReactElement) =>
  render(
    <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
      {ui}
    </ThemeProvider>,
  );

const hosted: CatalogModel = {
  modelName: "mistral-3-14B",
  modelLabel: "Mistral 3 14B",
  category: "chat",
  providerName: "do-inference",
  isDefault: true,
};
const selfHosted: CatalogModel = {
  modelName: "qwen2.5:7b",
  modelLabel: "Qwen 2.5 7B",
  category: "chat",
  providerName: "ollama",
};

describe("the line under the chat: which model, and what the network saw", () => {
  it("names the model that answers and opens the choice", async () => {
    const pick = jest.fn();
    await wrap(<ChatMeta model={hosted} onPickModel={pick} />);
    await fireEvent.press(screen.getByText("Mistral 3 14B"));
    expect(pick).toHaveBeenCalled();
  });

  it("does not claim that nothing left the network when the model is a hosted one", async () => {
    await wrap(<ChatMeta model={hosted} onPickModel={() => undefined} />);
    expect(screen.queryByText(/left your network/)).toBeNull();
  });

  it("says it, because it is computed true, for a self-hosted model", async () => {
    await wrap(<ChatMeta model={selfHosted} onPickModel={() => undefined} />);
    expect(screen.getByText("0 bytes left your network")).toBeTruthy();
  });

  it("shows nothing while the catalogue has not answered", async () => {
    await wrap(<ChatMeta model={null} onPickModel={() => undefined} />);
    expect(screen.queryByText(/network/)).toBeNull();
  });
});

describe("the answer and the pipeline that really ran", () => {
  const base = { id: "a", role: "assistant" as const, planned: [], steps: [], settled: false };

  it("shows the typing indicator until the first word arrives", async () => {
    await wrap(<AssistantMessage message={{ ...base, content: "" }} />);
    expect(screen.getByLabelText("Sovkora is answering")).toBeTruthy();
  });

  it("renders the answer as Markdown and one chip per interceptor that finished, in words", async () => {
    await wrap(
      <AssistantMessage
        message={{
          ...base,
          content: "**Hello** there",
          steps: ["UserContextResolver", "SafetyInterceptor", "BrandNewInterceptor"],
          settled: true,
        }}
      />,
    );
    expect(screen.getByText("Hello")).toBeTruthy();
    expect(screen.queryByText(/\*\*/)).toBeNull();
    expect(screen.getByText("User context")).toBeTruthy();
    expect(screen.getByText("Safety")).toBeTruthy();
    // An interceptor the catalogue has no word for is spaced from its id, never shown as a code.
    expect(screen.getByText("Brand New")).toBeTruthy();
  });

  it("draws no chip before a step has finished", async () => {
    await wrap(<PipelineChips steps={[]} />);
    expect(screen.toJSON()).toBeNull();
  });
});
