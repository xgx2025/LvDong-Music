import { jsonRequest, RequestError } from "./http";
import { legacyUrl } from "./legacy";

export interface LegacyResult {
  success?: boolean;
  message?: unknown;
}

// The existing WAR remains the identity and write API during the Vue migration.
// Credentials only travel to the explicitly configured legacy origin.
export async function servletRequest<T extends LegacyResult>(
  servlet: string,
  action: string,
  data: Record<string, unknown> = {},
  token = "",
  signal?: AbortSignal,
): Promise<T> {
  const url = new URL(legacyUrl(servlet));
  url.searchParams.set("action", action);
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
