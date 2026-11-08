import "@testing-library/jest-dom";
import * as React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { ModelDialog } from "@/app/dashboard/admin/ModelDialog";
import { CatalogModel } from "@/app/dashboard/admin/CategoryCard";

jest.mock("@/core/constants/capability.constants", () => ({
  MODEL_CATEGORY: {
    SPEECH: "speech",
    IMAGE: "image",
    VIDEO: "video",
    VISION: "vision",
    AUDIO: "audio",
  },
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      a11y: jest.requireActual("@/core/context/translations.automation").a11y.en,
      admin: {
        editModelTitle: "Edit Model",
        addModelTitle: "Add Model",
        optSpeech: "Speech",
        optImage: "Image",
        optVideo: "Video",
        optVision: "Vision",
        optAudio: "Audio",
        optTheme: "Theme",
        optCode: "Code",
        categoryLabel: "Category",
        helpCategory: "Help Category",
        providerLabel: "Provider",
        helpProvider: "Help Provider",
        modelNameLabel: "Model Name",
        helpModelName: "Help Model Name",
        placeholderModelName: "Placeholder Model Name",
        modelLabelLabel: "Model Label",
        helpModelLabel: "Help Model Label",
        placeholderModelLabel: "Placeholder Model Label",
        optionsLabel: "Options",
        helpOptions: "Help Options",
        placeholderOptions: "Placeholder Options",
        setAsDefaultLabel: "Set as Default",
        helpDefault: "Help Default",
        cancel: "Cancel",
        saveChanges: "Save Changes",
        createModel: "Create Model",
      },
    },
    locale: "en",
  }),
}));

const mockModel: CatalogModel = {
  id: 1,
  category: "speech",
  providerName: "ollama",
  modelName: "tts-model",
  modelLabel: "TTS Model",
  options: "temp=0.7",
  isDefault: true,
};

describe("ModelDialog", () => {
  const defaultProps = {
    isOpen: true,
    onClose: jest.fn(),
    formModel: mockModel,
    setFormModel: jest.fn(),
    formMode: "edit" as const,
    saving: false,
    onSubmit: jest.fn((e) => e.preventDefault()),
    errorMessage: null as string | null,
    setErrorMessage: jest.fn(),
    providers: ["ollama", "openai", "replicate"],
  };

  afterEach(() => jest.clearAllMocks());

  it("does not render when isOpen is false", () => {
    const { container } = render(<ModelDialog {...defaultProps} isOpen={false} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders Edit mode header and form fields with values", () => {
    render(<ModelDialog {...defaultProps} />);
    expect(screen.getByText("Edit Model")).toBeInTheDocument();
    expect(screen.getByDisplayValue("tts-model")).toBeInTheDocument();
    expect(screen.getByDisplayValue("TTS Model")).toBeInTheDocument();
    expect(screen.getByDisplayValue("temp=0.7")).toBeInTheDocument();
    expect(screen.getByRole("checkbox")).toBeChecked();
  });

  it("renders Create mode header", () => {
    render(<ModelDialog {...defaultProps} formMode="create" />);
    expect(screen.getByText("Add Model")).toBeInTheDocument();
    expect(screen.getByText("Create Model")).toBeInTheDocument();
  });

  it("calls onClose when close icon clicked", () => {
    render(<ModelDialog {...defaultProps} />);
    // Find the X button inside header
    const closeBtn = screen.getAllByRole("button")[0];
    fireEvent.click(closeBtn);
    expect(defaultProps.onClose).toHaveBeenCalled();
  });

  it("calls onClose when cancel clicked", () => {
    render(<ModelDialog {...defaultProps} />);
    fireEvent.click(screen.getByText("Cancel"));
    expect(defaultProps.onClose).toHaveBeenCalled();
  });

  it("calls onClose when backdrop clicked", () => {
    render(<ModelDialog {...defaultProps} />);
    const backdropBtn = screen.getByLabelText("Close the dialog");
    fireEvent.click(backdropBtn);
    expect(defaultProps.onClose).toHaveBeenCalled();
  });

  it("calls setErrorMessage(null) when error close clicked", () => {
    const { container } = render(<ModelDialog {...defaultProps} errorMessage="Some error" />);
    expect(screen.getByText("Some error")).toBeInTheDocument();

    // Find the button inside the error banner container
    const errorBanner = container.querySelector(".bg-danger\\/5");
    expect(errorBanner).not.toBeNull();
    const clearErrorBtn = errorBanner!.querySelector("button");
    expect(clearErrorBtn).not.toBeNull();

    fireEvent.click(clearErrorBtn!);
    expect(defaultProps.setErrorMessage).toHaveBeenCalledWith(null);
  });

  it("calls setFormModel when inputs are changed", () => {
    render(<ModelDialog {...defaultProps} />);

    // Test modelName change
    const nameInput = screen.getByPlaceholderText("Placeholder Model Name");
    fireEvent.change(nameInput, { target: { value: "new-name" } });
    expect(defaultProps.setFormModel).toHaveBeenCalled();

    // Test modelLabel change
    const labelInput = screen.getByPlaceholderText("Placeholder Model Label");
    fireEvent.change(labelInput, { target: { value: "new-label" } });
    expect(defaultProps.setFormModel).toHaveBeenCalled();

    // Test options change
    const optionsInput = screen.getByPlaceholderText("Placeholder Options");
    fireEvent.change(optionsInput, { target: { value: "new-options" } });
    expect(defaultProps.setFormModel).toHaveBeenCalled();

    // Test providerName change
    const providerSelect = screen.getByRole("combobox");
    fireEvent.change(providerSelect, { target: { value: "openai" } });
    expect(defaultProps.setFormModel).toHaveBeenCalled();

    // Test default checkbox change
    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);
    expect(defaultProps.setFormModel).toHaveBeenCalled();
  });

  it("calls setFormModel when a category is selected", () => {
    render(<ModelDialog {...defaultProps} />);

    // Click on Video category button
    const videoCategoryBtn = screen.getByText("Video");
    fireEvent.click(videoCategoryBtn);
    expect(defaultProps.setFormModel).toHaveBeenCalled();
  });

  it("triggers onSubmit when submit button clicked", () => {
    const { container } = render(<ModelDialog {...defaultProps} />);
    const form = container.querySelector("form");
    expect(form).not.toBeNull();
    fireEvent.submit(form!);
    expect(defaultProps.onSubmit).toHaveBeenCalled();
  });

  it("disables buttons and fields when saving is true", () => {
    render(<ModelDialog {...defaultProps} saving={true} />);
    expect(screen.getByPlaceholderText("Placeholder Model Name")).toBeDisabled();
    expect(screen.getByPlaceholderText("Placeholder Model Label")).toBeDisabled();
    expect(screen.getByPlaceholderText("Placeholder Options")).toBeDisabled();
    expect(screen.getByRole("checkbox")).toBeDisabled();
    expect(screen.getByRole("combobox")).toBeDisabled();
    expect(screen.getByText("Cancel")).toBeDisabled();

    // Category Selector buttons should be disabled
    const categoryBtns = screen.getAllByRole("button");
    categoryBtns.forEach((btn) => {
      const ariaLabel = btn.getAttribute("aria-label");
      if (ariaLabel !== "Help" && ariaLabel !== "Close dialog" && btn.textContent !== "") {
        expect(btn).toBeDisabled();
      }
    });
  });

  it("shows tooltip text on focus (Radix tooltip)", async () => {
    render(<ModelDialog {...defaultProps} />);

    // Find a help button (tooltip trigger)
    const helpBtns = screen.getAllByLabelText("Help");
    expect(helpBtns.length).toBeGreaterThan(0);
    const trigger = helpBtns[0];

    // Tooltip text shouldn't be visible initially
    expect(screen.queryByText("Help Category")).not.toBeInTheDocument();

    act(() => {
      trigger.focus();
    });
    expect((await screen.findAllByText("Help Category")).length).toBeGreaterThan(0);

    // Trigger focus
    act(() => {
      fireEvent.focus(trigger);
    });
    expect(screen.getByText("Help Category")).toBeInTheDocument();

    // Trigger blur
    act(() => {
      fireEvent.blur(trigger);
    });
    expect(screen.queryByText("Help Category")).not.toBeInTheDocument();
  });
});
