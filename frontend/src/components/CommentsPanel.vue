<script setup lang="ts">
import AppIcon from "./AppIcon.vue";
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useSessionStore } from "../stores/session";
import { comments, publishComment, likeComment, type Comment } from "../api/comments";
import { errorText } from "../api/servlet";
const props = defineProps<{ songId: string }>();
const session = useSessionStore(), route = useRoute();
const list = ref<Comment[]>([]), page = ref(1), total = ref(0), hot = ref(false), content = ref(""), reply = ref<Comment>(), error = ref(""), loading = ref(false), sending = ref(false), pending = ref<Record<string, boolean>>({});
const emojiOpen = ref(false), emojiCategory = ref(0), editor = ref<HTMLTextAreaElement>();
const emojiControl = ref<HTMLElement>();
function closeEmoji(event: PointerEvent) { if (event.target instanceof Node && !emojiControl.value?.contains(event.target)) emojiOpen.value = false; }
onMounted(() => document.addEventListener('pointerdown', closeEmoji));
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeEmoji));
const emojiGroups = [
  { name: '表情', items: ['😀','😊','😂','🥰','😍','😎','😭','🥺','🤔','🤗','😴','🥳','😢','😮'] },
  { name: '手势', items: ['👍','👏','🙌','🤝','✌️','💪','🙏'] },
  { name: '音乐', items: ['🎵','🎶','🎤','🎧','🎸','🎹','🥁','🎻','❤️','💙','🔥'] },
];
async function insertEmoji(emoji: string) {
  const field = editor.value, start = field?.selectionStart ?? content.value.length, end = field?.selectionEnd ?? start;
  if (content.value.length - (end - start) + emoji.length > 500) return;
  content.value = content.value.slice(0, start) + emoji + content.value.slice(end);
  await nextTick(); field?.focus(); field?.setSelectionRange(start + emoji.length, start + emoji.length);
}
let version = 0;
let replyPages: Record<string, number> = {};
async function load() {
  const current = ++version;
  list.value = []; total.value = 0; error.value = "";
  replyPages = {};
  if (!session.authenticated) return;
  loading.value = true;
  try {
    const data = (await comments(props.songId, page.value, hot.value)).comments;
    if (current !== version) return;
    list.value = data.list; total.value = data.total;
  } catch (e) { if (current === version) error.value = errorText(e); }
  finally { if (current === version) loading.value = false; }
}
watch(() => [props.songId, session.authenticated, page.value, hot.value], load, { immediate: true });
watch(() => props.songId, () => { page.value = 1; content.value = ""; reply.value = undefined; });
async function send() {
  if (!content.value.trim() || sending.value) return;
  sending.value = true; error.value = "";
  try {
    await publishComment(props.songId, content.value.trim(), reply.value?.id || "0", reply.value?.userId || "");
    content.value = ""; reply.value = undefined; await load();
  } catch (e) { error.value = errorText(e); }
  finally { sending.value = false; }
}
async function like(item: Comment) {
  if (pending.value[item.id]) return;
  pending.value[item.id] = true;
  try { const data = await likeComment(item.id, !item.userHasLiked); item.likeCount = data.likeCount; item.userHasLiked = data.hasLiked; }
  catch (e) { error.value = errorText(e); }
  finally { delete pending.value[item.id]; }
}
async function replies(item: Comment) {
  if (pending.value[item.id]) return;
  pending.value[item.id] = true;
  try {
    const next = (replyPages[item.id] || 0) + 1;
    const data = (await comments(props.songId, 1, false, item.id, next, 10)).comments;
    item.replies = next === 1 ? data.list : [...(item.replies || []), ...data.list];
    item.replyCount = data.total; replyPages[item.id] = next;
  }
  catch (e) { error.value = errorText(e); }
  finally { delete pending.value[item.id]; }
}
</script>
<template>
  <section class="comments-panel" @keydown.esc="emojiOpen = false"><div class="section-heading"><h2>全部评论 <span class="comment-count">({{ total }})</span></h2><select v-if="session.authenticated" v-model="hot" aria-label="评论排序"><option :value="false">最新评论</option><option :value="true">热门评论</option></select></div>
    <p v-if="!session.authenticated"><RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后查看和发表评论</RouterLink></p>
    <template v-else>
      <form class="form-stack" @submit.prevent="send"><p v-if="reply">回复 {{ reply.username }} <button type="button" @click="reply = undefined">取消回复</button></p><label>发表评论<textarea ref="editor" v-model="content" required maxlength="500" rows="3" placeholder="分享你的听歌感受…"></textarea></label><div class="comment-composer-tools"><div ref="emojiControl" class="emoji-control" @keydown.esc="emojiOpen = false"><button type="button" :aria-expanded="emojiOpen" aria-controls="comment-emojis" @click="emojiOpen = !emojiOpen"><AppIcon name="smile" />表情</button><div v-if="emojiOpen" id="comment-emojis" class="emoji-picker"><div class="emoji-categories"><button v-for="(group, i) in emojiGroups" :key="group.name" type="button" :aria-pressed="emojiCategory === i" @click="emojiCategory = i">{{ group.name }}</button><button type="button" aria-label="关闭表情" @click="emojiOpen = false"><AppIcon name="close" /></button></div><div class="emoji-grid"><button v-for="emoji in emojiGroups[emojiCategory]?.items" :key="emoji" type="button" @mousedown.prevent @click="insertEmoji(emoji)">{{ emoji }}</button></div></div></div><small>{{ content.length }}/500</small></div><button class="primary" type="submit" :disabled="sending || !content.trim()"><AppIcon :name="sending ? 'spinner' : 'send'" />{{ sending ? '发表中…' : '发表评论' }}</button></form>
      <p v-if="error" class="form-error" role="alert">{{ error }} <button @click="load">重新加载评论</button></p>
      <p v-if="loading" role="status">正在加载评论…</p>
      <p v-else-if="!list.length">还没有评论</p>
      <article v-for="item in list" :key="item.id" class="comment-card"><strong>{{ item.username }}</strong><time>{{ item.createTime }}</time><p>{{ item.content }}</p><div class="button-group"><button :aria-pressed="item.userHasLiked" :disabled="pending[item.id]" @click="like(item)"><AppIcon name="heart" :filled="item.userHasLiked" />{{ item.userHasLiked ? '已点赞' : '点赞' }} {{ item.likeCount }}</button><button @click="reply = item"><AppIcon name="reply" />回复</button><button v-if="item.replyCount > (item.replies?.length || 0)" @click="replies(item)">查看回复 {{ item.replyCount }}</button></div><div v-for="child in item.replies" :key="child.id" class="comment-reply"><strong>{{ child.username }}</strong><span v-if="child.replyToUsername"> 回复 {{ child.replyToUsername }}</span><p>{{ child.content }}</p><button :aria-pressed="child.userHasLiked" :disabled="pending[child.id]" @click="like(child)"><AppIcon name="heart" :filled="child.userHasLiked" />点赞 {{ child.likeCount }}</button><button @click="reply = { ...child, id: item.id }"><AppIcon name="reply" />回复</button></div></article>
      <div class="pagination"><button :disabled="page <= 1 || loading" @click="page--">上一页</button><span>第 {{ page }} 页 · {{ total }} 条</span><button :disabled="page * 10 >= total || loading" @click="page++">下一页</button></div>
    </template>
  </section>
</template>
