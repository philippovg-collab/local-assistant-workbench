import type { ChatExecutionRequest } from "@/types";
import { resolveApiBaseUrl } from "./apiTransport";

export const buildChatRunCurlExample = (input: ChatExecutionRequest) => {
  const curlBaseUrl = resolveApiBaseUrl();

  return `curl ${curlBaseUrl}/api/chat-runs \\
  -H "Content-Type: application/json" \\
  --data-binary @- <<'JSON'
${JSON.stringify(input, null, 2)}
JSON`;
};
