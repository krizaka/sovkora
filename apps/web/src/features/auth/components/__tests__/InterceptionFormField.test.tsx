import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { InterceptionFormField } from "@/features/auth/components/InterceptionFormField";

jest.mock("@krizaka/orazaka-design-system", () => ({
  ...jest.requireActual("@krizaka/orazaka-design-system"),
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
}));

const textField = {
  name: "fullName",
  label: "Full Name",
  type: "text" as const,
  required: true,
  placeholder: "Enter your name",
};

const selectField = {
  name: "language",
  label: "Language",
  type: "select" as const,
  required: false,
  options: [
    { value: "en", label: "English" },
    { value: "fr", label: "Français" },
  ],
};

const textareaField = {
  name: "bio",
  label: "Bio",
  type: "textarea" as const,
  required: false,
  placeholder: "Tell us about yourself",
};

describe("InterceptionFormField", () => {
  it("renders text input with label", () => {
    render(<InterceptionFormField field={textField} value="" onChange={jest.fn()} locale="en" />);
    expect(screen.getByText("Full Name")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Enter your name")).toBeInTheDocument();
  });

  it("shows required indicator", () => {
    render(<InterceptionFormField field={textField} value="" onChange={jest.fn()} locale="en" />);
    expect(screen.getByText("* required")).toBeInTheDocument();
  });

  it("shows French required indicator", () => {
    render(<InterceptionFormField field={textField} value="" onChange={jest.fn()} locale="fr" />);
    expect(screen.getByText("* requis")).toBeInTheDocument();
  });

  it("renders select with options", () => {
    render(<InterceptionFormField field={selectField} value="en" onChange={jest.fn()} locale="en" />);
    expect(screen.getByText("English")).toBeInTheDocument();
    expect(screen.getByText("Français")).toBeInTheDocument();
  });

  it("renders textarea", () => {
    render(<InterceptionFormField field={textareaField} value="" onChange={jest.fn()} locale="en" />);
    expect(screen.getByPlaceholderText("Tell us about yourself")).toBeInTheDocument();
  });

  it("calls onChange on text input", () => {
    const onChange = jest.fn();
    render(<InterceptionFormField field={textField} value="" onChange={onChange} locale="en" />);
    fireEvent.change(screen.getByPlaceholderText("Enter your name"), { target: { value: "John" } });
    expect(onChange).toHaveBeenCalledWith("John");
  });
});
