/* eslint-disable react/display-name */
import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { NotificationBell } from "@/components/layout/NotificationBell";

jest.mock("next/link", () => {
  return ({ children, ...props }: { children: React.ReactNode; href: string; onClick?: () => void }) => (
    <a {...props}>{children}</a>
  );
});

let mockActiveJobsCount = 0;
let mockLastJobs: { id: string; featureKey: string; status: string }[] = [];

jest.mock("@/core/context/TenantContext", () => ({
  useTenant: () => ({
    accentClasses: { text: "text-warning", bgSoft: "bg-warning/10" },
  }),
}));

jest.mock("@/core/context/JobStreamContext", () => ({
  useJobStream: () => ({
    activeJobsCount: mockActiveJobsCount,
    lastJobs: mockLastJobs,
  }),
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      notifications: {
        title: "Background Tasks",
        region: "Notifications",
        active: "active",
        noTasks: "No tasks",
        viewAll: "View All",
        videoGen: "Video Generation",
        imageGen: "Image Generation",
        speechGen: "Speech Generation",
        textGen: "Text Generation",
        videoAnalysis: "Video Analysis",
      },
      showcase: {
        notifications: {
          emptyTitle: "No tasks",
          emptyBody: "Nothing yet",
          running: "Running",
          done: "Ready",
          failed: "Failed",
        },
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/core/constants/capability.constants", () => ({
  MODEL_CATEGORY: { VIDEO: "video", IMAGE: "image", SPEECH: "speech", VISION: "vision", AUDIO: "audio" },
}));

jest.mock("@/core/constants/http.constants", () => ({
  JOB_STATUS: { PROCESSING: "PROCESSING", PENDING: "PENDING", COMPLETED: "COMPLETED", FAILED: "FAILED" },
}));

describe("NotificationBell", () => {
  beforeEach(() => {
    mockActiveJobsCount = 0;
    mockLastJobs = [];
    jest.clearAllMocks();
  });

  it("renders bell button", () => {
    render(<NotificationBell bellOpen={false} onToggle={jest.fn()} />);
    expect(screen.getByLabelText("Notifications")).toBeInTheDocument();
  });

  it("does not show count badge when no active jobs", () => {
    render(<NotificationBell bellOpen={false} onToggle={jest.fn()} />);
    expect(screen.queryByText(/^\d+$/)).not.toBeInTheDocument();
  });

  it("shows count badge when active jobs present", () => {
    mockActiveJobsCount = 3;
    render(<NotificationBell bellOpen={false} onToggle={jest.fn()} />);
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("calls onToggle when bell clicked", () => {
    const onToggle = jest.fn();
    render(<NotificationBell bellOpen={false} onToggle={onToggle} />);
    fireEvent.click(screen.getByLabelText("Notifications"));
    expect(onToggle).toHaveBeenCalledWith(true);
  });

  it("shows dropdown when bellOpen is true", () => {
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("Background Tasks")).toBeInTheDocument();
    expect(screen.getByText("View All")).toBeInTheDocument();
  });

  it("shows empty state in dropdown", () => {
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("No tasks")).toBeInTheDocument();
  });

  it("renders completed job", () => {
    mockLastJobs = [{ id: "job-12345678-abcd", featureKey: "chat.text", status: "COMPLETED" }];
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("Text Generation")).toBeInTheDocument();
  });

  it("renders video job", () => {
    mockLastJobs = [{ id: "job-22345678-abcd", featureKey: "media.video", status: "PROCESSING" }];
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("Video Generation")).toBeInTheDocument();
  });

  it("renders image job", () => {
    mockLastJobs = [{ id: "job-32345678-abcd", featureKey: "media.image", status: "COMPLETED" }];
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("Image Generation")).toBeInTheDocument();
  });

  it("renders speech job", () => {
    mockLastJobs = [{ id: "job-42345678-abcd", featureKey: "chat.speech", status: "FAILED" }];
    render(<NotificationBell bellOpen={true} onToggle={jest.fn()} />);
    expect(screen.getByText("Speech Generation")).toBeInTheDocument();
  });

  it("closes on Escape (Radix popover)", () => {
    const onToggle = jest.fn();
    render(<NotificationBell bellOpen={true} onToggle={onToggle} />);
    fireEvent.keyDown(screen.getByText("Background Tasks"), { key: "Escape" });
    expect(onToggle).toHaveBeenCalledWith(false);
  });

  it("closes when a job is followed", () => {
    mockLastJobs = [{ id: "job-12345678-abcd", featureKey: "chat.text", status: "COMPLETED" }];
    const onToggle = jest.fn();
    render(<NotificationBell bellOpen={true} onToggle={onToggle} />);
    fireEvent.click(screen.getByText("Text Generation"));
    expect(onToggle).toHaveBeenCalledWith(false);
  });
});
