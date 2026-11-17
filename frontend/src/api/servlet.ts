import { jsonRequest, RequestError } from "./http";
import { legacyUrl } from "./legacy";

export interface LegacyResult {
  success?: boolean;
  message?: unknown;
}

// Only implemented actions move to Boot; remaining writes still execute in WAR.
export const bootActions: Record<string, string> = {
  "userServlet/updateProfile": "/api/v1/creator/profile",
  "singerServlet/getStatus": "/api/v1/creator/status",
  "singerServlet/applyMusician": "/api/v1/creator/application",
  "musicServlet/upload": "/api/v1/creator/upload",
  "recognitionServlet/musicRecognition": "/api/v1/recognition",
  "authServlet/sendVerificationCode": "/api/v1/account/verification-code",
  "authServlet/verifyCode": "/api/v1/account/verification",
  "userServlet/register": "/api/v1/account/registration",
  "userServlet/resetPassword": "/api/v1/account/password-reset",
  "commentServlet/getComments": "/api/v1/comments/page",
  "commentServlet/getCommentCount": "/api/v1/comments/count",
  "commentServlet/createComment": "/api/v1/comments",
  "commentServlet/likeComment": "/api/v1/comments/like",
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
