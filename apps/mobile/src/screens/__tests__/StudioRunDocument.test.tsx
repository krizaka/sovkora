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

// The phone's file picker and file system are native: stand-ins that "pick" the file a test names and read its bytes.
const mockPicked: { current: { name: string; mimeType: string; size: number; base64: string } | null } = {
  current: null,
};
jest.mock("expo-document-picker", () => ({
  getDocumentAsync: async () =>
    mockPicked.current === null
      ? { canceled: true, assets: null }
      : {
          canceled: false,
          assets: [
            {
              uri: "file:///cache/doc",
              name: mockPicked.current.name,
              mimeType: mockPicked.current.mimeType,
              size: mockPicked.current.size,
            },
          ],
        },
}));
jest.mock("expo-file-system", () => ({
  EncodingType: { Base64: "base64" },
  readAsStringAsync: async () => mockPicked.current?.base64 ?? "",
}));
const toBase64 = (text: string) => Buffer.from(text, "latin1").toString("base64");

const finished = {
  id: "run-1",
  installationId: "inst-1",
  studioKey: "document-authenticity",
  blueprintVersion: "1.0.0",
  status: "SUCCEEDED",
  startedAt: "2026-10-15T10:00:00Z",
  finishedAt: "2026-10-15T10:01:00Z",
  errorMessage: null,
  steps: [{ stepId: "check", ordinal: 0, status: "SUCCEEDED" }],
  outputs: [{ key: "clip", label: "Clip", type: "TEXT", value: "**Plan :** ok" }],
};

describe("StudioRunScreen — a document is picked from the phone's files and checked before the run costs anything", () => {
  const schema = JSON.stringify({
    required: ["documentBase64"],
    properties: { documentBase64: { type: "string", maxLength: 1_400_000, title: "Le fichier PDF" } },
  });
  const open = async () => {
    const calls = answerFetch({
      "/api/v1/studios/document-authenticity/runs": finished,
      "/api/v1/studios/document-authenticity": { inputSchema: schema },
    });
    const route = { params: { installationId: null, label: "Authenticité", studioKey: "document-authenticity" } };
    await render(
      <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
        <AuthProvider>
          <StudioRunScreen route={route as never} navigation={{} as never} />
        </AuthProvider>
      </ThemeProvider>,
    );
    return calls;
  };
  const goodPdf = "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\nstartxref\n9\n%%EOF\n";

  it("shows a picker, not a text box for the base64; a good PDF starts the run with its base64", async () => {
    mockPicked.current = {
      name: "lease.pdf",
      mimeType: "application/pdf",
      size: goodPdf.length,
      base64: toBase64(goodPdf),
    };
    const calls = await open();
    expect(await screen.findByText("Choose a PDF")).toBeTruthy();
    expect(screen.queryByTestId("field-documentBase64-input")).toBeNull();
    await fireEvent.press(screen.getByText("Choose a PDF"));
    expect(await screen.findByText(/lease\.pdf is ready/)).toBeTruthy();
    await waitFor(() => expect(screen.getByTestId("run-launch").props.accessibilityState?.disabled).toBeFalsy());
    await fireEvent.press(screen.getByText("Launch"));
    expect(await screen.findByText("Plan :", {}, { timeout: 5000 })).toBeTruthy();
    expect(calls.find((c) => c.path.endsWith("/runs") && c.method === "POST")?.body).toEqual({
      inputs: { documentBase64: toBase64(goodPdf) },
    });
  }, 20000);

  it("refuses a program renamed .pdf, says why in words, and sends nothing", async () => {
    mockPicked.current = {
      name: "lease.pdf",
      mimeType: "application/pdf",
      size: 40,
      base64: toBase64("MZ\u0090\u0000 not a document at all"),
    };
    const calls = await open();
    await fireEvent.press(await screen.findByText("Choose a PDF"));
    expect(await screen.findByText("This file is a program, not a document.")).toBeTruthy();
    expect(calls.some((c) => c.method === "POST")).toBe(false);
  });

  it("refuses a file too large for the field before reading it", async () => {
    mockPicked.current = { name: "scan.pdf", mimeType: "application/pdf", size: 300 * 1024 * 1024, base64: "" };
    await open();
    await fireEvent.press(await screen.findByText("Choose a PDF"));
    expect(await screen.findByText(/too large for this check \(1 MB at most\)/)).toBeTruthy();
  });
});
