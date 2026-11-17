<script setup lang="ts">
function scrollToSongs() { document.getElementById("search")?.scrollIntoView({ behavior: "smooth" }); }
import { onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { search } from "../api/search";
import type { SearchResult, Song } from "../types";
import { usePlayerStore } from "../stores/player";
import HomeDiscovery from "../components/HomeDiscovery.vue";
import CoverArt from "../components/CoverArt.vue";
import FavoriteButton from "../components/FavoriteButton.vue";
import { useFavoritesStore } from "../stores/favorites";
import { useSessionStore } from "../stores/session";
const favorites = useFavoritesStore(), session = useSessionStore();
const route = useRoute(),
  router = useRouter(),
  player = usePlayerStore();
const category = ref("songs");
const keyword = ref(""),
  result = ref<SearchResult>(),
  loading = ref(false),
  error = ref(""),
  notice = ref("");
let request: AbortController | undefined;
const pageNumber = () =>
  Math.min(2147483647, Math.max(1, Math.floor(Number(route.query.page)) || 1));
async function load() {
  request?.abort();
  request = new AbortController();
  const active = request;
  category.value = ["songs", "artists", "albums", "playlists"].includes(String(route.query.category)) ? String(route.query.category) : "songs";
  keyword.value = String(route.query.q || "").slice(0, 200);
  loading.value = true;
  error.value = "";
  result.value = undefined;
  try {
    const data = await search(keyword.value, pageNumber(), active.signal);
    if (!active.signal.aborted) result.value = data;
  } catch (e) {
    if (!active.signal.aborted)
      error.value = e instanceof Error ? e.message : "搜索失败，请重试";
  } finally {
    if (request === active) loading.value = false;
  }
}
function tabKey(event: KeyboardEvent, selected: string) {
  const keys = ['songs', 'albums', 'artists', 'playlists'];
  const direction = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
  if (!direction && !['Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  category.value = keys[event.key === 'Home' ? 0 : event.key === 'End' ? 3 : (keys.indexOf(selected) + direction + 4) % 4]!;
  document.getElementById(`tab-${category.value}`)?.focus();
}
function page(delta: number) {
  void router.push({ query: { ...route.query, page: pageNumber() + delta } });
}
function add(song: Song) {
  player.enqueue(song);
  notice.value = `已加入队列：${song.songName || "未命名歌曲"}`;
}
watch(() => route.fullPath, load, { immediate: true });
watch(() => [result.value, session.authenticated], () => {
  if (result.value) void favorites.load(result.value.songs.map(song => song.songId)).catch(() => {});
});
onBeforeUnmount(() => request?.abort());
</script>
<template>
  <template v-if="!route.query.q && !route.query.category">
    <section class="welcome">
      <h1>嗨，<span>{{ session.user?.username || "音乐爱好者" }}</span></h1>
      <p>今日为你推荐这些好音乐</p>
    </section>
    <section class="home-banner">
      <img src="https://picsum.photos/1200/400?random=1" alt="热门推荐" />
      <div class="banner-copy">
        <span>精选歌单</span>
        <h2>夏日限定 · 清凉节奏</h2>
        <p>32首适合夏日聆听的轻快旋律，陪你度过炎热午后</p>
      </div>
      <a class="banner-play" href="#" @click.prevent="scrollToSongs" aria-label="浏览推荐歌曲">▶</a>
    </section>
    <HomeDiscovery />
  </template>
  <section id="search" class="search-section">
    <div class="section-heading">
      <h2>{{ route.query.q || route.query.category ? '搜索结果' : '最新歌曲' }}</h2>
    </div>

    <div v-if="route.query.q || route.query.category" class="search-tabs" role="tablist" aria-label="搜索分类">
      <button v-for="tab in [{ key: 'songs', name: '歌曲', count: result?.songCount }, { key: 'albums', name: '专辑', count: result?.albumCount }, { key: 'artists', name: '歌手', count: result?.singerCount }, { key: 'playlists', name: '歌单', count: result?.playlistCount }]" :id="`tab-${tab.key}`" :key="tab.key" role="tab" :aria-selected="category === tab.key" :tabindex="category === tab.key ? 0 : -1" @keydown="tabKey($event, tab.key)" aria-controls="search-results" @click="category = tab.key">{{ tab.name }}<span>({{ tab.count || 0 }})</span></button>
    </div>
    <div class="results-heading">
      <span>{{ route.query.q ? `“${route.query.q}” 的搜索结果` : "已发布歌曲" }}</span
      ><span v-if="result">{{ result.songCount }} 首歌曲</span>
    </div>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div v-if="loading" class="empty-state" role="status">
      <span class="loading-disc" aria-hidden="true"></span>
      <h3>正在寻找好声音</h3>
      <p>连接公开曲库中…</p>
    </div>
    <div v-else-if="error" class="empty-state error-state" role="alert">
      <h3>暂时没能连接曲库</h3>
      <p>{{ error }}</p>
      <button class="primary" @click="load">重试搜索</button
      ><span class="subtle">请稍后重试，或联系站点维护者。</span>
    </div>
    <template v-else-if="result">
      <div id="search-results" role="tabpanel" :aria-labelledby="route.query.q || route.query.category ? `tab-${category}` : undefined">
      <div v-if="category === 'artists'" class="artist-results">
        <article v-for="singer in result.singers" :key="singer.id" class="discovery-card artist-card"><CoverArt :path="singer.avatar" :name="singer.name" /><h3>{{ singer.name || '未知歌手' }}</h3><RouterLink :to="{ path: '/', query: { q: singer.name } }">查看歌曲</RouterLink></article>
        <div v-if="!result.singers.length" class="empty-state"><h3>暂无相关歌手</h3><p>试试其他歌手名称。</p></div>
      </div>
      <div v-else-if="category !== 'songs'" class="empty-state"><h3>暂无相关{{ category === 'albums' ? '专辑' : '歌单' }}</h3><p>可以切换到歌曲分类继续查找。</p></div>
      <template v-else><div v-if="!result.songs.length" class="empty-state">
        <h3>暂时没有找到歌曲</h3>
        <p>换一个歌名或歌手名试试看。</p>
      </div>
      <div v-else class="song-list">
        <div class="song-table-head">
          <span>歌曲 / 歌手</span><span>专辑</span><span>时长</span
          ><span>操作</span>
        </div>
        <article
          v-for="(song, i) in result.songs"
          :key="song.songId"
          class="song-row"
        >
          <span class="row-number">{{
            ((pageNumber() - 1) * 20 + i + 1).toString().padStart(2, "0")
          }}</span
          ><CoverArt :path="song.coverUrl" :name="song.songName" />
          <div class="song-title">
            <RouterLink :to="`/song/${song.songId}`"><strong>{{ song.songName || "未命名歌曲" }}</strong></RouterLink
            ><small>{{
              song.singers.map((s) => s.name || "未知歌手").join(" / ") ||
              "未知歌手"
            }}</small>
          </div>
          <span class="song-album">{{ song.albumName || "单曲" }}</span
          ><span class="song-duration">{{ song.duration || "—" }}</span>
          <div class="song-actions">
            <button
              :aria-label="`播放 ${song.songName}`"
              @click="player.select(player.enqueue(song))"
            >
              播放</button
            ><button
              :aria-label="`加入队列 ${song.songName}`"
              @click="add(song)"
            >
              加入
            </button>
            <FavoriteButton :song-id="song.songId" />
          </div>
        </article>
      </div>
      <div v-if="result.songCount > 20 || pageNumber() > 1" class="pagination">
        <button :disabled="pageNumber() <= 1" @click="page(-1)">上一页</button
        ><span>第 {{ pageNumber() }} 页</span
        ><button
          :disabled="pageNumber() * 20 >= result.songCount"
          @click="page(1)"
        >
          下一页
        </button>
      </div>
      </template></div>
      <section v-if="category === 'songs' && result.singers.length" class="singer-section">
        <h3>相关歌手</h3>
        <div class="singer-list">
          <div v-for="singer in result.singers" :key="singer.id">
            <CoverArt :path="singer.avatar" :name="singer.name" /><span>{{
              singer.name || "未知歌手"
            }}</span>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>
