import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CreateApiKeyForm } from "@/features/profile/components/ApiKeysParts";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      apiKeys: {
        nameLabel: "Key name",
        namePlaceholder: "CI",
        cancel: "Cancel",
        createCta: "Create key",
        limitReached: "Limit reached",
        createError: "Could not create",
      },
    },
    locale: "en",
  }),
}));

const renderForm = (props: Partial<Parameters<typeof CreateApiKeyForm>[0]> = {}) => {
  const onSubmit = jest.fn();
  const onCancel = jest.fn();
  render(<CreateApiKeyForm isSaving={false} error="none" onCancel={onCancel} onSubmit={onSubmit} {...props} />);
  return { onSubmit, onCancel };
};

describe("CreateApiKeyForm", () => {
  it("keeps the button disabled until a name is typed", () => {
    renderForm();
    expect(screen.getByRole("button", { name: "Create key" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Key name"), { target: { value: "  " } });
    expect(screen.getByRole("button", { name: "Create key" })).toBeDisabled();
  });

  it("hands the typed name over", async () => {
    const { onSubmit } = renderForm();
    fireEvent.change(screen.getByLabelText("Key name"), { target: { value: "CI runner" } });
    fireEvent.click(screen.getByRole("button", { name: "Create key" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("CI runner"));
  });

  it("shows the refusal and disables the button while saving", () => {
    renderForm({ error: "limit", isSaving: true });
    expect(screen.getByText("Limit reached")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Key name"), { target: { value: "x" } });
    expect(screen.getByRole("button", { name: "Create key" })).toBeDisabled();
  });

  it("cancels", () => {
    const { onCancel } = renderForm();
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalled();
  });
});
