<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useSessionStore } from "../stores/session";
import { usePlayerStore } from "../stores/player";
import { coverUrl } from "../api/legacy";
import AppIcon from "./AppIcon.vue";
const emit = defineEmits<{ search: [keyword: string] }>();
const route = useRoute(), session = useSessionStore(), player = usePlayerStore();
const keyword = ref(""), accountMenu = ref<HTMLDetailsElement>();
const avatar = computed(() => coverUrl(session.user?.avatar || null));
const libraryActive = computed(() => ['/qq-playlists', '/favorites', '/queue'].includes(route.path));
watch(() => route.query.q, value => keyword.value = String(value || ""), { immediate: true });
watch(() => route.fullPath, () => { if (accountMenu.value) accountMenu.value.open = false; });
function outside(event: MouseEvent) { if (accountMenu.value && !accountMenu.value.contains(event.target as Node)) accountMenu.value.open = false; }
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape' && accountMenu.value?.open) {
    accountMenu.value.open = false; accountMenu.value.querySelector('summary')?.focus();
  }
}
onMounted(() => { document.addEventListener('click', outside); document.addEventListener('keydown', escape); });
onBeforeUnmount(() => { document.removeEventListener('click', outside); document.removeEventListener('keydown', escape); });
</script>
<template>
  <header class="site-header">
    <div class="site-header-inner">
      <RouterLink class="site-brand" to="/" aria-label="律动音乐首页"><span class="site-brand-mark"><AppIcon name="music" /></span><span>律动<span class="site-brand-caption">音乐，与你有关</span></span></RouterLink>
      <nav class="site-primary-nav" aria-label="主导航">
        <RouterLink to="/" :class="{ selected: route.path === '/' }">发现音乐</RouterLink>
        <RouterLink to="/qq-playlists" :class="{ selected: libraryActive }">我的音乐</RouterLink>
      </nav>
      <form class="site-search" role="search" @submit.prevent="emit('search', keyword.trim())">
        <label class="sr-only" for="site-keyword">搜索本站歌曲、歌手或专辑</label><AppIcon name="search" /><input id="site-keyword" v-model="keyword" type="search" maxlength="200" placeholder="搜索本站音乐" /><button type="submit" aria-label="搜索音乐"><AppIcon name="search" /></button>
      </form>
      <div class="site-header-tools">
        <RouterLink class="site-queue" to="/queue" :aria-label="`播放队列，${player.queue.length} 首`" title="播放队列"><AppIcon name="list" /><span v-if="player.queue.length" class="site-queue-count">{{ player.queue.length > 99 ? '99+' : player.queue.length }}</span></RouterLink>
        <details ref="accountMenu" class="site-account">
          <summary aria-label="账户与工具"><span class="site-avatar"><img v-if="avatar" :src="avatar" alt="" /><span v-else>{{ session.user?.username?.slice(0, 1) || '我' }}</span></span><span class="site-account-label">账户与工具</span><AppIcon name="chevron-down" /></summary>
          <div class="site-account-popover">
            <RouterLink class="site-account-identity" to="/profile"><strong>{{ session.user?.username || '音乐爱好者' }}</strong><small>个人资料与音乐账号</small></RouterLink>
            <div class="site-menu-group"><span class="site-menu-label">探索音乐</span><RouterLink to="/recognition"><AppIcon name="microphone" />哼唱识曲<small>用旋律找歌</small></RouterLink><RouterLink to="/assistant"><AppIcon name="robot" />AI 音乐助手</RouterLink></div>
            <div class="site-menu-group"><span class="site-menu-label">创作与支持</span><RouterLink to="/upload"><AppIcon name="upload" />上传音乐</RouterLink><RouterLink to="/musician"><AppIcon name="guitar" />音乐人中心</RouterLink><RouterLink to="/feedback"><AppIcon name="chat" />意见反馈</RouterLink></div>
          </div>
        </details>
      </div>
    </div>
  </header>
</template>
<style scoped>
.site-header { position: fixed; inset: 0 0 auto; z-index: 30; height: 82px; border-bottom: 1px solid #dce3ed80; background: #f9faf8ed; backdrop-filter: blur(24px); color: #243249; }
.site-header-inner { max-width: 1380px; height: 100%; margin: auto; padding: 0 40px; display: flex; align-items: center; gap: 40px; }
.site-brand { display: inline-flex; align-items: center; gap: 10px; flex-shrink: 0; font-size: 23px; font-weight: 750; letter-spacing: -.04em; }
.site-brand-mark { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 13px; background: #233eaa; color: #fff; font-size: 19px; }
.site-brand-mark .app-icon { margin: 0; }
.site-brand-caption { display: block; margin-top: 2px; color: #8a929f; font-size: 9px; font-weight: 400; letter-spacing: .08em; }
.site-primary-nav { display: flex; width: auto; order: initial; gap: 28px; overflow: visible; padding: 0; justify-content: start; }
.site-primary-nav a { display: flex; height: 82px; align-items: center; position: relative; padding: 0; color: #7a8493; font-size: 14px; font-weight: 500; border: 0; }
.site-primary-nav a.selected { color: #243e9c; }
.site-primary-nav a.selected::after { content: ''; position: absolute; bottom: 17px; left: calc(50% - 8px); width: 16px; height: 3px; border-radius: 2px; background: #314db5; }
.site-search { display: flex; align-items: center; gap: 10px; margin-left: auto; width: min(310px, 27vw); border: 1px solid #e6e9ef; border-radius: 10px; padding: 0 13px; background: #f0f2f5; color: #8b94a4; }
.site-search:focus-within { outline: 2px solid #4664bd50; }
.site-search > .app-icon { margin: 0; font-size: 13px; }
.site-search input { width: 100%; min-width: 0; height: 38px; border: 0; background: transparent; outline: 0; color: #243249; font-size: 12px; }
.site-search button { display: none; }
.site-header-tools { display: flex; align-items: center; gap: 22px; }
.site-queue { display: flex; align-items: center; justify-content: center; position: relative; width: 34px; height: 38px; color: #647087; }
.site-queue .app-icon { margin: 0; font-size: 18px; }
.site-queue-count { position: absolute; top: -1px; right: -6px; min-width: 17px; padding: 2px 4px; border: 2px solid #f9faf8; border-radius: 8px; color: #fff; background: #314db5; font-size: 9px; line-height: 1; text-align: center; }
.site-account { position: relative; }
.site-account summary { display: flex; align-items: center; gap: 8px; cursor: pointer; list-style: none; color: #59657a; font-size: 12px; white-space: nowrap; }
.site-account summary::-webkit-details-marker { display: none; }
.site-account summary > .app-icon { margin: 0; font-size: 9px; transition: transform .2s; }
.site-account[open] summary > .app-icon { transform: rotate(180deg); }
.site-avatar { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: #e5eaf5; color: #40598e; font-weight: 600; overflow: hidden; }
.site-avatar img { width: 100%; height: 100%; object-fit: cover; }
.site-account-popover { position: absolute; top: calc(100% + 22px); right: 0; width: 264px; padding: 8px; border: 1px solid #e5e9f0; border-radius: 16px; background: #fff; box-shadow: 0 18px 60px #24324920; animation: menu-enter .16s ease-out; }
.site-account-identity { display: block; padding: 16px; border-radius: 9px; background: #f5f7fc; }
.site-account-identity strong { display: block; font-size: 14px; }
.site-account-identity small { display: block; margin-top: 5px; color: #7a8597; font-size: 11px; }
.site-menu-group { padding: 8px 4px; }
.site-menu-group + .site-menu-group { border-top: 1px solid #edf0f5; }
.site-menu-label { display: block; padding: 8px 10px; color: #9aa1ae; font-size: 10px; letter-spacing: .08em; }
.site-menu-group a { display: flex; align-items: center; gap: 8px; padding: 11px 10px; border-radius: 7px; font-size: 13px; color: #48556c; }
.site-menu-group a:hover { background: #f0f4fc; color: #314db5; }
.site-menu-group .app-icon { margin: 0; color: #8794ac; font-size: 13px; }
.site-menu-group small { margin-left: auto; color: #9aa1ae; font-size: 10px; }
@keyframes menu-enter { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } }
:global(.dark-theme) .site-header { background: #1a2235ed; border-color: #ffffff12; color: #e5eaf5; }
:global(.dark-theme) .site-primary-nav a { color: #b9c5da; }
:global(.dark-theme) .site-primary-nav a.selected { color: #dbe5ff; }
@media(max-width: 1050px) { .site-header-inner { gap: 26px; padding: 0 24px; } .site-primary-nav { gap: 22px; } .site-account-label { display: none; } }
@media(max-width: 680px) { .site-header { height: 126px; } .site-header-inner { flex-wrap: wrap; align-content: center; gap: 0 20px; padding: 0 20px; } .site-brand { font-size: 21px; } .site-brand-caption { display: none; } .site-brand-mark { width: 32px; height: 32px; border-radius: 10px; font-size: 16px; } .site-primary-nav { gap: 20px; flex: 1; } .site-primary-nav a { height: 52px; font-size: 12px; } .site-primary-nav a.selected::after { bottom: 6px; } .site-header-tools { gap: 12px; } .site-account summary > .app-icon { display: none; } .site-search { order: 3; width: 100%; margin-top: 8px; } .site-search input { height: 34px; } .site-account-popover { top: calc(100% + 14px); } }
@media(max-width: 380px) { .site-header-inner { gap: 0 13px; padding-inline: 14px; } .site-primary-nav { gap: 14px; } .site-header-tools { gap: 7px; } }
@media(prefers-reduced-motion: reduce) { .site-account-popover { animation: none; } }
</style>
