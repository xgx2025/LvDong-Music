<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { cancelQqTask, qqMusic, type QqChannel, type QqConnection, type QqQrTask } from "../api/qqMusic";
import { useSessionStore } from "../stores/session";
import { errorText } from "../api/servlet";

const session = useSessionStore();
const connection = ref<QqConnection>();
const loading = ref(true), busy = ref(false), error = ref(""), notice = ref("");
const dialog = ref<HTMLDialogElement>();
const channels = ref<QqChannel[]>([]), selected = ref<QqChannel>("qqmusic");
const task = ref<QqQrTask>();
const qrBusy = ref(false), qrError = ref("");
let generation = 0, taskToken = "", timer: ReturnType<typeof setTimeout> | undefined, disposed = false;
const channelLabels: Record<QqChannel, string> = { qqmusic: "QQ 音乐 App", qq: "手机 QQ", wechat: "微信" };
const image = computed(() => /^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=\r\n]+$/.test(task.value?.image || "") ? task.value?.image : undefined);
const hint = computed(() => task.value?.state === "scanned" ? "已扫描，请在手机上确认连接" : task.value?.state === "expired" ? "二维码已失效" : "使用手机扫码，并在手机上确认");

async function loadStatus() {
  const version = generation;
  loading.value = true; error.value = "";
  try { const data = await qqMusic.status(); if (!disposed && version === generation) connection.value = data; }
  catch (e) { if (!disposed && version === generation) error.value = errorText(e); }
  finally { if (!disposed && version === generation) loading.value = false; }
}
function stopTask() {
  generation++;
  clearTimeout(timer); timer = undefined;
  const previous = task.value;
  task.value = undefined; qrBusy.value = false;
  if (previous) void cancelQqTask(previous.id, taskToken).catch(() => {});
}
function closeDialog() { dialog.value?.close(); stopTask(); }
function schedule(version: number) {
  timer = setTimeout(() => { void poll(version); }, 2000);
}
async function poll(version: number) {
  if (disposed || version !== generation || !task.value) return;
  if (Date.now() >= task.value.expiresAt) { task.value = { ...task.value, state: "expired" }; return; }
  try {
    const result = await qqMusic.check(task.value.id);
    if (disposed || version !== generation) return;
    task.value = result;
    if (result.state === "confirmed") {
      closeDialog(); notice.value = "QQ 音乐账号已连接"; await loadStatus(); return;
    }
    if (result.state === "expired") return;
    schedule(version);
  } catch (e) {
    if (!disposed && version === generation) qrError.value = errorText(e);
    // Pause on failures; explicit retry keeps rate limits and broken services from looping.
  }
}
async function createQr() {
  if (qrBusy.value) return;
  stopTask();
  const version = generation, token = session.accessToken;
  qrBusy.value = true; qrError.value = "";
  try {
    const created = await qqMusic.start(selected.value);
    if (disposed || version !== generation) { void cancelQqTask(created.id, token).catch(() => {}); return; }
    taskToken = session.accessToken; task.value = created;
    if (!image.value) { qrError.value = "二维码图片无效，请重试"; return; }
    schedule(version);
  } catch (e) { if (!disposed && version === generation) qrError.value = errorText(e); }
  finally { if (!disposed && version === generation) qrBusy.value = false; }
}
async function openDialog() {
  if (busy.value) return;
  busy.value = true; error.value = ""; notice.value = "";
  const version = generation;
  try {
    const caps = await qqMusic.capabilities();
    if (disposed || version !== generation) return;
    if (!caps.configured || caps.channels.length === 0) throw new Error("QQ 音乐连接服务暂不可用");
    channels.value = caps.channels; selected.value = caps.channels[0]!;
    dialog.value?.showModal(); await createQr();
  } catch (e) { if (!disposed && version === generation) error.value = errorText(e); }
  finally { if (!disposed) busy.value = false; }
}
async function disconnect() {
  if (busy.value) return;
  busy.value = true; error.value = ""; notice.value = "";
  stopTask();
  const version = generation;
  try {
    await qqMusic.disconnect();
    if (!disposed && version === generation) {
      connection.value = { configured: connection.value?.configured ?? true, connected: false, needsReconnect: false, nickname: null, connectedAt: null };
      notice.value = "已断开 QQ 音乐连接";
    }
  } catch (e) { if (!disposed && version === generation) error.value = errorText(e); }
  finally { if (!disposed) busy.value = false; }
}
function retryCheck() { if (task.value) { qrError.value = ""; void poll(generation); } }
watch(() => session.revision, () => {
  closeDialog(); connection.value = undefined; error.value = ""; notice.value = "";
  if (session.authenticated) void loadStatus();
});
onMounted(loadStatus);
onBeforeUnmount(() => { disposed = true; stopTask(); });
</script>

<template>
  <section class="qq-connection" aria-labelledby="qq-connection-title">
    <div class="qq-connection-heading"><div><span class="qq-eyebrow">音乐账号</span><h3 id="qq-connection-title">连接 QQ 音乐</h3></div><span class="qq-connection-state" :class="{ connected: connection?.connected }">{{ loading ? '查询中' : connection?.connected ? '已连接' : connection?.needsReconnect ? '需要重新连接' : '未连接' }}</span></div>
    <p v-if="loading">正在查询连接状态…</p>
    <p v-else-if="connection?.connected">已连接 {{ connection.nickname }}，可在这里管理账号连接。</p>
    <p v-else-if="connection?.needsReconnect">{{ connection.nickname || 'QQ 音乐账号' }} 的连接已失效，请重新扫码连接。</p>
    <p v-else-if="connection && !connection.configured">QQ 音乐连接暂未开放。</p>
    <p v-else>通过手机扫码连接你的 QQ 音乐账号。</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div class="qq-connection-actions">
      <RouterLink v-if="connection?.connected" class="primary" to="/qq-playlists">管理 QQ 歌单</RouterLink>
      <button v-if="error" :disabled="busy || loading" @click="loadStatus">重试查询</button>
      <button v-if="connection?.configured && !connection.connected" class="primary" :disabled="busy || loading" @click="openDialog">{{ busy ? '正在处理…' : connection.needsReconnect ? '重新连接' : '连接账号' }}</button>
      <button v-if="connection?.connected || connection?.needsReconnect" :disabled="busy || loading" @click="disconnect">{{ busy ? '正在处理…' : '断开连接' }}</button>
    </div>
    <dialog ref="dialog" class="qq-dialog" aria-labelledby="qq-dialog-title" @cancel.prevent="closeDialog" @close="(task || qrBusy) && stopTask()">
      <button class="dialog-close" type="button" aria-label="关闭 QQ 音乐连接" @click="closeDialog">×</button>
      <span class="qq-eyebrow">账号连接</span><h2 id="qq-dialog-title">扫码连接 QQ 音乐</h2>
      <label v-if="channels.length > 1" class="qq-channel-select">扫码方式<select v-model="selected" :disabled="qrBusy" @change="createQr"><option v-for="channel in channels" :key="channel" :value="channel">{{ channelLabels[channel] }}</option></select></label>
      <p v-else>请使用{{ channelLabels[selected] }}扫码</p>
      <div class="qq-qr-frame" :aria-busy="qrBusy"><p v-if="qrBusy" role="status">正在获取二维码…</p><img v-else-if="image && task?.state !== 'expired'" :src="image" alt="QQ 音乐账号连接二维码" /><p v-else>{{ task?.state === 'expired' ? '二维码已失效' : '暂时无法显示二维码' }}</p></div>
      <p v-if="task && !qrBusy" role="status">{{ task.message || hint }}</p>
      <p v-if="qrError" role="alert" class="form-error">{{ qrError }}</p>
      <div class="qq-connection-actions"><button v-if="qrError && task && task.state !== 'expired'" @click="retryCheck">重试检查</button><button :disabled="qrBusy" @click="createQr">刷新二维码</button><button @click="closeDialog">取消</button></div>
      <small>账号密码无需在本站输入。你可以随时在个人中心断开连接。</small>
    </dialog>
  </section>
</template>

<style scoped>
.qq-connection { margin-top: 26px; padding: 24px; border: 1px solid #dce9df; border-radius: 18px; background: #f8fcf9; color: #202824; }
.qq-connection-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.qq-eyebrow { color: #16854c; font-size: 12px; font-weight: 600; letter-spacing: .08em; }
.qq-connection h3 { margin: 5px 0 12px; font-size: 21px; }
.qq-connection p { line-height: 1.7; }
.qq-connection-state { padding: 5px 12px; border-radius: 20px; background: #f3f4f6; color: #666; font-size: 12px; white-space: nowrap; }
.qq-connection-state.connected { color: #157447; background: #e8f6ed; }
.qq-connection-actions { display: flex; flex-wrap: wrap; gap: 10px; margin: 16px 0 0; }
.qq-dialog { width: min(440px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); overflow: auto; box-sizing: border-box; border: 0; border-radius: 22px; padding: 30px; color: #202824; background: #fff; box-shadow: 0 22px 90px #0003; }
.qq-dialog::backdrop { background: #10251c80; }
.qq-dialog h2 { margin: 8px 0 12px; font-size: 24px; }
.qq-dialog small { display: block; margin-top: 20px; color: #65736b; line-height: 1.7; }
.qq-dialog .dialog-close { color: #65736b; }
.qq-qr-frame { display: grid; place-items: center; width: 232px; height: 232px; margin: 22px auto; padding: 12px; box-sizing: border-box; border: 1px solid #dce9df; border-radius: 12px; background: #fff; }
.qq-qr-frame img { display: block; width: 100%; height: 100%; object-fit: contain; }
.qq-channel-select { display: flex; align-items: center; gap: 12px; }
@media (max-width: 480px) { .qq-connection { padding: 18px; } .qq-dialog { padding: 24px; } }
</style>
