// OWNER: Track B. Log kỹ thuật: CHỈ requestId/service/status/latency/con số.
// Không bao giờ log ảnh, base64, freeText, profile, token hay API key (CT-07).

export interface LogEvent {
  requestId: string;
  route: string;
  service?: string;
  status: number;
  latencyMs: number;
  errorCode?: string;
  model?: string;
  inputTokens?: number;
  outputTokens?: number;
  /** Số outfit hợp lệ trả về / số outfit AI trả bị loại theo lý do — chỉ là con số. */
  accepted?: number;
  rejected?: Record<string, number>;
  promptVersion?: string;
}

export type Logger = (event: LogEvent) => void;

export const consoleLogger: Logger = (event) => {
  console.log(JSON.stringify(event));
};
