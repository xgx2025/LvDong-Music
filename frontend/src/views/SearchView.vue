<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { search } from "../api/search";
import type { SearchResult, Song } from "../types";
import { usePlayerStore } from "../stores/player";
import CoverArt from "../components/CoverArt.vue";
const route = useRoute(),
  router = useRouter(),
  player = usePlayerStore();
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
function submit() {
  void router.push({ path: "/", query: { q: keyword.value.trim() } });
  if ((route.query.q || "") === keyword.value.trim() && !route.query.page)
    void load();
}
function page(delta: number) {
  void router.push({ query: { ...route.query, page: pageNumber() + delta } });
}
function add(song: Song) {
  player.enqueue(song);
  notice.value = `已加入队列：${song.songName || "未命名歌曲"}`;
}
watch(() => route.fullPath, load, { immediate: true });
onBeforeUnmount(() => request?.abort());
</script>
<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow">YOUR DAILY SOUNDTRACK</p>
      <h1>此刻，<br />让旋律接管。</h1>
      <p class="hero-description">
        熟悉的旋律，未曾听见的声音。<br />在这里，找到属于你的下一首。
      </p>
      <a class="light-button" href="#search">寻找一首歌</a>
    </div>
    <div class="record-scene" aria-hidden="true">
      <div class="record-sleeve">
        <span>律动<br /><em>日常的回响</em></span
        ><small>SIDE A / EVERYDAY SOUNDS</small>
      </div>
      <div class="vinyl">
        <div class="vinyl-label">律动<span>33⅓ RPM</span></div>
      </div>
      <span class="scene-caption"
        >A LITTLE MUSIC. A LITTLE ROOM TO BREATHE.</span
      >
    </div>
  </section>
  <section id="search" class="search-section">
    <div class="section-heading">
      <div>
        <p class="eyebrow">DISCOVER</p>
        <h2>发现好声音<span class="heading-dot">.</span></h2>
      </div>
      <span class="subtle">公开曲库 · 歌曲与歌手</span>
    </div>
    <form class="search-form" @submit.prevent="submit">
      <label for="keyword" class="sr-only">歌曲、歌手或专辑关键词</label
      ><input
        id="keyword"
        v-model="keyword"
        maxlength="200"
        placeholder="搜索歌曲、歌手或专辑名称"
        type="search"
      /><button class="primary" type="submit">搜索音乐</button>
    </form>
    <div class="results-heading">
      <span>{{
        route.query.q ? `“${route.query.q}” 的搜索结果` : "已发布歌曲"
      }}</span
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
      ><RouterLink to="/settings">查看连接说明</RouterLink>
    </div>
    <template v-else-if="result"
      ><div v-if="!result.songs.length" class="empty-state">
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
            <strong>{{ song.songName || "未命名歌曲" }}</strong
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
      <section v-if="result.singers.length" class="singer-section">
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
