/** One message of the conversation on screen. */
export interface ChatMessage {
  readonly id: string;
  readonly role: "user" | "assistant";
  readonly content: string;
  /** The interceptors the router said it would run for this answer (empty until the ack arrives). */
  readonly planned: readonly string[];
  /** The ones that really finished, in order. */
  readonly steps: readonly string[];
  /** True once the stream has ended (or failed): the typing indicator stops. */
  readonly settled: boolean;
}
