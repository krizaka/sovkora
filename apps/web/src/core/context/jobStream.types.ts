import type { ChatRouting } from "@/core/utils/routing";
import type { Job } from "@/core/types/jobs.types";
import type { ChatPipelineSchema } from "@/core/types/pipeline.types";

export interface JobStreamContextType {
  jobs: Job[];
  activeJobsCount: number;
  lastJobs: Job[];
  refreshJobs: () => Promise<void>;
  activeConversationId: string;
  setActiveConversationId: (id: string) => void;
  playgroundInputs: Record<string, Record<string, string>>;
  setPlaygroundInput: (nodeId: string, field: string, value: string) => void;
  playgroundResults: Record<string, unknown>;
  setPlaygroundResult: (nodeId: string, result: unknown) => void;
  activeJobIdByNodeId: Record<string, string>;
  setActiveJobIdForNode: (nodeId: string, jobId: string | null) => void;
  videoAnalysisJobId: string | null;
  setVideoAnalysisJobId: (jobId: string | null) => void;
  chatInput: string;
  setChatInput: (val: string) => void;
  isChatStreaming: boolean;
  chatPipeline: ChatPipelineSchema | null;
  chatStageProgress: string[];
  /** The routing of the last answer (from the stream's `routing` event), or null when the engine has not said. */
  chatRouting: ChatRouting | null;
  startChatStream: (conversationId: string, prompt: string, assetIds?: string[]) => void;
  stopChatStream: () => void;
  videoAnalysisIsUploading: boolean;
  setVideoAnalysisIsUploading: (val: boolean) => void;
  videoAnalysisError: string | null;
  setVideoAnalysisError: (val: string | null) => void;
  ragQuery: string;
  setRagQuery: (val: string) => void;
  ragResult: string | null;
  setRagResult: (val: string | null) => void;
  ragIsPending: boolean;
  setRagIsPending: (val: boolean) => void;
  ragError: string | null;
  setRagError: (val: string | null) => void;
  jobProgress: Record<string, number>;
}
