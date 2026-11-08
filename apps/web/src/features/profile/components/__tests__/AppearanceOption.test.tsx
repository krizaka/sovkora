import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { RadioGroup } from "@krizaka/ui/radio-group";
import { previewIsland, AppearanceOption } from "@/features/profile/components/AppearanceOption";

const defaultProps = {
  value: "dark" as const,
  label: "Dark Mode",
  desc: "Easy on the eyes",
  icon: <span data-testid="icon">🌙</span>,
  isActive: false,
  clickToApplyLabel: "Click to apply",
};

/** An option lives in its group (Radix): the group holds the choice. */
function Option(props: React.ComponentProps<typeof AppearanceOption>) {
  return (
    <RadioGroup.Root value={props.isActive ? props.value : ""} label="Appearance">
      <AppearanceOption {...props} />
    </RadioGroup.Root>
  );
}

describe("AppearanceOption", () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it("renders label and description", () => {
    render(<Option {...defaultProps} />);
    expect(screen.getByText("Dark Mode")).toBeInTheDocument();
    expect(screen.getByText("Easy on the eyes")).toBeInTheDocument();
  });

  it("renders the icon", () => {
    render(<Option {...defaultProps} />);
    expect(screen.getByTestId("icon")).toBeInTheDocument();
  });

  it("is a radio named by its words", () => {
    render(<Option {...defaultProps} />);
    expect(screen.getByRole("radio", { name: /Dark Mode/ })).not.toBeChecked();
  });

  it("shows checkmark when active", () => {
    const { container } = render(<Option {...defaultProps} isActive={true} />);
    const mark = container.querySelector("mark");
    expect(mark).toBeInTheDocument();
  });

  it("does not show checkmark when not active", () => {
    const { container } = render(<Option {...defaultProps} isActive={false} />);
    const mark = container.querySelector("mark");
    expect(mark).not.toBeInTheDocument();
  });

  it("shows hover overlay when not active", () => {
    render(<Option {...defaultProps} isActive={false} />);
    expect(screen.getByText("Click to apply")).toBeInTheDocument();
  });

  it("does not show hover overlay when active", () => {
    render(<Option {...defaultProps} isActive={true} />);
    expect(screen.queryByText("Click to apply")).not.toBeInTheDocument();
  });

  it("applies active class when isActive", () => {
    render(<Option {...defaultProps} isActive={true} />);
    const button = screen.getByRole("radio");
    expect(button.className).toContain("theme-card-active");
  });

  it("draws a theme inside its island, so the preview reads that theme's tokens", () => {
    const { container, rerender } = render(<Option {...defaultProps} value="cyberpunk" />);
    expect(container.querySelector("[data-island]")).toHaveClass("theme-cyberpunk");
    rerender(<Option {...defaultProps} value="dark" />);
    expect(container.querySelector("[data-island]")).toHaveClass("theme-dark");
  });

  it("draws light with the invariant tokens and system half light, half dark", () => {
    const { container, rerender } = render(<Option {...defaultProps} value="light" />);
    expect(container.querySelector("[data-island]")).toBeNull();
    rerender(<Option {...defaultProps} value="system" />);
    expect(container.querySelectorAll("[data-island='theme-dark']")).toHaveLength(1);
  });

  it("maps every appearance to the way its preview reads tokens", () => {
    expect(previewIsland("light")).toEqual({ kind: "light" });
    expect(previewIsland("system")).toEqual({ kind: "system" });
    expect(previewIsland("solarized")).toEqual({ kind: "island", className: "theme-solarized" });
  });

  it("exposes the selection as a checked radio", () => {
    render(<Option {...defaultProps} isActive={true} />);
    expect(screen.getByRole("radio")).toBeChecked();
  });
});
