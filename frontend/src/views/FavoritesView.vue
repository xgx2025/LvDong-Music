<script setup lang="ts">
import MusicLibraryNav from "../components/MusicLibraryNav.vue";
import AppIcon from "../components/AppIcon.vue";
import { ref, watch } from "vue";
import { getFavorites } from "../api/account";
import { errorText } from "../api/servlet";
import { useFavoritesStore } from "../stores/favorites";
import SongList from "../components/SongList.vue";
import type { Song } from "../types";
const songs = ref<Song[]>([]), page = ref(1), total = ref(0), loading = ref(false), error = ref("");
const favorites = useFavoritesStore();
let generation = 0;
async function load() {
  const version = ++generation;
  loading.value = true; error.value = "";
  try {
    const data = (await getFavorites(page.value)).favorites;
    if (version !== generation) return;
    songs.value = data.list; total.value = data.total;
    for (const song of data.list) favorites.states[song.songId] = true;
  } catch (e) { if (version === generation) error.value = errorText(e); }
  finally { if (version === generation) loading.value = false; }
}
watch(page, load, { immediate: true });
watch(() => favorites.states, () => {
  const visible = songs.value.filter(song => favorites.states[song.songId] !== false);
  total.value = Math.max(0, total.value - (songs.value.length - visible.length));
  songs.value = visible;
}, { deep: true });
</script>
<template><section><MusicLibraryNav /><div class="section-heading"><h1><AppIcon name="heart" />我的收藏</h1><button :disabled="loading" @click="load"><AppIcon name="refresh" />刷新列表</button></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-if="loading" role="status">正在加载收藏…</p><template v-else><SongList v-if="songs.length" :songs="songs" /><div v-else class="empty-state"><h3>还没有收藏歌曲</h3><RouterLink class="primary" to="/">发现音乐</RouterLink></div><div class="pagination"><button :disabled="page <= 1" @click="page--">上一页</button><span>第 {{ page }} 页 · 共 {{ total }} 首</span><button :disabled="page * 20 >= total" @click="page++">下一页</button></div></template></section></template>
