import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { AdminToolbar } from "@/app/dashboard/admin/AdminToolbar";

const mockT = {
  admin: {
    title: "Admin Panel",
    subtitle: "Manage models and features",
    refresh: "Refresh",
    addModel: "Add Model",
  },
  a11y: { closeDetails: "Close details" },
} as never;

describe("AdminToolbar", () => {
  const defaultProps = {
    loadingModels: false,
    errorMessage: null as string | null,
    onRefresh: jest.fn(),
    onAddModel: jest.fn(),
    onClearError: jest.fn(),
    t: mockT,
  };

  afterEach(() => jest.clearAllMocks());

  it("renders title and subtitle", () => {
    render(<AdminToolbar {...defaultProps} />);
    expect(screen.getByText("Admin Panel")).toBeInTheDocument();
    expect(screen.getByText("Manage models and features")).toBeInTheDocument();
  });

  it("renders refresh and add model buttons", () => {
    render(<AdminToolbar {...defaultProps} />);
    expect(screen.getByText("Refresh")).toBeInTheDocument();
    expect(screen.getByText("Add Model")).toBeInTheDocument();
  });

  it("calls onRefresh when refresh clicked", () => {
    render(<AdminToolbar {...defaultProps} />);
    fireEvent.click(screen.getByText("Refresh"));
    expect(defaultProps.onRefresh).toHaveBeenCalled();
  });

  it("calls onAddModel when add model clicked", () => {
    render(<AdminToolbar {...defaultProps} />);
    fireEvent.click(screen.getByText("Add Model"));
    expect(defaultProps.onAddModel).toHaveBeenCalled();
  });

  it("does not show error when null", () => {
    render(<AdminToolbar {...defaultProps} />);
    expect(screen.queryByText("Something went wrong")).not.toBeInTheDocument();
  });

  it("shows error message when present", () => {
    render(<AdminToolbar {...defaultProps} errorMessage="Something went wrong" />);
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
  });

  it("calls onClearError when error dismissed", () => {
    render(<AdminToolbar {...defaultProps} errorMessage="Error" />);
    // The X button to clear the error
    const buttons = screen.getAllByRole("button");
    const clearBtn = buttons.find((b) => !b.textContent?.includes("Refresh") && !b.textContent?.includes("Add"));
    fireEvent.click(clearBtn!);
    expect(defaultProps.onClearError).toHaveBeenCalled();
  });
});
