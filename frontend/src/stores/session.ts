import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { servletRequest, type LegacyResult } from "../api/servlet";
import { RequestError } from "../api/http";

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  avatar?: string;
  gender?: string;
  bio?: string;
  registerTime?: string;
}
interface Tokens { accessToken: string; refreshToken: string }
interface LoginResult extends LegacyResult { user: UserProfile; tokens: Tokens }
const storageKey = "lvdong.vue.session";

export const useSessionStore = defineStore("session", () => {
  const mediaToken = ref("");
  const accessToken = ref("");
  const user = ref<UserProfile>();
  const revision = ref(0);
  const authenticated = computed(() => !!accessToken.value && !!user.value);
  let refreshToken = "";
  let remember = false;
  let refreshing: Promise<void> | undefined;
  function persist() {
    try {
      if (remember && user.value)
        sessionStorage.setItem(storageKey, JSON.stringify({ user: user.value, accessToken: accessToken.value, refreshToken }));
      else sessionStorage.removeItem(storageKey);
    } catch { /* A blocked storage area does not prevent an in-memory login. */ }
  }
  function clear() {
    revision.value++;
    user.value = undefined;
    accessToken.value = mediaToken.value = refreshToken = "";
    remember = false;
    refreshing = undefined;
    persist();
  }
  async function login(email: string, password: string, keepSession = false) {
    clear();
    const version = revision.value;
    const result = await servletRequest<LoginResult>("userServlet", "login", { email, password });
    if (version !== revision.value) throw new Error("登录已取消");
    if (!result.success || !result.tokens?.accessToken || !result.tokens.refreshToken || !result.user?.id)
      throw new Error("登录响应不完整，请重试");
    user.value = result.user;
    accessToken.value = mediaToken.value = result.tokens.accessToken;
    refreshToken = result.tokens.refreshToken;
    remember = keepSession;
    persist();
  }
  async function refresh() {
    if (refreshing) return refreshing;
    if (!refreshToken) throw new RequestError("http", "请先登录", 401);
    const version = revision.value;
    const pending = (async () => {
      try {
        const result = await servletRequest<LegacyResult & { tokens: Tokens }>("userServlet", "refreshToken", { refreshToken });
        if (version !== revision.value) throw new Error("登录状态已改变");
        if (!result.tokens?.accessToken) throw new Error("登录已过期，请重新登录");
        accessToken.value = mediaToken.value = result.tokens.accessToken;
        refreshToken = result.tokens.refreshToken || refreshToken;
        persist();
      } catch (error) {
        if (version === revision.value) clear();
        throw error;
      }
    })();
    refreshing = pending;
    try { await pending; }
    finally { if (refreshing === pending) refreshing = undefined; }
  }
  async function request<T extends LegacyResult>(servlet: string, action: string, data: Record<string, unknown> = {}, signal?: AbortSignal): Promise<T> {
    if (!authenticated.value) throw new RequestError("http", "请先登录", 401);
    const version = revision.value;
    const token = accessToken.value;
    let result: T;
    try { result = await servletRequest<T>(servlet, action, data, token, signal); }
    catch (error) {
      if (!(error instanceof RequestError) || error.status !== 401 || signal?.aborted || version !== revision.value) throw error;
      if (accessToken.value === token) await refresh();
      if (version !== revision.value) throw new Error("登录状态已改变，请重新登录");
      try { result = await servletRequest<T>(servlet, action, data, accessToken.value, signal); }
      catch (retryError) {
        if (retryError instanceof RequestError && retryError.status === 401 && version === revision.value) clear();
        throw retryError;
      }
    }
    if (version !== revision.value) throw new Error("登录状态已改变，请重试");
    return result;
  }
  async function restore() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(storageKey) || "null");
      if (!saved?.user?.id || !saved.accessToken || !saved.refreshToken) return;
      user.value = saved.user;
      accessToken.value = saved.accessToken;
      refreshToken = saved.refreshToken;
      remember = true;
      await refresh(); // Never treat a browser snapshot as server authentication.
    } catch { clear(); }
  }
  function updateUser(changes: Partial<UserProfile>) {
    if (user.value) { user.value = { ...user.value, ...changes }; persist(); }
  }
  async function logout() {
    const token = accessToken.value, renewal = refreshToken;
    clear(); // Stops pending refresh from restoring this identity.
    if (token || renewal)
      await servletRequest("userServlet", "logout", { refreshToken: renewal }, token);
  }
  return { mediaToken, accessToken, user, revision, authenticated, login, logout, restore, refresh, request, updateUser, clear };
});
