// OWNER: Track B (proxy entry/config/auth). Contract: docs/contracts/proxy-api.md (v2).
//
// Mount route ở đây. Track C (VTO) export `tryonRoutes: RouteDef[]` từ src/tryon/** và gửi patch
// thêm vào ROUTES; C không sửa file này song song (CONTRACTS.md CT-00).
import { createApp } from "./app";
import type { Env } from "./env";
import { outfitsRoute } from "./outfits/route";
import { recognitionRoute } from "./recognition/route";
import type { RouteDef } from "./shared/registry";

export type { Env } from "./env";
export type { RouteDef, RouteContext } from "./shared/registry";

export const ROUTES: readonly RouteDef[] = [recognitionRoute, outfitsRoute];

const app = createApp(ROUTES);

export default {
  fetch: (request: Request, env: Env) => app.fetch(request, env),
} satisfies ExportedHandler<Env>;
