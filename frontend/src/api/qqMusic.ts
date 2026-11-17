import { jsonRequest, RequestError } from "./http";
import { bootUrl } from "./legacy";
import { useSessionStore } from "../stores/session";
import type { Song } from "../types";

const base = "/api/v1/music-providers/qq";
export type QqChannel = "qqmusic" | "qq" | "wechat";
export interface QqConnection {
  configured: boolean;
  connected: boolean;
  needsReconnect: boolean;
  nickname: string | null;
  connectedAt: number | null;
}
export interface QqQrTask {
  id: string;
  channel: QqChannel;
  image: string;
  state: "waiting" | "scanned" | "confirmed" | "expired";
  expiresAt: number;
  message: string | null;
}

// Cleanup uses the initiating website credential even if another user is now signed in.
export async function cancelQqTask(id: string, token: string): Promise<void> {
  await jsonRequest(bootUrl(`${base}/login/${encodeURIComponent(id)}`), {
    method: "DELETE", headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(35000),
  });
}

async function request<T>(path: string, method = "GET", body?: unknown, signal?: AbortSignal): Promise<T> {
  const session = useSessionStore(), version = session.revision;
  if (!session.authenticated) throw new RequestError("http", "请先登录", 401);
  let token = session.accessToken;
  const send = () => jsonRequest<{ code: number; message: string; data: T }>(bootUrl(`${base}${path}`), {
    method, headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(240000)]) : AbortSignal.timeout(240000),
  });
  let response;
  try { response = await send(); }
  catch (error) {
    if (!(error instanceof RequestError) || error.status !== 401 || version !== session.revision) throw error;
    if (session.accessToken === token) await session.refresh();
    if (version !== session.revision) throw new Error("登录状态已改变");
    token = session.accessToken;
    response = await send();
  }
  if (version !== session.revision) {
    if (path === "/login" && response.code === 0) {
      const id = (response.data as QqQrTask)?.id;
      if (id) void cancelQqTask(id, token).catch(() => {});
    }
    throw new Error("登录状态已改变");
  }
  signal?.throwIfAborted();
  if (response.code !== 0) throw new RequestError("business", response.message || "QQ 音乐连接失败");
  return response.data;
}

export const qqMusic = {
  lyrics: (songMid: string, signal?: AbortSignal) => request<{ lyric: string }>(`/songs/${encodeURIComponent(songMid)}/lyrics`, "GET", undefined, signal),
  dailySongs: (signal?: AbortSignal) => request<Song[]>("/daily-songs", "GET", undefined, signal),
  status: () => request<QqConnection>("/connection"),
  capabilities: () => request<{ configured: boolean; channels: QqChannel[] }>("/capabilities"),
  start: (channel: QqChannel) => request<QqQrTask>("/login", "POST", { channel }),
  check: (id: string) => request<QqQrTask>(`/login/${encodeURIComponent(id)}/check`, "POST"),
  disconnect: () => request<void>("/connection", "DELETE"),
  playlists: (favoriteOffset = 0, signal?: AbortSignal) => request<QqPlaylistPage>(`/playlists?favoriteOffset=${favoriteOffset}`, "GET", undefined, signal),
  songs: (kind: "remote" | "imported", id: string, offset = 0, signal?: AbortSignal) =>
    request<QqSongPage>(`/${kind === "remote" ? "playlists" : "imports"}/${encodeURIComponent(id)}/songs?offset=${offset}&limit=100`, "GET", undefined, signal),
  imports: (signal?: AbortSignal) => request<QqImportedPlaylist[]>("/imports", "GET", undefined, signal),
  importPlaylist: (playlistId: string) => request<QqImportedPlaylist>("/imports", "POST", { playlistId }),
  removeImport: (id: string) => request<void>(`/imports/${encodeURIComponent(id)}`, "DELETE"),
  playback: (song: Song, signal?: AbortSignal) => request<{ url: string; preview: boolean; quality: string }>("/playback", "POST", {
    songMid: song.providerSongId, mediaMid: song.mediaMid || "",
  }, signal),
};

export interface QqPlaylist { id: string; name: string; coverUrl: string | null; trackCount: number | null; owned: boolean; liked: boolean }
export interface QqPlaylistPage { items: QqPlaylist[]; total: number; more: boolean; nextFavoriteOffset: number | null }
export interface QqSongPage { items: Song[]; total: number; offset: number; nextOffset: number; more: boolean }
export interface QqImportedPlaylist { id: string; providerPlaylistId: string; name: string; coverUrl: string | null; songCount: number; importedAt: number }

export async function loadAllQqSongs(kind: "remote" | "imported", id: string, signal: AbortSignal, progress: (count: number) => void): Promise<Song[]> {
  const songs = new Map<string, Song>();
  let offset = 0;
  for (let page = 0; page <= 50; page++) {
    signal.throwIfAborted();
    const result = await qqMusic.songs(kind, id, offset, signal);
    if (result.total > 5000) throw new Error("单次最多加载5000首歌曲，请分批加入队列");
    const before = songs.size;
    for (const song of result.items) songs.set(song.providerSongId || song.songId, song);
    progress(songs.size);
    if (!result.more) return [...songs.values()];
    if (!result.items.length || result.nextOffset <= offset || before === songs.size) throw new Error("歌单未完整加载，请稍后重试");
    offset = result.nextOffset;
  }
  throw new Error("歌单未完整加载，请稍后重试");
}
