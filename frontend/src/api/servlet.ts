import { jsonRequest, RequestError } from "./http";
import { legacyUrl } from "./legacy";

export interface LegacyResult {
  success?: boolean;
  message?: unknown;
}

// Only implemented actions move to Boot; remaining writes still execute in WAR.
export const bootActions: Record<string, string> = {
  "searchServlet/getSong": "/api/v1/library/song",
  "favoriteServlet/getFavorites": "/api/v1/library/favorites",
  "favoriteServlet/getStates": "/api/v1/library/states",
  "favoriteServlet/addFavorite": "/api/v1/library/favorite",
  "favoriteServlet/removeFavorite": "/api/v1/library/unfavorite",
  "userServlet/login": "/api/v1/session/login",
  "userServlet/refreshToken": "/api/v1/session/refresh",
  "userServlet/logout": "/api/v1/session/logout",
  "userServlet/getProfile": "/api/v1/session/profile",
};
export async function servletRequest<T extends LegacyResult>(
  servlet: string,
  action: string,
  data: Record<string, unknown> = {},
  token = "",
  signal?: AbortSignal,
): Promise<T> {
  const bootPath = bootActions[`${servlet}/${action}`];
  const base = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");
  const url = bootPath ? new URL(`${base}${bootPath}`, window.location.origin) : new URL(legacyUrl(servlet));
  if (!bootPath) url.searchParams.set("action", action);
  const result = await jsonRequest<T>(url.href, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
    signal,
  });
  if (result.success === false)
    throw new RequestError("business", typeof result.message === "string" ? result.message : "操作未完成，请重试");
  return result;
}

export const errorText = (error: unknown) => error instanceof Error ? error.message : "操作失败，请重试";
