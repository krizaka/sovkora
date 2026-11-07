import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { RunForm } from "@/features/studio/components/RunForm";
import { MediaApi } from "@/services/media.api";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});
jest.mock("@/services/media.api", () => ({ MediaApi: { uploadMedia: jest.fn() } }));

const upload = MediaApi.uploadMedia as jest.Mock;

const schema = JSON.stringify({
  type: "object",
  required: ["prompt"],
  properties: {
    prompt: { type: "string", title: "Ce qui doit se passer à l'écran" },
    image: {
      type: "string",
      format: "asset:image",
      maxLength: 120,
      title: "Image d'amorce (facultatif)",
      description: "Choisissez…",
    },
  },
});

const setup = () => {
  const onSubmit = jest.fn();
  const view = render(
    <RunForm
      studioKey="video-generation"
      inputSchema={schema}
      estimatedCredits={3}
      isSubmitting={false}
      onSubmit={onSubmit}
    />,
  );
  return { onSubmit, ...view };
};
const photo = () => new File(["x"], "me.png", { type: "image/png" });
const pick = (container: HTMLElement, file: File) =>
  fireEvent.change(container.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [file] } });

beforeAll(() => {
  URL.createObjectURL = jest.fn(() => "blob:preview");
  URL.revokeObjectURL = jest.fn();
});
beforeEach(() => upload.mockReset());

describe("RunForm — an image input is a picker", () => {
  it("shows the field in English and a file picker, never a text field for the asset id", () => {
    const { container } = setup();
    expect(screen.getByText("Starting image (optional)")).toBeInTheDocument();
    expect(screen.getByText("What should happen on screen *")).toBeInTheDocument();
    expect(container.querySelector('input[type="file"]')).not.toBeNull();
    expect(container.querySelectorAll('input[type="text"]')).toHaveLength(1); // the prompt only
    expect(screen.getByRole("button", { name: "Choose a photo" })).toBeInTheDocument();
  });

  it("uploads the chosen photo and submits the asset id it gets back", async () => {
    upload.mockResolvedValue({ assetId: "asset-42" });
    const { container, onSubmit } = setup();
    fireEvent.change(screen.getByLabelText(/What should happen/), { target: { value: "a fox" } });
    pick(container, photo());
    await waitFor(() => expect(container.querySelector("[data-asset-id='asset-42']")).not.toBeNull());
    expect(upload).toHaveBeenCalledWith(expect.objectContaining({ name: "me.png" }));
    expect(screen.getByText("me.png")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox", image: "asset-42" });
  });

  it("holds the run while the photo is being sent", async () => {
    let finish: (v: { assetId: string }) => void = () => {};
    upload.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { container } = setup();
    fireEvent.change(screen.getByLabelText(/What should happen/), { target: { value: "a fox" } });
    pick(container, photo());
    await waitFor(() => expect(screen.getByRole("button", { name: "Run" })).toBeDisabled());
    finish({ assetId: "asset-1" });
    await waitFor(() => expect(screen.getByRole("button", { name: "Run" })).toBeEnabled());
  });

  it("removes the photo and submits without it", async () => {
    upload.mockResolvedValue({ assetId: "asset-42" });
    const { container, onSubmit } = setup();
    fireEvent.change(screen.getByLabelText(/What should happen/), { target: { value: "a fox" } });
    pick(container, photo());
    await waitFor(() => expect(container.querySelector("[data-asset-id='asset-42']")).not.toBeNull());
    fireEvent.click(screen.getByRole("button", { name: "Remove me.png" }));
    expect(container.querySelector('input[type="file"]')).not.toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox" });
  });

  it("says so when the upload fails, and sends nothing", async () => {
    upload.mockRejectedValue(new Error("boom"));
    const { container, onSubmit } = setup();
    fireEvent.change(screen.getByLabelText(/What should happen/), { target: { value: "a fox" } });
    pick(container, photo());
    await waitFor(() => expect(screen.getByText("The upload failed. Try again.")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onSubmit).toHaveBeenCalledWith({ prompt: "a fox" });
  });

  it("refuses a file that is not an image", () => {
    const { container } = setup();
    pick(container, new File(["x"], "doc.pdf", { type: "application/pdf" }));
    expect(screen.getByRole("alert")).toHaveTextContent("doc.pdf is not a supported file type.");
    expect(upload).not.toHaveBeenCalled();
  });
});
