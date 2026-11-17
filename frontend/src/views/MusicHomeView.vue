<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { qqMusic, type QqConnection, type QqPlaylist } from "../api/qqMusic";
import { useSessionStore } from "../stores/session";
import { usePlayerStore } from "../stores/player";
import type { Song } from "../types";
import { errorText } from "../api/servlet";
import AppIcon from "../components/AppIcon.vue";
import CoverArt from "../components/CoverArt.vue";
import SongList from "../components/SongList.vue";
import QqMusicConnection from "../components/QqMusicConnection.vue";
const session = useSessionStore(), player = usePlayerStore();
const connection = ref<QqConnection>(), statusError = ref("");
const daily = ref<Song[]>([]), playlists = ref<QqPlaylist[]>([]);
const loading = ref(true), dailyBusy = ref(false), playlistBusy = ref(false);
const dailyError = ref(""), playlistError = ref(""), notice = ref("");
const expanded = ref(false);
let generation = 0, controller = new AbortController();
const personalPlaylists = computed(() => playlists.value.filter(item => !item.liked).slice(0, 5));
const dailyVisible = computed(() => expanded.value ? daily.value : daily.value.slice(0, 6));
const date = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'Asia/Shanghai' }).format(new Date());
async function load() {
  controller.abort(); controller = new AbortController();
  const version = ++generation, signal = controller.signal;
  const valid = () => generation === version && !signal.aborted;
  loading.value = true; dailyBusy.value = playlistBusy.value = false;
  connection.value = undefined; daily.value = []; playlists.value = [];
  statusError.value = dailyError.value = playlistError.value = notice.value = ""; expanded.value = false;
  if (!session.authenticated) { loading.value = false; return; }
  try {
    const status = await qqMusic.status();
    if (!valid()) return;
    connection.value = status; loading.value = false;
    if (!status.connected) return;
    dailyBusy.value = playlistBusy.value = true;
    await Promise.allSettled([
      qqMusic.dailySongs(signal).then(data => { if (valid()) daily.value = data; }).catch(e => { if (valid()) dailyError.value = errorText(e); }).finally(() => { if (valid()) dailyBusy.value = false; }),
      qqMusic.playlists(0, signal).then(data => { if (valid()) playlists.value = data.items; }).catch(e => { if (valid()) playlistError.value = errorText(e); }).finally(() => { if (valid()) playlistBusy.value = false; }),
    ]);
  } catch (e) { if (valid()) statusError.value = errorText(e); }
  finally { if (valid()) loading.value = false; }
}
function playDaily() {
  if (!daily.value.length || !connection.value?.connected) return;
  player.enqueueMany(daily.value);
  void player.select(player.enqueue(daily.value[0]!));
  notice.value = `已将今日推荐的 ${daily.value.length} 首歌曲加入队列`;
}
watch(() => session.revision, load, { immediate: true });
onBeforeUnmount(() => { generation++; controller.abort(); });
</script>
<template>
  <div class="music-home">
    <header class="home-intro"><div><span class="home-date">{{ date }}</span><h1>让今天，有自己的旋律<span>。</span></h1><p>{{ connection?.connected ? `${connection.nickname || session.user?.username || '音乐爱好者'}，你的音乐已在这里。` : '从你的音乐账号出发，遇见今天想听的歌。' }}</p></div><span v-if="connection?.connected" class="home-provider"><i></i>QQ 音乐已连接</span></header>
    <p v-if="statusError" class="home-error" role="alert">{{ statusError }} <button @click="load">重新加载</button></p>
    <div v-if="loading" class="home-loading" role="status"><span class="loading-disc"></span>正在连接你的音乐世界…</div>
    <template v-else-if="connection?.connected">
      <div class="home-feature-grid">
        <section class="daily-feature" aria-labelledby="daily-title" :aria-busy="dailyBusy">
          <div class="daily-copy"><span class="home-overline">DAILY MIX / 每日推荐</span><h2 id="daily-title">今天的心动，<br />从这一首开始。</h2><p>{{ dailyBusy ? '正在读取 QQ 音乐为你准备的推荐…' : daily.length ? `来自你的 QQ 音乐 · ${daily.length} 首专属日常` : '等待 QQ 音乐为你准备新的旋律' }}</p><button class="daily-play" :disabled="dailyBusy || !daily.length" @click="playDaily">{{ dailyBusy ? '正在加载…' : '播放今日推荐' }}</button><span class="daily-source">每日推荐来自当前连接的 QQ 音乐账号</span></div>
          <div class="daily-artwork" aria-hidden="true"><div class="daily-vinyl"></div><CoverArt v-if="daily.length" :path="daily[0]?.coverUrl" :name="daily[0]?.albumName || daily[0]?.songName" /><span v-else class="daily-artwork-placeholder"><AppIcon name="music" /></span><span v-if="daily[0]" class="daily-artwork-caption">{{ daily[0].songName }}</span></div>
        </section>
        <aside class="home-personal-space" aria-labelledby="personal-space-title"><span class="home-overline">IN YOUR SPACE</span><h2 id="personal-space-title">喜欢的，都在身边。</h2><p>在熟悉的收藏里，找回自己的节奏。</p><RouterLink to="/qq-playlists"><span class="home-shortcut-icon"><AppIcon name="music" /></span><span><strong>我的 QQ 歌单</strong><small>我喜欢 · 自建 · 收藏</small></span></RouterLink><RouterLink to="/favorites"><span class="home-shortcut-icon"><AppIcon name="heart" /></span><span><strong>本站收藏</strong><small>留住在律动遇见的好歌</small></span></RouterLink><RouterLink class="home-account-link" to="/profile">管理音乐账号</RouterLink></aside>
      </div>
      <p v-if="notice" class="home-notice" role="status">{{ notice }} <RouterLink to="/queue">查看播放队列</RouterLink></p>
      <section class="home-playlists" aria-labelledby="your-playlists-title"><div class="home-section-heading"><div><span class="home-overline">YOUR COLLECTION</span><h2 id="your-playlists-title">你的歌单，你的频率</h2></div><RouterLink to="/qq-playlists">查看全部歌单</RouterLink></div>
        <p v-if="playlistError" class="home-error" role="alert">{{ playlistError }} <button @click="load">重新加载</button></p>
        <div v-if="playlistBusy" class="home-playlist-grid" role="status" aria-label="正在加载 QQ 歌单"><div v-for="i in 5" :key="i" class="home-card-skeleton"></div></div>
        <div v-else-if="personalPlaylists.length" class="home-playlist-grid"><RouterLink v-for="item in personalPlaylists" :key="item.id" class="home-playlist-card" :to="{ path: '/qq-playlists', query: { playlist: item.id } }"><div class="home-playlist-art"><CoverArt :path="item.coverUrl" :name="item.name" /><span class="home-playlist-type">{{ item.owned ? '自建' : '收藏' }}</span></div><h3>{{ item.name }}</h3><p>QQ 音乐<span v-if="item.trackCount !== null"> · {{ item.trackCount }} 首</span></p></RouterLink></div>
        <div v-else-if="!playlistError" class="home-empty-inline">这里还没有自建或收藏的 QQ 歌单。<RouterLink :to="{ path: '/qq-playlists', query: { playlist: 'liked' } }">听听我喜欢</RouterLink></div>
      </section>
      <section class="home-daily-songs" aria-labelledby="daily-songs-title"><div class="home-section-heading"><div><span class="home-overline">PICKED FOR YOU</span><h2 id="daily-songs-title">今日推荐</h2></div><button v-if="daily.length > 6" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '收起歌曲' : `查看全部 ${daily.length} 首` }}</button></div>
        <p v-if="dailyError" class="home-error" role="alert">{{ dailyError }} <button @click="load">重新加载</button></p><p v-else-if="dailyBusy" class="subtle" role="status">正在读取你的每日推荐…</p><SongList v-else-if="daily.length" :songs="dailyVisible" /><p v-else class="home-empty-inline">QQ 音乐暂未返回今日推荐，稍后再来听听。</p>
      </section>
      <footer class="home-source-note">内容来自你已连接的 QQ 音乐账号 · 可播放内容以平台与账号权限为准</footer>
    </template>
    <section v-else-if="connection" class="home-connect" aria-labelledby="home-connect-title"><div class="home-connect-intro"><span class="home-overline">YOUR MUSIC STARTS HERE</span><h2 id="home-connect-title">连接你的音乐，<br />听见你的日常。</h2><p>用自己的 QQ 音乐账号扫码，<br />让每日推荐和收藏歌单在这里相遇。</p></div><QqMusicConnection @changed="load" /></section>
  </div>
</template>
<style scoped>
.music-home { color: #243249; }
.home-intro { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-bottom: 32px; }
.home-date { color: #69778b; font-size: 12px; letter-spacing: .04em; }
.home-intro h1 { margin: 12px 0 10px; font-size: clamp(26px, 3vw, 36px); letter-spacing: -.05em; font-weight: 650; line-height: 1.35; }
.home-intro h1 > span { color: #536fc4; }
.home-intro p { margin: 0; color: #6d7b90; font-size: 13px; }
.home-provider { display: inline-flex; align-items: center; gap: 7px; color: #708178; font-size: 11px; white-space: nowrap; }
.home-provider i { width: 5px; height: 5px; border-radius: 50%; background: #3f9774; }
.home-feature-grid { display: grid; grid-template-columns: minmax(0, 2.15fr) minmax(260px, 1fr); gap: 24px; }
.home-overline { font-size: 10px; font-weight: 600; letter-spacing: .14em; color: #718098; }
.daily-feature { display: flex; align-items: center; position: relative; min-height: 330px; padding: 38px; border-radius: 22px; overflow: hidden; background: #253953; color: #fff; }
.daily-feature::before { content: ''; position: absolute; inset: 0; background: radial-gradient(ellipse at 90% 20%, #7396ae33, transparent 65%); pointer-events: none; }
.daily-copy { position: relative; z-index: 1; width: 61%; }
.daily-copy .home-overline { color: #a6b9ce; }
.daily-copy h2 { margin: 22px 0 16px; font-size: clamp(26px, 3vw, 37px); line-height: 1.4; letter-spacing: -.025em; font-weight: 500; }
.daily-copy p { color: #bdcada; font-size: 12px; line-height: 1.8; }
.daily-play { margin-top: 12px; padding: 12px 23px; border: 1px solid #ffffff90; border-radius: 8px; background: #fff; color: #293d59; font-size: 12px; font-weight: 600; }
.daily-play:hover:not(:disabled) { background: #e6edf9; }
.daily-source { display: block; margin-top: 18px; color: #8396ae; font-size: 9px; }
.daily-artwork { position: absolute; right: 5%; width: 29%; aspect-ratio: 1; transform: rotate(8deg); }
.daily-artwork :deep(.cover-art), .daily-artwork-placeholder { position: relative; width: 100%; height: 100%; border-radius: 6px; box-shadow: 0 16px 36px #0c172950; overflow: hidden; }
.daily-artwork :deep(.cover-fallback) { background: #849aa8; color: #e8edf0; font-size: 64px; font-family: Georgia, 'Songti SC', serif; }
.daily-artwork-placeholder { display: grid; place-items: center; color: #99aec2; background: #3b506a; font-size: 50px; }
.daily-vinyl { position: absolute; top: 4%; right: -23%; width: 91%; height: 91%; border-radius: 50%; background: repeating-radial-gradient(circle, #172538 0, #172538 2px, #1f3045 3px, #1f3045 4px); border: 5px solid #1a2a3c; }
.daily-vinyl::after { content: ''; position: absolute; inset: 36%; border-radius: 50%; background: #8c9aab; border: 17px solid #344b68; }
.daily-artwork-caption { display: block; margin-top: 12px; font-size: 10px; text-align: center; color: #adbed1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.home-personal-space { padding: 28px; background: #eef1f5; border: 1px solid #e6eaf0; border-radius: 22px; }
.home-personal-space h2 { margin: 13px 0 10px; font-size: 20px; letter-spacing: -.04em; font-weight: 600; }
.home-personal-space > p { margin: 0 0 20px; font-size: 11px; color: #6f7d92; line-height: 1.8; }
.home-personal-space > a:not(.home-account-link) { display: flex; align-items: center; gap: 12px; padding: 12px 0; }
.home-shortcut-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: #fff; color: #7488ad; }
.home-shortcut-icon .app-icon { margin: 0; }
.home-personal-space strong { display: block; font-size: 12px; font-weight: 500; }
.home-personal-space small { display: block; margin-top: 5px; color: #6f7d92; font-size: 11px; }
.home-account-link { display: inline-block; margin-top: 17px; color: #7385a3; font-size: 10px; }
.home-section-heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; margin: 38px 0 22px; }
.home-section-heading h2 { margin-top: 8px; font-size: 21px; letter-spacing: -.04em; font-weight: 600; }
.home-section-heading > a, .home-section-heading > button { padding: 4px 0; border: 0; background: transparent; color: #64748b; font-size: 12px; white-space: nowrap; }
.home-section-heading > a:hover, .home-section-heading > button:hover { color: #314db5; }
.home-playlist-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 24px; }
.home-playlist-art { position: relative; aspect-ratio: 1; border-radius: 13px; overflow: hidden; box-shadow: 0 5px 15px #2432490a; }
.home-playlist-art :deep(.cover-art) { width: 100%; height: 100%; border-radius: 0; transition: transform .3s; }
.home-playlist-card:hover :deep(.cover-art) { transform: scale(1.04); }
.home-playlist-art :deep(.cover-fallback) { background: #e2e7ee; color: #a4b1c3; font-size: 52px; font-family: Georgia, 'Songti SC', serif; }
.home-playlist-card:nth-child(3n+2) :deep(.cover-fallback) { background: #e1e9e5; color: #9aafa4; }
.home-playlist-card:nth-child(3n) :deep(.cover-fallback) { background: #ebe5df; color: #b4a394; }
.home-playlist-type { position: absolute; bottom: 9px; left: 9px; padding: 4px 7px; border-radius: 4px; background: #25395380; color: #fff; font-size: 9px; backdrop-filter: blur(8px); }
.home-playlist-card h3 { margin: 13px 0 7px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 14px; font-weight: 500; }
.home-playlist-card p { margin: 0; color: #6f7d92; font-size: 12px; }
.home-daily-songs :deep(.song-list) { border: 1px solid #e9edf3; background: #fff; border-radius: 14px; backdrop-filter: none; }
.home-daily-songs :deep(.song-row) { border-bottom: 1px solid #f0f2f6; padding: 15px 22px; }
.home-daily-songs :deep(.song-row:hover) { background: #f7f9fd; }
.home-daily-songs :deep(.song-title strong) { font-size: 13px; }
.home-daily-songs :deep(.song-title small) { font-size: 11px; color: #6f7d92; }
.home-daily-songs :deep(.song-album), .home-daily-songs :deep(.song-duration) { color: #6f7d92; font-size: 12px; }
.home-daily-songs :deep(.song-actions button) { background: #f7f9fc; border-color: #e9edf3; color: #7181a0; font-size: 10px; }
.home-daily-songs :deep(.song-actions) { width: auto; }
.home-loading { display: flex; align-items: center; justify-content: center; gap: 16px; min-height: 330px; color: #8a96a9; font-size: 13px; }
.home-card-skeleton { aspect-ratio: 1; border-radius: 13px; background: #e9edf2; animation: home-pulse 1.5s ease-in-out infinite alternate; }
.home-empty-inline, .home-error { padding: 20px; border: 1px solid #e5e9f0; border-radius: 10px; background: #fff; color: #8a94a5; font-size: 12px; line-height: 1.8; }
.home-empty-inline a, .home-error button { margin-left: 12px; color: #526da9; }
.home-error button { padding: 4px 8px; border: 0; background: #eef2fa; font-size: 11px; }
.home-notice { color: #537768; font-size: 12px; padding: 10px 0; }
.home-notice a { margin-left: 12px; color: #526da9; }
.home-source-note { margin-top: 40px; color: #6f7d92; font-size: 11px; text-align: center; }
.home-connect { display: grid; grid-template-columns: 1fr 1fr; gap: 50px; align-items: center; padding: 50px; border: 1px solid #e5e9f0; border-radius: 22px; background: #fff; }
.home-connect-intro h2 { margin: 18px 0; font-size: 36px; line-height: 1.5; font-weight: 500; letter-spacing: -.03em; }
.home-connect-intro p { color: #8d98a9; font-size: 13px; line-height: 2; }
.home-connect :deep(.qq-connection) { margin: 0; }
@keyframes home-pulse { from { opacity: 1; } to { opacity: .45; } }
@media(max-width: 1000px) { .home-feature-grid { grid-template-columns: minmax(0, 1.8fr) minmax(230px, 1fr); gap: 18px; } .daily-feature { padding: 28px; } .daily-copy h2 { font-size: 28px; } .home-personal-space { padding: 24px; } .home-playlist-grid { gap: 18px; } }
@media(max-width: 760px) { .home-feature-grid { grid-template-columns: 1fr; } .home-personal-space { display: none; } .daily-feature { min-height: 310px; } .home-playlist-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .home-provider { display: none; } .home-connect { grid-template-columns: 1fr; padding: 28px; gap: 20px; } .home-connect-intro h2 { font-size: 28px; } }
@media(max-width: 480px) { .home-intro { margin-bottom: 24px; } .home-intro h1 { font-size: 25px; } .home-intro p { font-size: 12px; line-height: 1.8; } .daily-feature { padding: 24px; min-height: 290px; border-radius: 16px; } .daily-copy { width: 66%; } .daily-copy h2 { font-size: 25px; margin-block: 18px 12px; } .daily-copy p { font-size: 11px; } .daily-source { max-width: 190px; line-height: 1.8; font-size: 8px; } .daily-artwork { width: 29%; right: 4%; } .daily-artwork-caption { display: none; } .home-section-heading h2 { font-size: 19px; } .home-section-heading { margin-top: 30px; } .home-playlist-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 16px; } .home-playlist-card:nth-child(n+5) { display: none; } .home-playlist-card h3 { font-size: 12px; } .home-daily-songs :deep(.song-row) { padding: 14px 12px; gap: 10px; } .home-source-note { font-size: 9px; line-height: 1.8; } }
@media(prefers-reduced-motion: reduce) { .home-card-skeleton { animation: none; } .home-playlist-art :deep(.cover-art) { transition: none; } }
</style>
