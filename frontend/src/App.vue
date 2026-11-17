<script setup lang="ts">
import AppIcon from "./components/AppIcon.vue";
import PlayerBar from "./components/PlayerBar.vue";
import { usePlayerStore } from "./stores/player";
import { useSessionStore } from "./stores/session";
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
const globalKeyword = ref(""), searchRevision = ref(0);
function globalSearch() {
  const q = globalKeyword.value.trim();
  if (route.path === '/' && String(route.query.q || '') === q && !route.query.page && !route.query.category) searchRevision.value++;
  else void router.push({ path: "/", query: { q } });
}
const player = usePlayerStore();
const session = useSessionStore(), route = useRoute(), router = useRouter();
watch(() => route.query.q, value => globalKeyword.value = String(value || ""), { immediate: true });
watch(() => session.revision, () => { player.clear(); player.expanded = false; }, { flush: "sync" });
watch(() => session.authenticated, (loggedIn) => {
  if (!loggedIn && route.meta.public !== true) void router.replace({ path: "/login", query: { redirect: route.fullPath } });
});
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="app-shell" :class="{ 'dark-theme': route.meta.dark, 'auth-layout': route.meta.authLayout }">
    <header v-if="session.authenticated && !route.meta.authLayout" class="topbar">
      <div class="header-content">
        <RouterLink class="brand" to="/" aria-label="律动音乐首页"
        ><span class="brand-mark" aria-hidden="true"><AppIcon name="music" /></span
        ><span>律动</span></RouterLink
      >
        <div id="header-search"><form class="search-form" @submit.prevent="globalSearch"><label class="sr-only" for="global-keyword">歌曲、歌手或专辑关键词</label><input id="global-keyword" v-model="globalKeyword" type="search" maxlength="200" placeholder="搜索歌曲、歌手或专辑名称" /><button class="primary" type="submit"><AppIcon name="search" />搜索音乐</button></form></div>
        <nav aria-label="主导航">
          <RouterLink to="/"><AppIcon name="home" />首页</RouterLink>
          <RouterLink to="/recognition"><AppIcon name="microphone" />哼唱识曲</RouterLink>
          <RouterLink to="/qq-playlists">QQ歌单</RouterLink>
          <RouterLink to="/upload"><AppIcon name="upload" />上传</RouterLink>
          <RouterLink to="/assistant"><AppIcon name="robot" />AI助手</RouterLink>
          <RouterLink to="/feedback"><AppIcon name="chat" />反馈</RouterLink>
          <RouterLink :to="session.authenticated ? '/profile' : '/login'"><AppIcon name="user" />{{ session.authenticated ? '我的' : '登录' }}</RouterLink>
          <RouterLink to="/queue">
            <AppIcon name="list" />播放队列<span>{{ player.queue.length.toString().padStart(2, "0") }}</span>
          </RouterLink>
        </nav>
      </div>
    </header>
    <div class="main-column">
      <main id="main" tabindex="-1"><RouterView :key="searchRevision" v-if="session.authenticated || route.meta.public === true" /></main>
    </div>
  </div>
  <PlayerBar v-if="session.authenticated" v-show="!route.meta.dark || player.queue.length > 0" :class="{ 'dark-player': route.meta.dark }" />
</template>
