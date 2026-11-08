/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Mock for useTranslation — returns a minimal t object for test assertions.
 */
export const mockT: any = {
  playground: {
    mediaPayload: "Media Payload",
    uploadingAsset: "Uploading...",
    removeAsset: "Remove",
    dragAndDropOr: "Drag and drop or",
    browse: "Browse",
    supportedFormats: "Supported: PNG, JPG",
    invalidImageFile: "Invalid image",
    invalidAudioFile: "Invalid audio",
    taskQueued: "Task queued: {jobId}",
    taskActive: "Task active",
    status: "Status: ",
    jobId: "Job ID:",
    taskFailed: "Task failed",
    unknownError: "Unknown error",
    taskCompletedNoOutput: "Completed, no output",
    noAssetOutput: "No output",
    keyframesExtracted: "Keyframes:",
    transcript: "Transcript",
    noDialogueDetected: "No dialogue",
    localCPlusPlusInference: "Local C++ Inference",
    localAudioGeneration: "Audio generation",
    localImageGeneration: "Image generation",
    outputResult: "Output",
    gatewayRestriction: "Gateway restriction",
    onlyMp4MovSupported: "Only MP4/MOV",
    fileLimitExceeded: "File too large",
    removeVideo: "Remove video",
    dragAndDropVideoOr: "Drag and drop video or",
    supportedFormatsVideo: "Supported: MP4/MOV (",
    videoAnalysis: "Video Analysis",
    videoAnalysisDesc: "Extract keyframes and transcript",
    uploadingVideo: "Uploading...",
    processingVideo: "Processing...",
    executeIngestion: "Execute",
    processingProgress: "Processing: {percent}%",
    runningIngestion: "Running...",
    analyzeAnother: "Analyze another",
    model: "Model",
  },
  executionTimeline: {
    title: "Pipeline Progress",
    step1Title: "Prepare",
    step1Desc: "Context loaded",
    step2Title: "Inference",
    step2Desc: "Running {modelName}",
    step3Title: "Post-process",
    step3Desc: "Formatting output",
    step4Title: "Done",
    step4Desc: "Result ready",
  },
  notifications: {
    videoGen: "Video Gen",
    imageGen: "Image Gen",
    speechGen: "Speech Gen",
    textGen: "Text Gen",
  },
  jobs: {
    running: "Running...",
    viewPayload: "View payload",
    viewResult: "View result",
    viewErrorLogs: "View error",
    copied: "Copied",
    copyError: "Copy error",
    errorDetails: "Error details:",
  },
  admin: {
    setAsDefaultLabel: "Set as default",
    helpDefault: "Use this model by default",
  },
  interception: {
    loadingError: "Loading error",
  },
};

/**
 * Mock the locale context module.
 */
export function mockLocaleContext() {
  jest.mock("@/core/context/LocaleContext", () => ({
    useTranslation: () => ({ t: mockT, locale: "en" }),
  }));
}

/**
 * Mock JobStreamContext provider wrapper for testing hooks that depend on it.
 */
export function createMockJobStreamContext(overrides: Record<string, any> = {}) {
  const defaults: Record<string, any> = {
    jobs: [],
    activeJobsCount: 0,
    lastJobs: [],
    refreshJobs: jest.fn().mockResolvedValue(undefined),
    activeConversationId: "",
    setActiveConversationId: jest.fn(),
    playgroundInputs: {},
    setPlaygroundInput: jest.fn(),
    playgroundResults: {},
    setPlaygroundResult: jest.fn(),
    activeJobIdByNodeId: {},
    setActiveJobIdForNode: jest.fn(),
    videoAnalysisJobId: null,
    setVideoAnalysisJobId: jest.fn(),
    chatInput: "",
    setChatInput: jest.fn(),
    isChatStreaming: false,
    startChatStream: jest.fn(),
    stopChatStream: jest.fn(),
    videoAnalysisIsUploading: false,
    setVideoAnalysisIsUploading: jest.fn(),
    videoAnalysisError: null,
    setVideoAnalysisError: jest.fn(),
    ragQuery: "",
    setRagQuery: jest.fn(),
    ragResult: null,
    setRagResult: jest.fn(),
    ragIsPending: false,
    setRagIsPending: jest.fn(),
    ragError: null,
    setRagError: jest.fn(),
    jobProgress: {},
    ...overrides,
  };

  return defaults;
}
