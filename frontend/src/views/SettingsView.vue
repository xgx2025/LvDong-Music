<script setup lang="ts">
import { ref } from "vue";
import { legacyBase } from "../api/legacy";
import { useSessionStore } from "../stores/session";
import { usePlayerStore } from "../stores/player";
const session = useSessionStore(),
  player = usePlayerStore(),
  draft = ref(""),
  saved = ref("");
function save() {
  player.stop();
  session.mediaToken = draft.value.trim();
  draft.value = "";
  saved.value = "播放凭证已保存在本页内存中。服务端将在播放时校验。";
}
function clear() {
  player.stop();
  session.clear();
  draft.value = "";
  saved.value = "已清除凭证并停止受保护媒体播放。队列仍然保留。";
}
</script>
<template>
  <section class="inner-page settings">
    <p class="eyebrow">CONNECTIONS</p>
    <h1>好音乐，<br />需要真实的连接。</h1>
    <div class="settings-card">
      <span class="pill">已接入</span>
      <h2>公开曲库</h2>
      <p>
        歌曲与歌手搜索使用独立后端公开
        API，无需登录。专辑和歌单独立检索尚未迁移。
      </p>
    </div>
    <div class="settings-card">
      <span class="pill muted">{{
        legacyBase ? "已配置地址 · 待播放校验" : "待配置"
      }}</span>
      <h2>旧站媒体连接</h2>
      <p>
        播放仍由旧站的鉴权 HLS 服务提供。{{
          legacyBase
            ? "已配置媒体服务地址，可提供有效播放凭证。"
            : "媒体服务尚未连接，仍可浏览并整理队列。请联系站点维护者配置服务。"
        }}
      </p>
      <p>
        凭证仅用于媒体请求，刷新页面即清除；不会导入旧站身份，不提供自动刷新。仅在可信设备和可信媒体地址下使用。原生
        HLS 会将凭证带入查询参数，服务端应使用 HTTPS 并避免记录凭证。
      </p>
      <form @submit.prevent="save">
        <label for="media-token">旧站 Access Token</label
        ><input
          id="media-token"
          v-model="draft"
          type="password"
          autocomplete="off"
          spellcheck="false"
          placeholder="仅保存在当前页面内存"
        />
        <div class="button-group">
          <button
            class="primary"
            :disabled="!legacyBase || !draft.trim()"
            type="submit"
          >
            保存播放凭证</button
          ><button type="button" :disabled="!session.mediaToken" @click="clear">
            清除凭证
          </button>
        </div>
      </form>
      <p v-if="saved" role="status" class="notice">{{ saved }}</p>
    </div>
    <div class="settings-card">
      <h2>迁移中的功能</h2>
      <p>
        账号登录、评论、收藏写入、个人中心和歌词同步尚未接入本应用。当前搜索为匿名查询，收藏状态不代表你的账号数据。
      </p>
      <p>
        浏览器减少动态效果偏好会自动生效。队列和播放进度在切换页面时持续保留，刷新页面后重置。
      </p>
    </div>
  </section>
</template>
