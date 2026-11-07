import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { FeatureToggleCard } from "@/app/dashboard/admin/FeatureToggleCard";

const mockT = {
  admin: {
    featureOverridesTitle: "Feature Overrides",
    featureNoOverrides: "No feature overrides configured",
    featureOverrideEnabled: "Enabled",
  },
} as never;

describe("FeatureToggleCard", () => {
  it("renders the title", () => {
    render(<FeatureToggleCard features={[]} onToggle={jest.fn()} t={mockT} />);
    expect(screen.getByText("Feature Overrides")).toBeInTheDocument();
  });

  it("shows empty state when no features", () => {
    render(<FeatureToggleCard features={[]} onToggle={jest.fn()} t={mockT} />);
    expect(screen.getByText("No feature overrides configured")).toBeInTheDocument();
  });

  it("renders feature items", () => {
    const features = [
      { featureKey: "chat.text", isEnabled: true },
      { featureKey: "media.video", isEnabled: false },
    ];
    render(<FeatureToggleCard features={features} onToggle={jest.fn()} t={mockT} />);
    expect(screen.getByText("chat.text")).toBeInTheDocument();
    expect(screen.getByText("media.video")).toBeInTheDocument();
  });

  it("renders a switch for each feature, named by its key", () => {
    const features = [
      { featureKey: "chat.text", isEnabled: true },
      { featureKey: "media.video", isEnabled: false },
    ];
    render(<FeatureToggleCard features={features} onToggle={jest.fn()} t={mockT} />);
    const switches = screen.getAllByRole("switch");
    expect(switches).toHaveLength(2);
    expect(switches[0]).toBeChecked();
    expect(switches[1]).not.toBeChecked();
    expect(screen.getByRole("switch", { name: /chat\.text/ })).toBe(switches[0]);
  });

  it("calls onToggle when the switch is pressed", () => {
    const onToggle = jest.fn();
    const features = [{ featureKey: "chat.text", isEnabled: true }];
    render(<FeatureToggleCard features={features} onToggle={onToggle} t={mockT} />);
    fireEvent.click(screen.getByRole("switch"));
    expect(onToggle).toHaveBeenCalledWith("chat.text", true);
  });
});
