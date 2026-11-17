<script setup lang="ts">
import AppHeader from "./components/AppHeader.vue";
import PlayerBar from "./components/PlayerBar.vue";
import { usePlayerStore } from "./stores/player";
import { useSessionStore } from "./stores/session";
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
const searchRevision = ref(0);
function globalSearch(q: string) {
  if (route.path === '/' && String(route.query.q || '') === q && !route.query.page && !route.query.category) searchRevision.value++;
  else void router.push({ path: "/", query: { q } });
}
const player = usePlayerStore();
const session = useSessionStore(), route = useRoute(), router = useRouter();
watch(() => session.revision, () => { player.clear(); player.expanded = false; }, { flush: "sync" });
watch(() => session.authenticated, (loggedIn) => {
  if (!loggedIn && route.meta.public !== true) void router.replace({ path: "/login", query: { redirect: route.fullPath } });
});
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="app-shell" :class="{ 'dark-theme': route.meta.dark, 'auth-layout': route.meta.authLayout }">
    <AppHeader v-if="session.authenticated && !route.meta.authLayout" @search="globalSearch" />
    <div class="main-column">
      <main id="main" tabindex="-1"><RouterView :key="searchRevision" v-if="session.authenticated || route.meta.public === true" /></main>
    </div>
  </div>
  <PlayerBar v-if="session.authenticated" v-show="!route.meta.dark || player.queue.length > 0" :class="{ 'dark-player': route.meta.dark }" />
</template>

<style scoped>
.app-shell:not(.auth-layout) .main-column { max-width: 1300px; padding: 120px 40px 180px; }
@media(max-width: 680px) { .app-shell:not(.auth-layout) .main-column { padding: 154px 20px 200px; } }
</style>
