/**
 * @file endpoints.ts
 * @description Turns what an operator typed for an external service into the base URL the platform is configured with.
 */

const DEFAULT_OLLAMA_PORT = 11434;
const SCHEME = /^[a-z][a-z0-9+.-]*:\/\//i;

/**
 * The Ollama base URL for an operator's answer.
 *
 * A full URL (`https://ollama.corp.internal:8443`) is kept as typed, so a TLS endpoint stays TLS; only
 * http and https are accepted. A bare host or IP keeps the historical meaning: plain http on the default port.
 *
 * @throws Error when the answer is empty, has a scheme other than http(s), or is not a valid URL.
 */
export function ollamaBaseUrl(answer: string): string {
  let value = answer.trim();
  if (value === "") {
    throw new Error("The Ollama endpoint is empty.");
  }
  if (!SCHEME.test(value)) {
    return `http://${value}:${DEFAULT_OLLAMA_PORT}`;
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`The Ollama endpoint is not a valid URL: ${value}`);
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error(`The Ollama endpoint must be http or https, not ${url.protocol}`);
  }
  // Strip trailing slashes
  while (value.endsWith("/")) {
    value = value.slice(0, -1);
  }
  return value;
}
