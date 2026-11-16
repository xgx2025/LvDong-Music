import { jsonRequest } from "./http";
export const legacyBase = (import.meta.env.VITE_LEGACY_BASE || "/legacy/") as string;
export function legacyUrl(path: string): string {
  if (!legacyBase)
    throw new Error("尚未配置旧媒体服务。可先加入队列，待媒体服务接入后播放。");
  const base = new URL(legacyBase, window.location.origin);
  if (
    !["http:", "https:"].includes(base.protocol) ||
    base.username ||
    base.password ||
    base.search ||
    base.hash
  )
    throw new Error("旧服务地址无效，请检查配置");
  if (!base.pathname.endsWith("/")) base.pathname += "/";
  return new URL(path.replace(/^\//, ""), base).href;
}
export function coverUrl(path: string | null): string | undefined {
  if (!path) return;
  try {
    const url = /^https?:\/\//i.test(path)
      ? new URL(path)
      : new URL(legacyUrl(path));
    return ["http:", "https:"].includes(url.protocol) ? url.href : undefined;
  } catch {
    return;
  }
}
export interface MediaSource {
  url: string;
  token?: string;
  hls?: boolean;
}
export async function prepareLegacy(
  songId: string,
  token: string,
  signal: AbortSignal,
  status: (text: string) => void,
): Promise<MediaSource> {
  const endpoint = legacyUrl("hls");
  if (!token)
    throw new Error(
      "请在连接设置中提供旧站播放凭证，凭证仅保存在当前页面内存中。",
    );
  const headers = { Authorization: `Bearer ${token}` };
  const params = new URLSearchParams({ action: "processMp3", songId });
  const start = await jsonRequest<{ success: boolean }>(
    `${endpoint}?${params}`,
    {
      method: "POST",
      headers,
      signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    },
  );
  if (!start.success) throw new Error("媒体任务未被接受，请检查凭证或稍后重试");
  const deadline = Date.now() + 300000;
  while (Date.now() < deadline) {
    signal.throwIfAborted();
    params.set("action", "checkStatus");
    const task = await jsonRequest<{ state: string; ready: boolean }>(
      `${endpoint}?${params}`,
      {
        headers,
        signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
      },
    );
    if (task.state === "FAILED") throw new Error("音乐转换失败，请重试");
    if (task.ready && task.state === "READY") {
      params.set("action", "serveHls");
      params.set("fileName", "index.m3u8");
      params.set("stream", "1");
      return { url: `${endpoint}?${params}`, token, hls: true };
    }
    status(task.state === "QUEUED" ? "媒体正在排队…" : "正在准备完整音频…");
    await new Promise<void>((resolve, reject) => {
      const abort = () => {
        clearTimeout(timer);
        reject(signal.reason);
      };
      const timer = setTimeout(() => {
        signal.removeEventListener("abort", abort);
        resolve();
      }, 1500);
      signal.addEventListener("abort", abort, { once: true });
    });
  }
  throw new Error("音频准备超过五分钟，可稍后重试；服务端任务可能仍在继续");
}
