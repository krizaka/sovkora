import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AuthProvider } from "../core/AuthContext";
import { ChatStreamScreen } from "../screens/ChatStreamScreen";
import { answerFetch } from "./test-api";
import login from "../__fixtures__/api/auth-login.local-demo.json";

const mockLogin = login;
jest.mock("expo-secure-store", () => ({
  getItemAsync: async () => JSON.stringify(mockLogin),
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

class FakeXhr {
  static last: FakeXhr | null = null;
  readyState = 0;
  status = 0;
  responseText = "";
  onreadystatechange: (() => void) | null = null;
  onerror: (() => void) | null = null;
  body: string | null = null;
  constructor() {
    FakeXhr.last = this;
  }
  open(): void {}
  setRequestHeader(): void {}
  send(body: string): void {
    this.body = body;
  }
  abort(): void {}
  push(chunk: string): void {
    this.readyState = 3;
    this.responseText += chunk;
    this.onreadystatechange?.();
  }
  finish(status = 200): void {
    this.readyState = 4;
    this.status = status;
    this.onreadystatechange?.();
  }
}

const catalog = [
  {
    modelName: "mistral-3-14B",
    modelLabel: "Mistral 3 14B",
    category: "chat",
    providerName: "do-inference",
    isDefault: true,
    compatible: true,
  },
  { modelName: "qwen2.5:7b", modelLabel: "Qwen 2.5 7B", category: "chat", providerName: "ollama", compatible: true },
];

const chat = () =>
  render(
    <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
      <AuthProvider>
        <ChatStreamScreen />
      </AuthProvider>
    </ThemeProvider>,
  );

beforeEach(() => {
  FakeXhr.last = null;
  (globalThis as { XMLHttpRequest: unknown }).XMLHttpRequest = FakeXhr;
  answerFetch({ "/api/v1/models/catalog": catalog });
});

describe("the chat screen — one real chat", () => {
  it("opens on the kit's empty state with things to try, not on a decorative exchange", async () => {
    await chat();
    expect(await screen.findByText("What can I do for you?")).toBeTruthy();
    expect(screen.getByText("Summarise a contract and flag the risks")).toBeTruthy();
    // The old showcase card (a fake exchange, a fake pipeline) is gone, and there is exactly one composer.
    expect(screen.queryByText(/sovereign AI, on this device/i)).toBeNull();
    expect(screen.queryByText(/Nothing leaves your network/i)).toBeNull();
    expect(screen.getAllByLabelText("Message Sovkora")).toHaveLength(1);
  });

  it("opens on the Sovkora mark, never the code-named one", async () => {
    await chat();
    expect(await screen.findByTestId("sovkora-mark", { includeHiddenElements: true })).toBeTruthy();
    expect(screen.queryByTestId("orazaka-mark", { includeHiddenElements: true })).toBeNull();
  });

  it("says which model answers, and nothing about the network while that model is a hosted one", async () => {
    await chat();
    expect(await screen.findByText("Mistral 3 14B")).toBeTruthy();
    expect(screen.queryByText(/left your network/)).toBeNull();
  });

  it("sends a suggestion, streams the answer and the chips of the interceptors that really ran", async () => {
    await chat();
    await fireEvent.press(await screen.findByText("Draft a polite reply to a customer"));

    const xhr = FakeXhr.last;
    expect(xhr).not.toBeNull();
    expect(JSON.parse(xhr?.body ?? "{}")).toMatchObject({ prompt: "Draft a polite reply to a customer", model: null });

    xhr?.push(
      'event:pipeline-ack\ndata:{"coreInterceptorIds":["UserContextResolver","SafetyInterceptor"],"dynamicInterceptorIds":["RouterInterceptor"]}\n\n',
    );
    xhr?.push('event:pipeline-step\ndata:{"interceptorId":"UserContextResolver"}\n\n');
    xhr?.push('event:pipeline-step\ndata:{"interceptorId":"SafetyInterceptor"}\n\n');
    xhr?.push('event:message\ndata:{"content":"Dear **customer**,"}\n\n');
    xhr?.finish(200);

    expect(await screen.findByText("Dear")).toBeTruthy();
    expect(screen.getByText("User context")).toBeTruthy();
    expect(screen.getByText("Safety")).toBeTruthy();
    // Only the steps that finished are chips: the router had not reported.
    expect(screen.queryByText("Router")).toBeNull();
  });

  it("says so when the stream ends without a word", async () => {
    await chat();
    await fireEvent.press(await screen.findByText("Draft a polite reply to a customer"));
    FakeXhr.last?.finish(200);
    await waitFor(() => expect(screen.getByText("No answer came back. Try again or pick another model.")).toBeTruthy());
  });
});
