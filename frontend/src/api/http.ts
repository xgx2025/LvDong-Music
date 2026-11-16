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
  if (!response.ok)
    throw new RequestError(
      "http",
      response.status === 401
        ? "播放凭证已失效，请重新配置"
        : `服务请求失败（${response.status}），请稍后重试`,
      response.status,
    );
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
