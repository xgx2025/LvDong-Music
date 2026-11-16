<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useFavoritesStore } from "../stores/favorites";
import { useSessionStore } from "../stores/session";
import { errorText } from "../api/servlet";
const props = defineProps<{ songId: string }>();
const favorites = useFavoritesStore(), session = useSessionStore(), router = useRouter(), route = useRoute();
const error = ref("");
async function toggle() {
  if (!session.authenticated) { await router.push({ path: "/login", query: { redirect: route.fullPath } }); return; }
  error.value = "";
  try { await favorites.toggle(props.songId); } catch (e) { error.value = errorText(e); }
}
</script>
<template><span class="favorite-control"><button :aria-label="favorites.states[songId] ? '取消收藏' : '收藏歌曲'" :aria-pressed="!!favorites.states[songId]" :disabled="favorites.pending[songId]" @click="toggle">{{ favorites.states[songId] ? '已收藏' : '收藏' }}</button><span v-if="error" class="form-error" role="alert">{{ error }}</span></span></template>
