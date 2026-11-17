export class RequestError extends Error {
  constructor(
    public kind: "http" | "business" | "network" | "format",
    message: string,
    public status?: number,
  ) {
    super(message);
  }
}
export async function jsonRequest<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      signal: init?.signal ?? AbortSignal.timeout(15000),
    });
  } catch (error) {
    if (init?.signal?.aborted) throw error;
    throw new RequestError(
      "network",
      "无法连接服务，请检查网络或服务是否已启动",
    );
  }
  if (!response.ok) {
    let detail: string | undefined;
    if ([400, 403, 404, 409, 429, 503].includes(response.status)) {
      try {
        const body = await response.json() as { message?: unknown };
        if (typeof body.message === "string" && body.message.length <= 300) detail = body.message;
      } catch { /* Non-JSON errors keep the generic status message. */ }
    }
    throw new RequestError(
      "http",
      response.status === 401
        ? "登录凭证已失效，请重新登录"
        : detail || `服务请求失败（${response.status}），请稍后重试`,
      response.status,
    );
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new RequestError("format", "服务返回了无法识别的内容");
  }
}
export async function apiRequest<T>(
  path: string,
  signal?: AbortSignal,
): Promise<T> {
  const base = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");
  const result = await jsonRequest<{ code: number; message: string; data: T }>(
    `${base}${path}`,
    { signal },
  );
  if (result.code !== 0)
    throw new RequestError("business", result.message || "操作未完成，请重试");
  return result.data;
}
