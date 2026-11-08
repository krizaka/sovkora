import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { translations } from "@/core/context/translations";
import { InstallDialog } from "@/features/studio/components/InstallDialog";

const t = translations.en;

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

jest.mock("@/features/studio/hooks/useConfigSchema", () => ({
  useConfigSchema: () => [
    { key: "tone", title: "Tone", defaultValue: "formal", options: ["formal", "casual"] },
    { key: "signature", title: "Signature", defaultValue: "" },
  ],
}));

const base = { configSchema: "{}", isSubmitting: false, onSubmit: jest.fn(), onCancel: jest.fn() };

describe("InstallDialog (@krizaka/ui Dialog)", () => {
  afterEach(() => jest.clearAllMocks());

  it("is a dialog named by the configuration title, seeded from the installation", () => {
    render(<InstallDialog {...base} open initialConfig={{ tone: "casual" }} />);
    expect(screen.getByRole("dialog", { name: t.studio.configTitle })).toBeInTheDocument();
    expect(screen.getByLabelText("Tone")).toHaveValue("casual");
  });

  it("submits the filled fields only", () => {
    render(<InstallDialog {...base} open />);
    fireEvent.change(screen.getByLabelText("Signature"), { target: { value: "Ana" } });
    fireEvent.click(screen.getByRole("button", { name: t.studio.save }));
    expect(base.onSubmit).toHaveBeenCalledWith({ tone: "formal", signature: "Ana" });
  });

  it("blocks the inputs and refuses to close while submitting (ERR-126)", () => {
    render(<InstallDialog {...base} open isSubmitting />);
    expect(screen.getByLabelText("Tone")).toBeDisabled();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(base.onCancel).not.toHaveBeenCalled();
  });

  it("cancels on Escape", () => {
    render(<InstallDialog {...base} open />);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(base.onCancel).toHaveBeenCalled();
  });

  it("renders nothing when closed", () => {
    render(<InstallDialog {...base} open={false} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
