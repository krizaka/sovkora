import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { CategoryCard } from "@/app/dashboard/admin/CategoryCard";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      admin: {
        optSpeech: "Speech",
        optImage: "Image",
        optVideo: "Video",
        optVision: "Vision",
        optAudio: "Audio",
        optTheme: "Theme",
        optCode: "Code",
        modelsSuffix: "Models",
        noModels: "No models registered",
        activeDefault: "Default",
        optionsPrefix: "Options: ",
        editModelTitle: "Edit",
        deleteModelTitle: "Delete",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/core/constants/capability.constants", () => ({
  MODEL_CATEGORY: {
    SPEECH: "speech",
    IMAGE: "image",
    VIDEO: "video",
    VISION: "vision",
    AUDIO: "audio",
  },
}));

describe("CategoryCard", () => {
  const onEdit = jest.fn();
  const onDelete = jest.fn();

  afterEach(() => jest.clearAllMocks());

  it("renders category heading with model count", () => {
    const models = [{ id: 1, modelName: "gpt-4o", modelLabel: "GPT-4o", category: "text" }];
    render(<CategoryCard category="text" models={models} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText("1")).toBeInTheDocument();
  });

  it("shows empty state when no models", () => {
    render(<CategoryCard category="text" models={[]} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText("No models registered")).toBeInTheDocument();
  });

  it("renders model items with label and name", () => {
    const models = [{ id: 1, modelName: "llama3:latest", modelLabel: "Llama 3", category: "text" }];
    render(<CategoryCard category="text" models={models} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText("Llama 3")).toBeInTheDocument();
    expect(screen.getByText("llama3:latest")).toBeInTheDocument();
  });

  it("shows default badge for default model", () => {
    const models = [{ id: 1, modelName: "gpt-4o", modelLabel: "GPT-4o", category: "text", isDefault: true }];
    render(<CategoryCard category="text" models={models} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText("Default")).toBeInTheDocument();
  });

  it("shows options when present", () => {
    const models = [{ id: 1, modelName: "tts-1", modelLabel: "TTS-1", category: "speech", options: "voice1,voice2" }];
    render(<CategoryCard category="speech" models={models} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText(/voice1,voice2/)).toBeInTheDocument();
  });

  it("calls onEdit when edit button clicked", () => {
    const models = [{ id: 1, modelName: "gpt-4o", modelLabel: "GPT-4o", category: "text" }];
    render(<CategoryCard category="text" models={models} onEdit={onEdit} onDelete={onDelete} />);
    fireEvent.click(screen.getByTitle("Edit"));
    expect(onEdit).toHaveBeenCalledWith(models[0]);
  });

  it("calls onDelete when delete button clicked", () => {
    const models = [{ id: 1, modelName: "gpt-4o", modelLabel: "GPT-4o", category: "text" }];
    render(<CategoryCard category="text" models={models} onEdit={onEdit} onDelete={onDelete} />);
    fireEvent.click(screen.getByTitle("Delete"));
    expect(onDelete).toHaveBeenCalledWith(1);
  });

  it("renders speech category label", () => {
    render(<CategoryCard category="speech" models={[]} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText(/Speech/)).toBeInTheDocument();
  });

  it("renders image category label", () => {
    render(<CategoryCard category="image" models={[]} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText(/Image/)).toBeInTheDocument();
  });

  it("renders custom category as-is when not mapped", () => {
    render(<CategoryCard category="custom" models={[]} onEdit={onEdit} onDelete={onDelete} />);
    expect(screen.getByText(/custom/)).toBeInTheDocument();
  });
});
