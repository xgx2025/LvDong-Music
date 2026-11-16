import { useSessionStore } from "../stores/session";
import { servletRequest, type LegacyResult } from "./servlet";
import type { Song } from "../types";

export const sendCode = (email: string) => servletRequest("authServlet", "sendVerificationCode", { email });
export const register = (data: Record<string, unknown>) => servletRequest("userServlet", "register", data);
export const resetPassword = (data: Record<string, unknown>) => servletRequest("userServlet", "resetPassword", data);
export const updateProfile = (data: Record<string, unknown>) => useSessionStore().request<LegacyResult & { avatarUrl?: string }>("userServlet", "updateProfile", data);
export const getProfile = () => useSessionStore().request<LegacyResult & { user: import("../stores/session").UserProfile }>("userServlet", "getProfile");
export const getFavorites = (page: number) => useSessionStore().request<LegacyResult & { favorites: { list: Song[]; total: number } }>("favoriteServlet", "getFavorites", { page, size: 20 });
export const getFavoriteStates = (songIds: string[]) => useSessionStore().request<LegacyResult & { states: Record<string, boolean> }>("favoriteServlet", "getStates", { songIds: songIds.join(",") });
export const musicianStatus = () => useSessionStore().request<LegacyResult & { status: string }>("singerServlet", "getStatus", { singerId: useSessionStore().user?.id });
export const applyMusician = (data: Record<string, unknown>) => useSessionStore().request("singerServlet", "applyMusician", data);
export const uploadMusic = (data: Record<string, unknown>) => useSessionStore().request("musicServlet", "upload", data);
export const setFavorite = (songId: string, liked: boolean) => useSessionStore().request("favoriteServlet", liked ? "addFavorite" : "removeFavorite", { songId });
export const recognize = (text: string) => useSessionStore().request<LegacyResult & { data?: Song; songInfo?: string }>("recognitionServlet", "musicRecognition", { text });

export function readFile(file: File, kind: "image" | "audio" | "lyric"): Promise<{ name: string; type: string; data: string }> {
  const limit = { image: 3 * 1024 * 1024, audio: 30 * 1024 * 1024, lyric: 512 * 1024 }[kind];
  const allowed = kind === "image" ? /^(image\/(jpeg|jpg|png|webp|gif))$/ : kind === "audio" ? /^audio\/(mpeg|mp3|wav|x-wav|wave|ogg|flac|x-flac|mp4|aac)$/ : /^(text\/plain|application\/octet-stream|application\/x-lrc|text\/x-lrc)?$/;
  if (!file.size || file.size > limit) return Promise.reject(new Error(`文件为空或超过${Math.round(limit / 1024)}KB大小限制`));
  if (!allowed.test(file.type)) return Promise.reject(new Error("不支持的文件格式"));
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("文件读取失败，请重试"));
    reader.onload = () => resolve({ name: file.name, type: file.type, data: String(reader.result) });
    reader.readAsDataURL(file);
  });
}
