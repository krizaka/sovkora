import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { RecentSessions } from "@/features/dashboard/components/RecentSessions";

const mockT = {
  dashboard: {
    recentActivity: "Recent Activity",
    noRecentSessions: "No recent sessions",
    startNewChat: "Start New Chat",
    resumeSession: "Resume",
  },
} as never;

describe("RecentSessions", () => {
  const onStartNewChat = jest.fn();
  const onResumeSession = jest.fn();
  const formatDate = jest.fn((ts: number) => `date-${ts}`);

  afterEach(() => jest.clearAllMocks());

  it("renders heading", () => {
    render(
      <RecentSessions
        recentThreads={[]}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
  });

  it("shows empty state when no threads", () => {
    render(
      <RecentSessions
        recentThreads={[]}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    expect(screen.getByText("No recent sessions")).toBeInTheDocument();
    expect(screen.getByText("Start New Chat")).toBeInTheDocument();
  });

  it("calls onStartNewChat in empty state", () => {
    render(
      <RecentSessions
        recentThreads={[]}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    fireEvent.click(screen.getByText("Start New Chat"));
    expect(onStartNewChat).toHaveBeenCalled();
  });

  it("renders thread items", () => {
    const threads = [
      { conversationId: "c1", title: "Chat about AI", updatedAt: 1000 },
      { conversationId: "c2", title: "Code review", updatedAt: 2000 },
    ];
    render(
      <RecentSessions
        recentThreads={threads}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    expect(screen.getByText("Chat about AI")).toBeInTheDocument();
    expect(screen.getByText("Code review")).toBeInTheDocument();
  });

  it("formats dates using formatDate callback", () => {
    const threads = [{ conversationId: "c1", title: "Test", updatedAt: 1234 }];
    render(
      <RecentSessions
        recentThreads={threads}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    expect(formatDate).toHaveBeenCalledWith(1234);
    expect(screen.getByText("date-1234")).toBeInTheDocument();
  });

  it("calls onResumeSession when thread clicked", () => {
    const threads = [{ conversationId: "c1", title: "Chat about AI", updatedAt: 1000 }];
    render(
      <RecentSessions
        recentThreads={threads}
        onStartNewChat={onStartNewChat}
        onResumeSession={onResumeSession}
        formatDate={formatDate}
        t={mockT}
      />,
    );
    fireEvent.click(screen.getByText("Chat about AI"));
    expect(onResumeSession).toHaveBeenCalledWith("c1");
  });
});
