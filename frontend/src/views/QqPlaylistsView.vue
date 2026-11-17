<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { qqMusic, loadAllQqSongs, type QqConnection, type QqPlaylist, type QqImportedPlaylist } from "../api/qqMusic";
import type { Song } from "../types";
import { usePlayerStore } from "../stores/player";
import { useSessionStore } from "../stores/session";
import { errorText } from "../api/servlet";
import CoverArt from "../components/CoverArt.vue";
import SongList from "../components/SongList.vue";

type Selection = { kind: "remote" | "imported"; id: string; name: string; sourceId: string };
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
    <header class="qq-library-heading"><div><span class="qq-library-eyebrow">你的音乐收藏</span><h1>QQ 歌单</h1><p>从 QQ 音乐读取歌单，保存到本站，随时加载到播放队列。</p></div><RouterLink to="/profile">管理账号连接</RouterLink></header>
    <p v-if="connectionError" role="alert" class="form-error">{{ connectionError }} <button @click="initialize">重试连接查询</button></p>
    <p v-if="connection && !connection.connected" class="qq-library-callout">{{ connection.needsReconnect ? 'QQ 音乐连接已失效，请重新扫码。' : '连接 QQ 音乐账号后，可以读取远端歌单和播放歌曲。' }} 已导入的歌曲信息仍可查看。<RouterLink to="/profile">前往连接</RouterLink></p>
    <p v-if="error" role="alert" class="form-error">{{ error }} <button @click="initialize">重试加载</button></p>
    <p v-if="notice" role="status" class="qq-library-notice">{{ notice }}</p>
    <div class="qq-library-layout">
      <aside class="qq-library-sidebar" aria-label="歌单列表">
        <div class="qq-library-tabs" role="tablist" aria-label="歌单来源"><button role="tab" :aria-selected="tab === 'remote'" @click="tab = 'remote'">QQ 账号歌单</button><button role="tab" :aria-selected="tab === 'imported'" @click="tab = 'imported'">已导入本站</button></div>
        <div class="qq-library-list-heading"><span>{{ count }} 个歌单</span><button :disabled="listBusy || (tab === 'remote' && !connection?.connected)" @click="tab === 'remote' ? loadRemote() : initialize()">刷新列表</button></div>
        <template v-if="tab === 'remote'">
          <p v-if="listBusy && !remote.length" role="status">正在读取 QQ 歌单…</p>
          <p v-else-if="!remote.length" class="subtle">{{ connection?.connected ? '暂时没有可显示的歌单' : '请先连接 QQ 音乐账号' }}</p>
          <button v-for="item in remote" :key="item.id" class="qq-library-card" :aria-pressed="selected?.kind === 'remote' && selected.id === item.id" @click="choose('remote', item)"><CoverArt :path="item.coverUrl" :name="item.name" /><span><strong>{{ item.name }}</strong><small>{{ item.liked ? '我喜欢' : item.owned ? '自建歌单' : '收藏歌单' }}{{ item.trackCount === null ? '' : ` · ${item.trackCount} 首` }}</small></span></button>
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
          <div class="qq-detail-heading"><div><span class="qq-library-eyebrow">{{ selected.kind === 'remote' ? 'QQ 音乐' : '本站保存的歌单' }}</span><h2>{{ selected.name }}</h2><p>已显示 {{ songs.length }} 首<span v-if="songTotal"> · 共 {{ songTotal }} 首</span></p></div><div class="qq-detail-actions"><button class="primary" :disabled="importing || !connection?.connected" @click="importSelected">{{ importing ? '正在完整导入…' : selected.kind === 'remote' ? '导入本站' : '更新导入' }}</button><button :disabled="queueBusy || !songs.length" @click="queueAll(false)">加载到队列</button><button :disabled="queueBusy || !songs.length || !connection?.connected" @click="queueAll(true)">播放全部</button></div></div>
          <p class="subtle">导入保存歌曲信息，播放时使用当前连接的 QQ 账号获取音频。再次导入会更新同一账号下的同一歌单。</p>
          <p v-if="detailError" role="alert" class="form-error">{{ detailError }} <button :disabled="songBusy" @click="loadSongs()">重新加载歌曲</button></p>
          <p v-if="queueBusy" role="status">{{ progress || '正在完整加载歌单…' }}</p>
          <p v-if="songBusy" role="status">正在加载歌曲…</p>
          <SongList v-if="songs.length" :songs="songs" />
          <p v-else-if="!songBusy && !detailError" class="qq-no-songs">这个歌单还没有歌曲。</p>
          <button v-if="moreSongs" class="qq-more-songs" :disabled="songBusy" @click="loadSongs(true)">{{ songBusy ? '加载中…' : '加载更多歌曲' }}</button>
        </template>
        <div v-else class="qq-library-empty"><span>QQ MUSIC</span><h2>让收藏来到这里</h2><p>选择一个歌单，查看歌曲并导入本站。</p></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.qq-library { max-width: 1360px; margin: 0 auto; padding: 20px 0 60px; }
.qq-library-heading { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-bottom: 26px; }
.qq-library-heading h1 { margin: 8px 0 12px; font-size: clamp(30px, 4vw, 44px); letter-spacing: -.04em; }
.qq-library-heading p { margin: 0; color: #62736b; line-height: 1.7; }
.qq-library-eyebrow { color: #16854c; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.qq-library-heading > a { white-space: nowrap; }
.qq-library-layout { display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr); gap: 24px; align-items: start; }
.qq-library-sidebar { padding: 18px; border: 1px solid #dae8df; border-radius: 18px; background: #f8fcf9; }
.qq-library-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.qq-library-tabs button { padding: 10px 6px; font-size: 13px; }
.qq-library-tabs button[aria-selected="true"] { background: #176b43; color: #fff; border-color: #176b43; }
.qq-library-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 16px 0 12px; font-size: 12px; color: #617369; }
.qq-library-list-heading button { padding: 6px 8px; font-size: 12px; }
.qq-library-card { display: flex; width: 100%; gap: 12px; align-items: center; text-align: left; padding: 12px 8px; border: 1px solid transparent; background: transparent; color: #25352c; box-shadow: none; border-radius: 10px; }
.qq-library-card[aria-pressed="true"] { border-color: #add7ba; background: #e9f5ed; }
.qq-library-card > span:last-child { min-width: 0; }
.qq-library-card strong { display: block; overflow-wrap: anywhere; font-size: 14px; }
.qq-library-card small { display: block; margin-top: 5px; color: #687b70; font-size: 12px; }
.qq-library-card :deep(.cover-art) { width: 48px; height: 48px; min-width: 48px; border-radius: 8px; }
.qq-imported-row { position: relative; padding-bottom: 18px; }
.qq-remove-import { display: block; margin-left: auto; border: 0; padding: 2px 8px; font-size: 12px; color: #8a554e; background: transparent; }
.qq-library-detail { min-width: 0; padding: 26px; background: #fff; border: 1px solid #e4ebe6; border-radius: 18px; }
.qq-detail-heading { display: flex; flex-wrap: wrap; gap: 18px; justify-content: space-between; align-items: center; }
.qq-detail-heading h2 { margin: 8px 0; overflow-wrap: anywhere; }
.qq-detail-heading p { color: #6b7b71; font-size: 13px; }
.qq-detail-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.qq-detail-actions button { font-size: 13px; }
.qq-library-detail > .subtle { line-height: 1.7; margin: 16px 0 24px; font-size: 12px; }
.qq-library-empty { padding: 100px 20px; text-align: center; color: #687b70; }
.qq-library-empty > span { font-size: 12px; letter-spacing: .22em; color: #16854c; }
.qq-library-empty h2 { color: #253b2e; margin: 16px 0; font-size: 28px; }
.qq-library-callout, .qq-library-notice { padding: 14px 18px; border-radius: 12px; background: #e9f5ed; line-height: 1.8; }
.qq-library-callout a { margin-left: 8px; }
.qq-no-songs { padding: 60px 10px; text-align: center; color: #687b70; }
.qq-more-songs { display: block; margin: 24px auto 0; }
.qq-library-detail :deep(.song-actions) { flex-wrap: wrap; }
.qq-library-detail :deep(.song-actions button) { font-size: 12px; }
@media(max-width: 900px) { .qq-library-layout { grid-template-columns: 240px minmax(0, 1fr); gap: 14px; } .qq-library-detail { padding: 18px; } .qq-library-detail :deep(.song-row) { grid-template-columns: 24px 40px minmax(0, 1fr) auto; } .qq-library-detail :deep(.song-album), .qq-library-detail :deep(.song-duration) { display: none; } }
@media(max-width: 640px) { .qq-library-heading { align-items: start; flex-direction: column; gap: 12px; } .qq-library-layout { grid-template-columns: minmax(0, 1fr); } .qq-library-sidebar { max-height: 380px; overflow: auto; } .qq-library-detail { padding: 16px; } .qq-library-empty { padding: 60px 12px; } }
</style>
