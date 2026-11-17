<script setup lang="ts">
import { ref } from "vue";
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
    <h1>连接设置</h1>
    <div class="settings-card">
      <span class="pill">已接入</span>
      <h2>公开曲库</h2>
      <p>
        歌曲与歌手搜索使用独立后端公开
        API，无需登录。专辑和歌单独立检索尚未完整实现。
      </p>
    </div>
    <div class="settings-card">
      <span class="pill muted">{{
        "已接入 · 待播放校验"
      }}</span>
      <h2>媒体连接</h2>
      <p v-if="session.authenticated">已登录为 {{ session.user?.username }}，播放与账号操作使用同一登录身份。<RouterLink to="/profile">查看个人中心</RouterLink></p>
      <p>
        播放由独立后端的鉴权 HLS 服务提供，登录后即可使用。无法准备音频时，请联系站点维护者确认媒体存储与转换服务已配置。
      </p>
      <p>
        手工凭证仅用于媒体请求，刷新页面即清除，不提供自动刷新。仅在可信设备上使用。原生
        HLS 会将凭证带入查询参数，服务端应使用 HTTPS 并避免记录凭证。
      </p>
      <form v-if="!session.authenticated" @submit.prevent="save">
        <label for="media-token">播放 Access Token</label
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
            :disabled="!draft.trim()"
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
      <h2>账号与页面</h2>
      <p>
        登录、注册、密码重置、个人资料、音乐人申请、上传、收藏、评论与歌词已接入。公开搜索保持匿名，个人收藏状态通过登录后的独立接口查询。
      </p>
      <p>
        浏览器减少动态效果偏好会自动生效。队列和播放进度在切换页面时持续保留，刷新页面后重置。勾选保持登录仅在当前标签页保存会话，关闭标签页后清除。AI助手为本地演示，反馈仅支持本机导出。
      </p>
    </div>
  </section>
</template>
