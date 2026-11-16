<script setup lang="ts">
import { nextTick, ref } from "vue";
const input = ref(""), messages = ref<{ role: string; text: string }[]>([]), container = ref<HTMLElement>();
const suggestions = ['推荐适合学习的轻音乐', '如何提高唱歌技巧', '如何查找一首歌的歌词'];
async function send(text = input.value) {
  if (!text.trim()) return;
  messages.value.push({ role: "你", text: text.trim() }); input.value = "";
  const response = text.includes('歌词') ? '可以搜索歌曲名称，打开歌曲详情查看歌词；播放时歌词会随进度高亮，也能点击歌词跳转。' : text.includes('唱歌') ? '这是本地演示。你可以记录自己练习的歌曲，在播放器中反复听需要练习的片段。' : '这是本地演示，暂未连接 AI 服务。你可以前往首页搜索喜欢的歌曲，或用歌词识别查找记得的旋律。';
  messages.value.push({ role: "律动助手（演示）", text: response });
  await nextTick(); container.value?.scrollTo({ top: container.value.scrollHeight });
}
</script>
<template><section class="content-page"><h1>律动AI音乐助手</h1><p class="subtle">演示模式 · 回复在本机生成，未连接 AI 服务</p><div class="settings-card"><div class="assistant-suggestions"><button v-for="question in suggestions" :key="question" @click="send(question)">{{ question }}</button></div><div ref="container" class="chat-messages" role="log" aria-label="助手对话"><article v-for="(message, i) in messages" :key="i" :class="{ 'own-message': message.role === '你' }"><strong>{{ message.role }}</strong><p>{{ message.text }}</p></article></div><form class="search-form" @submit.prevent="send()"><label class="sr-only" for="assistant-input">向助手提问</label><input id="assistant-input" v-model="input" required maxlength="1000" placeholder="输入你的音乐问题…" /><button type="submit" class="primary">发送</button></form></div></section></template>
