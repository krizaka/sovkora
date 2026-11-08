/* eslint-disable @typescript-eslint/no-explicit-any */
import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { ResultDisplay } from "@/app/jobs/ResultDisplay";

// Mock dependencies
const mockT = {
  playground: {
    taskQueued: "Queued: {jobId}",
    taskActive: "Active",
    status: "Status: ",
    jobId: "Job:",
    taskFailed: "Failed",
    unknownError: "Unknown error",
    taskCompletedNoOutput: "Completed",
    noAssetOutput: "No output",
    keyframesExtracted: "Keyframes:",
    transcript: "Transcript",
    noDialogueDetected: "No dialogue",
    localCPlusPlusInference: "C++ Inference",
    localAudioGeneration: "Audio Gen",
    localImageGeneration: "Image Gen",
  },
};

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: mockT, locale: "en" }),
}));

const mockJobs: any[] = [];
jest.mock("@/core/context/JobStreamContext", () => ({
  useJobStream: () => ({ jobs: mockJobs }),
}));

jest.mock("@/core/constants/http.constants", () => ({
  JOB_STATUS: {
    PENDING: "PENDING",
    PROCESSING: "PROCESSING",
    COMPLETED: "COMPLETED",
    FAILED: "FAILED",
  },
}));

describe("ResultDisplay", () => {
  beforeEach(() => {
    mockJobs.length = 0;
  });

  it("renders no output message when payload is null", () => {
    render(<ResultDisplay payload={null} />);
    expect(screen.getByText("No output")).toBeInTheDocument();
  });

  it("renders no output message when payload is empty string", () => {
    render(<ResultDisplay payload="" />);
    expect(screen.getByText("No output")).toBeInTheDocument();
  });

  it("renders a raw URL as pre block", () => {
    render(<ResultDisplay payload="https://example.com/result" />);
    expect(screen.getByText("https://example.com/result")).toBeInTheDocument();
  });

  it("renders an image when URL ends with .png", () => {
    render(<ResultDisplay payload="https://example.com/image.png" />);
    const img = screen.getByAltText("Sovkora Framework Local AI Generation");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://example.com/image.png");
  });

  it("renders an image for .jpg extension", () => {
    render(<ResultDisplay payload="https://example.com/photo.jpg" />);
    const img = screen.getByAltText("Sovkora Framework Local AI Generation");
    expect(img).toBeInTheDocument();
  });

  it("renders a video for .mp4 extension", () => {
    render(<ResultDisplay payload="https://example.com/clip.mp4" />);
    const video = document.querySelector("video");
    expect(video).toBeInTheDocument();
    expect(video).toHaveAttribute("src", "https://example.com/clip.mp4");
  });

  it("renders audio for .mp3 extension", () => {
    render(<ResultDisplay payload="https://example.com/audio.mp3" />);
    const audio = document.querySelector("audio");
    expect(audio).toBeInTheDocument();
    expect(audio).toHaveAttribute("src", "https://example.com/audio.mp3");
  });

  it("renders audio for .wav extension", () => {
    render(<ResultDisplay payload="https://example.com/audio.wav" />);
    const audio = document.querySelector("audio");
    expect(audio).toBeInTheDocument();
  });

  it("renders video for data:video/ URL", () => {
    render(<ResultDisplay payload="data:video/mp4;base64,AAAA" />);
    const video = document.querySelector("video");
    expect(video).toBeInTheDocument();
  });

  it("renders image for data:image/ URL", () => {
    render(<ResultDisplay payload="data:image/png;base64,AAAA" />);
    const img = screen.getByAltText("Sovkora Framework Local AI Generation");
    expect(img).toBeInTheDocument();
  });

  it("renders audio for data:audio/ URL", () => {
    render(<ResultDisplay payload="data:audio/mp3;base64,AAAA" />);
    const audio = document.querySelector("audio");
    expect(audio).toBeInTheDocument();
  });

  it("renders content from object payload with format=mp4", () => {
    render(<ResultDisplay payload={{ url: "https://example.com/clip.mp4", format: "mp4" }} />);
    const video = document.querySelector("video");
    expect(video).toBeInTheDocument();
  });

  it("renders content from object payload with format=png", () => {
    render(<ResultDisplay payload={{ url: "https://example.com/img.png", format: "png" }} />);
    const img = screen.getByAltText("Sovkora Framework Local AI Generation");
    expect(img).toBeInTheDocument();
  });

  it("renders content from object payload with format=mp3", () => {
    render(<ResultDisplay payload={{ url: "https://example.com/audio.mp3", format: "mp3" }} />);
    const audio = document.querySelector("audio");
    expect(audio).toBeInTheDocument();
  });

  it("renders text content when no URL is provided", () => {
    render(<ResultDisplay payload={{ content: "Some text content" }} />);
    expect(screen.getByText("Some text content")).toBeInTheDocument();
  });

  it("renders analysis content when no URL is provided", () => {
    render(<ResultDisplay payload={{ analysis: "Analysis result here" }} />);
    expect(screen.getByText("Analysis result here")).toBeInTheDocument();
  });

  it("renders transcript info for video analysis result", () => {
    render(
      <ResultDisplay
        payload={{
          transcript: "Hello world",
          keyframeCount: 5,
        }}
      />,
    );
    expect(screen.getByText("Hello world")).toBeInTheDocument();
    expect(screen.getByText(/Keyframes/)).toBeInTheDocument();
  });

  it("shows no dialogue detected for empty transcript", () => {
    render(
      <ResultDisplay
        payload={{
          transcript: "",
          keyframeCount: 3,
        }}
      />,
    );
    expect(screen.getByText("No dialogue")).toBeInTheDocument();
  });

  it("renders loader and taskQueued when jobId is provided but job is not in stream", () => {
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    expect(screen.getByText("Queued: job-123")).toBeInTheDocument();
  });

  it("renders loader and taskQueued when jobId is in parsed JSON string payload and job not in stream", () => {
    render(<ResultDisplay payload='{"jobId":"job-456"}' />);
    expect(screen.getByText("Queued: job-456")).toBeInTheDocument();
  });

  it("renders active job status when job is PENDING or PROCESSING in stream", () => {
    mockJobs.push({ id: "job-123", status: "PENDING", result: null, errorMessage: null });
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText(/PENDING/)).toBeInTheDocument();
    expect(screen.getByText(/job-123/)).toBeInTheDocument();
  });

  it("renders failed job status with message when job status is FAILED", () => {
    mockJobs.push({
      id: "job-123",
      status: "FAILED",
      result: null,
      errorMessage: "GPU out of memory",
    });
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("GPU out of memory")).toBeInTheDocument();
  });

  it("renders failed job status with unknown error fallback", () => {
    mockJobs.push({
      id: "job-123",
      status: "FAILED",
      result: null,
      errorMessage: null,
    });
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
    expect(screen.getByText("Unknown error")).toBeInTheDocument();
  });

  it("renders completed status with no output message when result is missing", () => {
    mockJobs.push({ id: "job-123", status: "COMPLETED", result: null, errorMessage: null });
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("renders resultInner content when job status is COMPLETED and result exists", () => {
    mockJobs.push({
      id: "job-123",
      status: "COMPLETED",
      result: "https://example.com/image.png",
      errorMessage: null,
    });
    render(<ResultDisplay payload={{ jobId: "job-123" }} />);
    const img = screen.getByAltText("Sovkora Framework Local AI Generation");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://example.com/image.png");
  });
});
