// OWNER: Track C (Deliverable C & G — Standalone Local HTTP Try-On Server Harness for Android Emulator & Live POC)
// Runs on 127.0.0.1:8788 ONLY (reachable from Android Emulator at http://10.0.2.2:8788).
// Does NOT modify Track B's `proxy/src/index.ts` or `proxy/package.json`.

import * as fs from "node:fs";
import * as http from "node:http";
import * as os from "node:os";
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import { createTryOnRouter } from "../src/tryon/handler";
import { JsonLedgerTryOnJobStore } from "../src/tryon/store";
import type { DurableTryOnJobStore, TryOnEnvConfig } from "../src/tryon/types";

function parseSimpleEnvFile(filePath: string): Record<string, string> {
  if (!fs.existsSync(filePath)) {
    return {};
  }
  const content = fs.readFileSync(filePath, "utf8");
  const out: Record<string, string> = {};
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eqIdx = line.indexOf("=");
    if (eqIdx <= 0) continue;
    const key = line.slice(0, eqIdx).trim();
    let val = line.slice(eqIdx + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

/**
 * Creates an atomic local-disk-backed `DurableTryOnJobStore` strictly outside Git (`~/.fitcheckai_private/vto_server_state/`).
 */
export function createLocalDiskDurableTryOnJobStore(stateDir: string): DurableTryOnJobStore {
  const resolvedDir = path.resolve(stateDir);
  const currentScriptDir = path.dirname(fileURLToPath(import.meta.url));
  const repoRoot = path.resolve(currentScriptDir, "..", "..");
  if (resolvedDir.startsWith(repoRoot)) {
    throw new Error(
      `TRYON_STATE_DIR (${resolvedDir}) MUST reside outside the Git repository (${repoRoot}).`,
    );
  }
  fs.mkdirSync(resolvedDir, { recursive: true, mode: 0o700 });
  fs.chmodSync(resolvedDir, 0o700);
  const ledgerFile = path.join(resolvedDir, "tryon_jobs_ledger.json");

  return new JsonLedgerTryOnJobStore({
    read(): string | null {
      if (!fs.existsSync(ledgerFile)) {
        return null;
      }
      return fs.readFileSync(ledgerFile, "utf8");
    },
    write(serializedJson: string): void {
      const tmpFile = `${ledgerFile}.tmp.${process.pid}`;
      fs.writeFileSync(tmpFile, serializedJson, { encoding: "utf8", mode: 0o600 });
      fs.renameSync(tmpFile, ledgerFile);
    },
  });
}

function buildRuntimeEnv(): {
  envConfig: TryOnEnvConfig;
  rawMerged: Record<string, string | undefined>;
} {
  const defaultEnvPath = path.join(os.homedir(), ".fitcheckai_private", "tryon.env");
  const envFilePath = process.env.TRYON_ENV_FILE || defaultEnvPath;
  const fromFile = parseSimpleEnvFile(envFilePath);
  const merged: Record<string, string | undefined> = { ...fromFile, ...process.env };

  const selectedProvider = (merged.TRYON_PROVIDER ?? "openai").trim().toLowerCase();
  const defaultDecision =
    selectedProvider === "fosta" ? "GO_DUAL" : "GO_FRONT_ONLY";
  const defaultConfigVersion =
    selectedProvider === "fosta" ? "v2-fosta-poc-1" : "v2-openai-poc-1";

  const envConfig: TryOnEnvConfig = {
    TRYON_PROVIDER: selectedProvider,
    // Safety invariant (Section 10): LIVE defaults strictly to "false"!
    TRYON_LIVE_ENABLED: merged.TRYON_LIVE_ENABLED ?? "false",
    TRYON_OFFLINE_MOCK_OPENAI: merged.TRYON_OFFLINE_MOCK_OPENAI ?? "false",
    TRYON_MOCK_SCENARIO: merged.TRYON_MOCK_SCENARIO,
    TRYON_OFFLINE_MOCK_FOSTA: merged.TRYON_OFFLINE_MOCK_FOSTA ?? "false",
    TRYON_FOSTA_MOCK_SCENARIO: merged.TRYON_FOSTA_MOCK_SCENARIO,
    TRYON_BUDGET_CONFIRMED: merged.TRYON_BUDGET_CONFIRMED ?? "false",
    TRYON_ALLOW_UNPRICED_MODEL: merged.TRYON_ALLOW_UNPRICED_MODEL ?? "false",
    TRYON_LIVE_MAX_REQUESTS: merged.TRYON_LIVE_MAX_REQUESTS ?? "1",
    TRYON_LIVE_MAX_ESTIMATED_USD: merged.TRYON_LIVE_MAX_ESTIMATED_USD ?? "0",
    VTO_FEASIBILITY_DECISION:
      (merged.VTO_FEASIBILITY_DECISION as TryOnEnvConfig["VTO_FEASIBILITY_DECISION"]) ??
      defaultDecision,
    VTO_PROVIDER_CONFIG_VERSION:
      merged.VTO_PROVIDER_CONFIG_VERSION ?? defaultConfigVersion,
    VTO_KILL_SWITCH: merged.VTO_KILL_SWITCH ?? "false",
    VTO_BUDGET_CAP_UNITS: merged.VTO_BUDGET_CAP_UNITS ?? "25",
    VTO_OUTPUT_TTL_MS: merged.VTO_OUTPUT_TTL_MS,
    OPENAI_API_KEY: merged.OPENAI_API_KEY,
    OPENAI_IMAGE_MODEL: merged.OPENAI_IMAGE_MODEL ?? "gpt-image-2",
    OPENAI_API_BASE_URL: merged.OPENAI_API_BASE_URL,
    OPENAI_ORG_ID: merged.OPENAI_ORG_ID,
    OPENAI_PROJECT_ID: merged.OPENAI_PROJECT_ID,
    OPENAI_IMAGE_SIZE: merged.OPENAI_IMAGE_SIZE ?? "1024x1536",
    OPENAI_IMAGE_QUALITY: merged.OPENAI_IMAGE_QUALITY ?? "medium",
    OPENAI_OUTPUT_FORMAT: merged.OPENAI_OUTPUT_FORMAT ?? "png",
    OPENAI_INPUT_FIDELITY: merged.OPENAI_INPUT_FIDELITY ?? "auto",
    OPENAI_ALLOW_CUSTOM_MODEL: merged.OPENAI_ALLOW_CUSTOM_MODEL ?? "false",
    OPENAI_TIMEOUT_MS: merged.OPENAI_TIMEOUT_MS ?? "90000",
    FOSTA_API_KEY: merged.FOSTA_API_KEY,
    FOSTA_ENDPOINT: merged.FOSTA_ENDPOINT,
    FOSTA_MODEL: merged.FOSTA_MODEL ?? "gpt-6-luna",
    FOSTA_TIMEOUT_MS: merged.FOSTA_TIMEOUT_MS ?? "240000",
    FOSTA_APPROVE_QUOTA: merged.FOSTA_APPROVE_QUOTA ?? "false",
  };
  return { envConfig, rawMerged: merged };
}

async function main(): Promise<void> {
  const { rawMerged } = buildRuntimeEnv();
  const bindHost = rawMerged.TRYON_BIND_HOST?.trim() || "127.0.0.1";
  if (bindHost !== "127.0.0.1" && bindHost !== "localhost" && bindHost !== "::1") {
    throw new Error(
      `Refusing to bind local Try-On server to non-loopback address '${bindHost}'. Keep bindHost=127.0.0.1 (Android Emulator connects via 10.0.2.2).`,
    );
  }
  const port = Number(rawMerged.TRYON_PORT ?? "8788");
  const stateDir =
    rawMerged.TRYON_STATE_DIR ||
    path.join(os.homedir(), ".fitcheckai_private", "vto_server_state");

  const store = createLocalDiskDurableTryOnJobStore(stateDir);
  const router = createTryOnRouter({ store });

  const server = http.createServer(
    async (req: http.IncomingMessage, res: http.ServerResponse) => {
      try {
        // Reload env file on each request so flipping TRYON_LIVE_ENABLED=false stops LIVE immediately without code edit!
        const { envConfig, rawMerged: latestMerged } = buildRuntimeEnv();
        const requiredToken = latestMerged.TRYON_LOCAL_AUTH_TOKEN?.trim();
        if (requiredToken) {
          const provided = req.headers["x-tryon-local-token"];
          if (provided !== requiredToken) {
            res.writeHead(401, { "content-type": "application/json; charset=utf-8" });
            res.end(
              JSON.stringify({
                error: {
                  code: "UNAUTHORIZED",
                  message: "Thiếu hoặc sai x-tryon-local-token trên máy chủ cục bộ.",
                  requestId: "local_auth_denied",
                  retryable: false,
                },
              }),
            );
            return;
          }
        }

        const chunks: Uint8Array[] = [];
        for await (const chunk of req) {
          chunks.push(chunk instanceof Uint8Array ? chunk : new TextEncoder().encode(String(chunk)));
        }
        const totalBytes = chunks.reduce((acc, c) => acc + c.byteLength, 0);
        const rawBody = new Uint8Array(totalBytes);
        let offset = 0;
        for (const chunk of chunks) {
          rawBody.set(chunk, offset);
          offset += chunk.byteLength;
        }

        const headers = new Headers();
        for (const [k, v] of Object.entries(req.headers)) {
          if (Array.isArray(v)) {
            for (const item of v) headers.append(k, item);
          } else if (typeof v === "string") {
            headers.set(k, v);
          }
        }

        const url = `http://${bindHost}:${port}${req.url ?? "/"}`;
        const method = req.method ?? "GET";
        const parsedPath = new URL(url).pathname;
        if (
          method === "GET" &&
          (parsedPath === "/v2/tryon/preflight" || parsedPath === "/v2/tryon/capabilities") &&
          !headers.has("x-session-id")
        ) {
          headers.set("x-session-id", "local-cli-preflight");
        }
        const webReq = new Request(url, {
          method,
          headers,
          body: method === "GET" || method === "HEAD" ? undefined : rawBody.buffer,
        });

        const webRes = await router.handle(webReq, envConfig);
        const outHeaders: Record<string, string> = {};
        webRes.headers.forEach((value, key) => {
          outHeaders[key] = value;
        });
        const resBytes = new Uint8Array(await webRes.arrayBuffer());
        res.writeHead(webRes.status, outHeaders);
        res.end(resBytes);
      } catch {
        res.writeHead(500, { "content-type": "application/json; charset=utf-8" });
        res.end(
          JSON.stringify({
            error: {
              code: "IO",
              message: "Lỗi nội bộ trên máy chủ thử đồ cục bộ.",
              requestId: "local_srv_err",
              retryable: false,
            },
          }),
        );
      }
    },
  );

  server.listen(port, bindHost, () => {
    const { envConfig } = buildRuntimeEnv();
    console.info(
      JSON.stringify({
        event: "tryon.local_server.listening",
        bind: `http://${bindHost}:${port}`,
        emulatorEndpoint: `http://10.0.2.2:${port}`,
        provider: envConfig.TRYON_PROVIDER,
        liveEnabled: envConfig.TRYON_LIVE_ENABLED === "true",
        offlineMockOpenAi: envConfig.TRYON_OFFLINE_MOCK_OPENAI === "true",
        offlineMockFosta: envConfig.TRYON_OFFLINE_MOCK_FOSTA === "true",
        modelId:
          envConfig.TRYON_PROVIDER === "fosta"
            ? envConfig.FOSTA_MODEL
            : envConfig.OPENAI_IMAGE_MODEL,
        maxLiveRequests: Number(envConfig.TRYON_LIVE_MAX_REQUESTS ?? "1"),
      }),
    );
  });
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
