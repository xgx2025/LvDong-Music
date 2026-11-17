<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { qqMusic, loadAllQqSongs, type QqConnection, type QqPlaylist, type QqImportedPlaylist } from "../api/qqMusic";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "../components/AppIcon.vue";
import MusicLibraryNav from "../components/MusicLibraryNav.vue";
import QqMusicConnection from "../components/QqMusicConnection.vue";
import type { Song } from "../types";
import { usePlayerStore } from "../stores/player";
import { useSessionStore } from "../stores/session";
import { errorText } from "../api/servlet";
import CoverArt from "../components/CoverArt.vue";
import SongList from "../components/SongList.vue";

type Selection = { kind: "remote" | "imported"; id: string; name: string; sourceId: string };
const route = useRoute(), router = useRouter();
const accountPanel = ref<HTMLDetailsElement>();
function openAccount() { if (accountPanel.value) { accountPanel.value.open = true; accountPanel.value.scrollIntoView({ behavior: "smooth", block: "start" }); } }
const filter = ref<"all" | "liked" | "owned" | "collected">("all");
const session = useSessionStore(), player = usePlayerStore();
const connection = ref<QqConnection>(), connectionError = ref("");
const remote = ref<QqPlaylist[]>([]), imported = ref<QqImportedPlaylist[]>([]);
const tab = ref<"remote" | "imported">("remote"), selected = ref<Selection>();
const songs = ref<Song[]>([]), songTotal = ref(0), nextSongOffset = ref(0), moreSongs = ref(false);
const moreLists = ref(false), nextFavoriteOffset = ref<number | null>(null);
const listBusy = ref(false), songBusy = ref(false), importing = ref(false), queueBusy = ref(false);
const error = ref(""), detailError = ref(""), notice = ref(""), progress = ref("");
let listGeneration = 0, detailGeneration = 0, disposed = false;
let lifetime = new AbortController(), queueRequest: AbortController | undefined;
const visibleRemote = computed(() => remote.value.filter(item => filter.value === "all" || (filter.value === "liked" ? item.liked : filter.value === "owned" ? item.owned && !item.liked : !item.owned && !item.liked)));
function switchTab(value: "remote" | "imported") { tab.value = value; void router.replace({ query: { ...route.query, tab: value } }); }
watch(() => route.query.tab, value => { tab.value = value === "imported" ? "imported" : "remote"; }, { immediate: true });
watch(() => [route.query.playlist, remote.value] as const, ([id, items]) => {
  const item = items.find(entry => entry.id === id);
  if (item && (selected.value?.kind !== 'remote' || selected.value.id !== item.id)) choose('remote', item);
});
const count = computed(() => tab.value === "remote" ? remote.value.length : imported.value.length);
const valid = (revision: number) => !disposed && revision === session.revision;

async function loadImports() {
  const revision = session.revision;
  const data = await qqMusic.imports(lifetime.signal);
  if (valid(revision)) imported.value = data;
}
async function loadRemote(append = false) {
  if (listBusy.value) return;
  const version = ++listGeneration, revision = session.revision;
  listBusy.value = true; error.value = "";
  try {
    const data = await qqMusic.playlists(append ? nextFavoriteOffset.value || 0 : 0, lifetime.signal);
    if (!valid(revision) || version !== listGeneration) return;
    remote.value = [...new Map([...(append ? remote.value : []), ...data.items].map(item => [item.id, item])).values()];
    moreLists.value = data.more; nextFavoriteOffset.value = data.nextFavoriteOffset;
  } catch (e) { if (valid(revision) && version === listGeneration) error.value = errorText(e); }
  finally { if (valid(revision) && version === listGeneration) listBusy.value = false; }
}
async function initialize() {
  const revision = session.revision;
  error.value = ""; connectionError.value = "";
  const results = await Promise.allSettled([qqMusic.status(), loadImports()]);
  if (!valid(revision)) return;
  if (results[1].status === "rejected") error.value = errorText(results[1].reason);
  if (results[0].status === "fulfilled") {
    connection.value = results[0].value;
    if (connection.value.connected) await loadRemote();
    else tab.value = "imported";
  } else { connectionError.value = errorText(results[0].reason); tab.value = "imported"; }
}
async function connectionChanged() {
  await initialize();
  if (connection.value?.connected) switchTab("remote");
}
function choose(kind: Selection["kind"], item: QqPlaylist | QqImportedPlaylist) {
  queueRequest?.abort(); queueBusy.value = false; progress.value = "";
  detailGeneration++;
  selected.value = { kind, id: item.id, name: item.name, sourceId: "providerPlaylistId" in item ? item.providerPlaylistId : item.id };
  songs.value = []; songTotal.value = 0; nextSongOffset.value = 0; moreSongs.value = false;
  notice.value = ""; detailError.value = ""; songBusy.value = false;
  void loadSongs();
}
async function loadSongs(append = false) {
  if (!selected.value || songBusy.value) return;
  const target = selected.value, version = detailGeneration, revision = session.revision;
  songBusy.value = true; detailError.value = "";
  try {
    const data = await qqMusic.songs(target.kind, target.id, append ? nextSongOffset.value : 0, lifetime.signal);
    if (!valid(revision) || version !== detailGeneration) return;
    if (data.more && (data.items.length === 0 || data.nextOffset <= (append ? nextSongOffset.value : 0))) throw new Error("QQ 歌单未完整加载，请稍后重试");
    songs.value = [...new Map([...(append ? songs.value : []), ...data.items].map(item => [item.songId, item])).values()];
    songTotal.value = data.total; nextSongOffset.value = data.nextOffset; moreSongs.value = data.more;
  } catch (e) { if (valid(revision) && version === detailGeneration) detailError.value = errorText(e); }
  finally { if (valid(revision) && version === detailGeneration) songBusy.value = false; }
}
async function importSelected() {
  if (!selected.value || importing.value) return;
  const target = selected.value, revision = session.revision;
  importing.value = true; detailError.value = ""; notice.value = "";
  try {
    const result = await qqMusic.importPlaylist(target.sourceId);
    if (!valid(revision)) return;
    notice.value = `已保存「${result.name}」，共 ${result.songCount} 首歌曲`;
    await loadImports();
    if (selected.value?.kind === "imported" && selected.value.id === result.id) choose("imported", result);
  } catch (e) { if (valid(revision)) detailError.value = errorText(e); }
  finally { if (valid(revision)) importing.value = false; }
}
async function remove(item: QqImportedPlaylist) {
  const revision = session.revision;
  try {
    await qqMusic.removeImport(item.id);
    if (!valid(revision)) return;
    imported.value = imported.value.filter(entry => entry.id !== item.id);
    if (selected.value?.kind === "imported" && selected.value.id === item.id) {
      queueRequest?.abort(); detailGeneration++; selected.value = undefined; songs.value = []; queueBusy.value = false;
    }
    notice.value = "已移除本站导入，QQ 音乐原歌单保持不变";
  } catch (e) { if (valid(revision)) error.value = errorText(e); }
}
async function queueAll(play: boolean) {
  if (!selected.value || queueBusy.value) return;
  const target = selected.value, version = detailGeneration, revision = session.revision;
  queueRequest?.abort(); queueRequest = new AbortController();
  const request = queueRequest;
  queueBusy.value = true; detailError.value = ""; notice.value = "";
  try {
    const all = await loadAllQqSongs(target.kind, target.id, request.signal, count => { if (valid(revision) && version === detailGeneration) progress.value = `已读取 ${count} 首歌曲…`; });
    if (!valid(revision) || version !== detailGeneration || request.signal.aborted) return;
    const added = player.enqueueMany(all);
    notice.value = `已加载 ${all.length} 首歌曲，新增 ${added} 首到播放队列`;
    if (play && all.length) await player.select(player.enqueue(all[0]!));
  } catch (e) { if (valid(revision) && version === detailGeneration && !request.signal.aborted) detailError.value = errorText(e); }
  finally { if (queueRequest === request) { queueBusy.value = false; progress.value = ""; } }
}
watch(() => session.revision, () => {
  lifetime.abort(); lifetime = new AbortController(); queueRequest?.abort(); listGeneration++; detailGeneration++;
  connection.value = undefined; remote.value = []; imported.value = []; selected.value = undefined; songs.value = [];
  listBusy.value = songBusy.value = importing.value = queueBusy.value = false;
  error.value = detailError.value = notice.value = progress.value = connectionError.value = "";
  if (session.authenticated) void initialize();
});
onMounted(initialize);
onBeforeUnmount(() => { disposed = true; lifetime.abort(); queueRequest?.abort(); listGeneration++; detailGeneration++; });
</script>

<template>
  <section class="qq-library">
    <MusicLibraryNav />
    <header class="qq-library-heading"><div><span class="qq-library-eyebrow">你的音乐收藏</span><h1>你的收藏，在这里相遇<span class="qq-title-dot">。</span></h1><p>QQ 音乐里的喜欢，在律动继续听。</p></div><a class="qq-account-link" href="#qq-account" @click.prevent="openAccount"><span class="qq-status-dot" :class="{ connected: connection?.connected }"></span>{{ connection?.connected ? (connection.nickname || "QQ 音乐已连接") : "管理账号连接" }}</a></header>
    <details ref="accountPanel" id="qq-account" class="qq-account-panel" :open="connection && !connection.connected"><summary>QQ 音乐账号 <span>{{ connection?.connected ? "已连接 · 管理连接" : "扫码连接与账号管理" }}</span></summary><QqMusicConnection @changed="connectionChanged" /></details>
    <p v-if="connectionError" role="alert" class="form-error">{{ connectionError }} <button @click="initialize">重试连接查询</button></p>
    <p v-if="connection && !connection.connected" class="qq-library-callout">{{ connection.needsReconnect ? 'QQ 音乐连接已失效，请重新扫码。' : '连接 QQ 音乐账号后，可以读取远端歌单和播放歌曲。' }} 已导入的歌曲信息仍可查看。<a href="#qq-account" @click.prevent="openAccount">在上方连接账号</a></p>
    <p v-if="error" role="alert" class="form-error">{{ error }} <button @click="initialize">重试加载</button></p>
    <p v-if="notice" role="status" class="qq-library-notice">{{ notice }}</p>
    <div class="qq-library-layout">
      <aside class="qq-library-sidebar" aria-label="歌单列表">
        <div class="qq-library-tabs" role="tablist" aria-label="歌单来源"><button role="tab" :aria-selected="tab === 'remote'" @click="switchTab('remote')">QQ 账号歌单</button><button role="tab" :aria-selected="tab === 'imported'" @click="switchTab('imported')">已导入本站</button></div>
        <div class="qq-library-list-heading"><span>{{ count }} 个歌单</span><button :disabled="listBusy || (tab === 'remote' && !connection?.connected)" @click="tab === 'remote' ? loadRemote() : initialize()">刷新列表</button></div>
        <div v-if="tab === 'remote' && remote.length" class="qq-source-filters" role="group" aria-label="筛选 QQ 歌单"><button v-for="option in ([{ key: 'all', label: '全部' }, { key: 'liked', label: '我喜欢' }, { key: 'owned', label: '自建' }, { key: 'collected', label: '收藏' }] as const)" :key="option.key" :aria-pressed="filter === option.key" @click="filter = option.key">{{ option.label }}</button></div>
        <template v-if="tab === 'remote'">
          <p v-if="listBusy && !remote.length" role="status">正在读取 QQ 歌单…</p>
          <p v-else-if="!remote.length" class="subtle">{{ connection?.connected ? '暂时没有可显示的歌单' : '请先连接 QQ 音乐账号' }}</p>
          <button v-for="item in visibleRemote" :key="item.id" class="qq-library-card" :aria-pressed="selected?.kind === 'remote' && selected.id === item.id" @click="choose('remote', item)"><CoverArt :path="item.coverUrl" :name="item.name" /><span><strong>{{ item.name }}</strong><small>{{ item.liked ? '我喜欢' : item.owned ? '自建歌单' : '收藏歌单' }}{{ item.trackCount === null ? '' : ` · ${item.trackCount} 首` }}</small></span></button>
          <p v-if="remote.length && !visibleRemote.length" class="subtle">当前已加载的歌单中暂无此分类。</p>
          <button v-if="moreLists && nextFavoriteOffset !== null" :disabled="listBusy" @click="loadRemote(true)">{{ listBusy ? '加载中…' : '加载更多歌单' }}</button>
          <p v-else-if="moreLists" class="subtle">QQ 尚未返回完整歌单列表，请稍后刷新。</p>
        </template>
        <template v-else>
          <p v-if="!imported.length" class="subtle">尚未导入歌单。选择 QQ 账号歌单，点击“导入本站”。</p>
          <div v-for="item in imported" :key="item.id" class="qq-imported-row"><button class="qq-library-card" :aria-pressed="selected?.kind === 'imported' && selected.id === item.id" @click="choose('imported', item)"><CoverArt :path="item.coverUrl" :name="item.name" /><span><strong>{{ item.name }}</strong><small>{{ item.songCount }} 首 · {{ new Date(item.importedAt).toLocaleDateString('zh-CN') }} 导入</small></span></button><button class="qq-remove-import" :disabled="importing" :aria-label="`移除导入 ${item.name}`" @click="remove(item)">移除</button></div>
        </template>
      </aside>
      <div class="qq-library-detail" aria-label="歌单歌曲">
        <template v-if="selected">
          <div class="qq-detail-heading"><div><span class="qq-library-eyebrow">{{ selected.kind === 'remote' ? 'QQ 音乐' : '本站保存的歌单' }}</span><h2>{{ selected.name }}</h2><p>已显示 {{ songs.length }} 首<span v-if="songTotal"> · 共 {{ songTotal }} 首</span></p></div><div class="qq-detail-actions"><button :disabled="importing || !connection?.connected" @click="importSelected">{{ importing ? '正在完整导入…' : selected.kind === 'remote' ? '导入本站' : '更新导入' }}</button><button :disabled="queueBusy || !songs.length" @click="queueAll(false)">加载到队列</button><button class="primary" :disabled="queueBusy || !songs.length || !connection?.connected" @click="queueAll(true)">播放全部</button></div></div>
          <p class="subtle">导入后可在“已导入本站”找到这个歌单；播放需要保持 QQ 音乐连接，部分歌曲可能仅提供试听。</p>
          <p v-if="detailError" role="alert" class="form-error">{{ detailError }} <button :disabled="songBusy" @click="loadSongs()">重新加载歌曲</button></p>
          <p v-if="queueBusy" role="status">{{ progress || '正在完整加载歌单…' }}</p>
          <p v-if="songBusy" role="status">正在加载歌曲…</p>
          <SongList v-if="songs.length" :songs="songs" :qq-playback-available="!!connection?.connected" />
          <p v-else-if="!songBusy && !detailError" class="qq-no-songs">这个歌单还没有歌曲。</p>
          <button v-if="moreSongs" class="qq-more-songs" :disabled="songBusy" @click="loadSongs(true)">{{ songBusy ? '加载中…' : '加载更多歌曲' }}</button>
        </template>
        <div v-else class="qq-library-empty"><div class="qq-empty-record" aria-hidden="true"><AppIcon name="music" /></div><span>YOUR MUSIC, TOGETHER</span><h2>{{ connection?.connected ? '熟悉的歌，新的陪伴' : '让喜欢的音乐来到这里' }}</h2><p>{{ connection?.connected ? '从左侧选择一个歌单，开始播放或保存到律动。' : '在上方扫码连接 QQ 音乐，或查看已经导入的歌单。' }}</p><div class="qq-library-steps"><span><b>01</b> 选择歌单</span><span><b>02</b> 播放或导入</span><span><b>03</b> 在律动继续听</span></div></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.qq-library { max-width: 1360px; margin: 0 auto; padding: 20px 0 60px; }
.qq-library-heading { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-bottom: 26px; }
.qq-library-heading h1 { margin: 8px 0 12px; font-size: clamp(30px, 4vw, 44px); letter-spacing: -.04em; }
.qq-library-heading p { margin: 0; color: #61718a; line-height: 1.7; }
.qq-library-eyebrow { color: var(--primary); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.qq-library-heading > a { white-space: nowrap; }
.qq-library-layout { display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr); gap: 24px; align-items: start; }
.qq-library-sidebar { padding: 18px; border: 1px solid #dce5f1; border-radius: 18px; background: #ffffff80; }
.qq-library-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.qq-library-tabs button { padding: 10px 6px; font-size: 13px; }
.qq-library-tabs button[aria-selected="true"] { background: var(--primary); color: #fff; border-color: var(--primary); }
.qq-library-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 16px 0 12px; font-size: 12px; color: #61718a; }
.qq-library-list-heading button { padding: 6px 8px; font-size: 12px; }
.qq-library-card { display: flex; width: 100%; gap: 12px; align-items: center; text-align: left; padding: 12px 8px; border: 1px solid transparent; background: transparent; color: #263652; box-shadow: none; border-radius: 10px; }
.qq-library-card[aria-pressed="true"] { border-color: #b8c8f7; background: #edf2ff; }
.qq-library-card > span:last-child { min-width: 0; }
.qq-library-card strong { display: block; overflow-wrap: anywhere; font-size: 14px; }
.qq-library-card small { display: block; margin-top: 5px; color: #69788e; font-size: 12px; }
.qq-library-card :deep(.cover-art) { width: 48px; height: 48px; min-width: 48px; border-radius: 8px; }
.qq-imported-row { position: relative; padding-bottom: 18px; }
.qq-remove-import { display: block; margin-left: auto; border: 0; padding: 2px 8px; font-size: 12px; color: #8a554e; background: transparent; }
.qq-library-detail { min-width: 0; padding: 26px; background: #fff; border: 1px solid #e0e8f2; border-radius: 18px; }
.qq-detail-heading { display: flex; flex-wrap: wrap; gap: 18px; justify-content: space-between; align-items: center; }
.qq-detail-heading h2 { margin: 8px 0; overflow-wrap: anywhere; }
.qq-detail-heading p { color: #6b7b71; font-size: 13px; }
.qq-detail-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.qq-detail-actions button { font-size: 13px; }
.qq-library-detail > .subtle { line-height: 1.7; margin: 16px 0 24px; font-size: 12px; }
.qq-library-empty { padding: 54px 20px; text-align: center; color: #69788e; }
.qq-library-empty > span { font-size: 12px; letter-spacing: .22em; color: var(--primary); }
.qq-library-empty h2 { color: #263652; margin: 16px 0; font-size: 28px; }
.qq-library-callout, .qq-library-notice { padding: 14px 18px; border-radius: 12px; background: #edf2ff; line-height: 1.8; }
.qq-library-callout a { margin-left: 8px; }
.qq-no-songs { padding: 60px 10px; text-align: center; color: #69788e; }
.qq-more-songs { display: block; margin: 24px auto 0; }
.qq-library-detail :deep(.song-actions) { flex-wrap: wrap; }
.qq-library-detail :deep(.song-actions button) { font-size: 12px; }
@media(max-width: 900px) { .qq-library-layout { grid-template-columns: 240px minmax(0, 1fr); gap: 14px; } .qq-library-detail { padding: 18px; } .qq-library-detail :deep(.song-row) { grid-template-columns: 24px 40px minmax(0, 1fr) auto; } .qq-library-detail :deep(.song-album), .qq-library-detail :deep(.song-duration) { display: none; } }
@media(max-width: 640px) { .qq-library-heading { align-items: start; flex-direction: column; gap: 12px; } .qq-library-layout { grid-template-columns: minmax(0, 1fr); } .qq-library-sidebar { max-height: 380px; overflow: auto; } .qq-library-detail { padding: 16px; } .qq-library-empty { padding: 60px 12px; } }
</style>

<style scoped>
.qq-title-dot { color: var(--primary); }
.qq-account-link { display: inline-flex; align-items: center; gap: 9px; padding: 10px 15px; border: 1px solid #ffffffc0; border-radius: 30px; background: #ffffff80; font-size: 12px; }
.qq-status-dot { width: 7px; height: 7px; border-radius: 50%; background: #9aa6b7; flex-shrink: 0; }
.qq-status-dot.connected { background: #21865b; box-shadow: 0 0 0 4px #21865b12; }
.qq-account-panel { margin-bottom: 24px; border-block: 1px solid #b5c8df60; scroll-margin-top: 100px; }
.qq-account-panel summary { cursor: pointer; padding: 16px 4px; color: #354969; font-size: 13px; }
.qq-account-panel summary > span { margin-left: 14px; font-size: 12px; color: #69788e; }
.qq-account-panel :deep(.qq-connection) { margin: 0 0 18px; }
.qq-source-filters { display: flex; flex-wrap: wrap; gap: 5px; padding: 0 0 12px; }
.qq-source-filters button[aria-pressed="true"] { background: #edf2ff; color: var(--primary); border-color: #b8c8f7; }
.qq-source-filters button { padding: 5px 9px; border: 0; border-radius: 6px; font-size: 11px; background: transparent; color: #61718a; }
.qq-source-filters button[aria-pressed="true"] { background: #e7edfc; color: var(--primary); }
.qq-empty-record { display: grid; place-items: center; width: 114px; height: 114px; margin: 0 auto 30px; border-radius: 50%; border: 12px solid #edf2fa; outline: 1px solid #e4eaf4; background: #dee7f9; color: var(--primary); font-size: 30px; box-shadow: 0 12px 30px #23469012; }
.qq-library-empty p { line-height: 1.8; font-size: 13px; }
.qq-library-steps { display: flex; justify-content: center; flex-wrap: wrap; gap: 18px; margin-top: 32px; font-size: 11px; }
.qq-library-steps b { color: #7288b3; margin-right: 5px; font-variant-numeric: tabular-nums; }
.qq-library-detail :deep(.song-actions) { width: auto; }
@media(max-width: 480px) { .qq-library-heading h1 { font-size: 30px; } .qq-library-steps { gap: 10px; } }
</style>
