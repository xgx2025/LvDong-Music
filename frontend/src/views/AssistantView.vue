<script setup lang="ts">
import { nextTick, ref } from "vue";
const input = ref(""), messages = ref<{ role: string; text: string }[]>([]), container = ref<HTMLElement>();
const features = [['个性化推荐', '根据你的喜好和心情，探索适合的歌曲和歌单'], ['歌词解析', '一起理解歌词含义，感受歌曲中的故事'], ['音乐知识', '了解音乐理论、乐器学习和创作技巧']];
const suggestions = ['推荐几首适合学习的轻音乐', '最近有什么新发行的华语专辑', '如何提高唱歌技巧', '解释一下这首歌的歌词含义'];
async function send(text = input.value) {
  if (!text.trim()) return;
  messages.value.push({ role: "你", text: text.trim() }); input.value = "";
  const response = text.includes('歌词') ? '可以搜索歌曲名称，打开歌曲详情查看歌词；播放时歌词会随进度高亮，也能点击歌词跳转。' : text.includes('唱歌') ? '这是本地演示。你可以记录自己练习的歌曲，在播放器中反复听需要练习的片段。' : '这是本地演示，暂未连接 AI 服务。你可以前往首页搜索喜欢的歌曲，或用歌词识别查找记得的旋律。';
  messages.value.push({ role: "律动助手（演示）", text: response });
  await nextTick(); container.value?.scrollTo({ top: container.value.scrollHeight });
}
</script>
<template>
  <section class="assistant-page">
    <header class="assistant-welcome"><span class="feature-icon" aria-hidden="true">♪</span><div><h1>律动AI音乐助手</h1><p class="subtle">你的专属音乐顾问，发现更多音乐的乐趣</p></div></header>
    <p class="subtle">演示模式 · 回复在本机生成，未连接 AI 服务</p>
    <div class="assistant-suggestions"><button v-for="question in suggestions" :key="question" :aria-label="question" @click="send(question)"><strong>{{ question }}</strong><small>点击提问</small></button></div>
    <div class="chat-card"><div ref="container" class="chat-messages" role="log" aria-label="助手对话"><article><strong>律动助手（演示）</strong><p>你好！我是律动AI音乐助手。你可以和我聊聊歌曲推荐、歌词和音乐知识。当前为本地演示，试试上方的快捷问题吧。</p></article><article v-for="(message, i) in messages" :key="i" :class="{ 'own-message': message.role === '你' }"><strong>{{ message.role }}</strong><p>{{ message.text }}</p></article></div><form class="search-form chat-input" @submit.prevent="send()"><label class="sr-only" for="assistant-input">向助手提问</label><input id="assistant-input" v-model="input" required maxlength="1000" placeholder="输入你的音乐问题…" /><button type="submit" class="primary">发送</button></form><p class="chat-disclaimer">演示回复仅供参考，请理性判断</p></div>
    <section class="assistant-features"><h2>AI音乐助手能做什么</h2><div class="feature-grid"><article v-for="(item, i) in features" :key="item[0]"><span class="feature-icon" aria-hidden="true">{{ ['♪', '≡', '♫'][i] }}</span><h3>{{ item[0] }}</h3><p>{{ item[1] }}</p><small>功能展示 · 当前为本地演示</small></article></div></section>
  </section>
</template>
