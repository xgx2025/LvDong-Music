<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { Song } from "../types";
import { servletRequest, errorText } from "../api/servlet";
import { usePlayerStore } from "../stores/player";
import { useFavoritesStore } from "../stores/favorites";
import { useSessionStore } from "../stores/session";
import CoverArt from "../components/CoverArt.vue";
import FavoriteButton from "../components/FavoriteButton.vue";
import LyricsPanel from "../components/LyricsPanel.vue";
import CommentsPanel from "../components/CommentsPanel.vue";
const route = useRoute(), player = usePlayerStore(), favorites = useFavoritesStore(), session = useSessionStore();
const song = ref<Song>(), loading = ref(false), error = ref("");
const id = computed(() => String(route.params.id || ""));
watch(id, async (value, _, cleanup) => {
  const controller = new AbortController(); cleanup(() => controller.abort());
  song.value = undefined; error.value = ""; loading.value = true;
  try {
    const result = await servletRequest<{ success: boolean; song: Song }>("searchServlet", "getSong", { songId: value }, "", controller.signal);
    if (!controller.signal.aborted) song.value = result.song;
  } catch (e) { if (!controller.signal.aborted) error.value = errorText(e); }
  finally { if (!controller.signal.aborted) loading.value = false; }
}, { immediate: true });
watch(() => [song.value?.songId, session.authenticated], () => { if (song.value) void favorites.load([song.value.songId]).catch(() => {}); });
</script>
<template><section><p v-if="loading" role="status">正在加载歌曲…</p><div v-else-if="error" class="empty-state" role="alert"><h2>歌曲暂时无法查看</h2><p>{{ error }}</p><RouterLink class="primary" to="/">返回首页</RouterLink></div><template v-else-if="song"><div class="song-detail settings-card"><CoverArt :path="song.coverUrl" :name="song.songName" /><div><h1>{{ song.songName }}</h1><p>{{ song.singers?.map(s => s.name).join(' / ') || '未知歌手' }}</p><p>{{ song.albumName || '单曲' }} · {{ song.duration || '未知时长' }}</p><div class="button-group"><button class="primary" @click="player.select(player.enqueue(song))">播放歌曲</button><button @click="player.enqueue(song)">加入队列</button><FavoriteButton :song-id="song.songId" /></div></div></div><LyricsPanel :path="song.lyricUrl" :active="player.current?.songId === song.songId" /><CommentsPanel :key="song.songId" :song-id="song.songId" /></template></section></template>
