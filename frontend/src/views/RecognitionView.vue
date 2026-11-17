<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { recognize } from "../api/account";
import { errorText } from "../api/servlet";
import type { Song } from "../types";
import SongList from "../components/SongList.vue";
interface SpeechEngine {
  lang: string; interimResults: boolean; continuous: boolean;
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: ((event: { error: string }) => void) | null; onend: (() => void) | null;
  start(): void; stop(): void; abort(): void;
}
const text = ref(""), listening = ref(false), busy = ref(false), error = ref(""), info = ref(""), song = ref<Song>();
let speech: SpeechEngine | undefined;
const elapsed = ref(0);
let ticker: ReturnType<typeof setInterval> | undefined;
watch(listening, active => { clearInterval(ticker); if (active) { elapsed.value = 0; ticker = setInterval(() => elapsed.value++, 1000); } });
onBeforeUnmount(() => clearInterval(ticker));
function cancel() { speech?.abort(); listening.value = false; }

onBeforeUnmount(() => { if (speech) { speech.onresult = speech.onerror = speech.onend = null; speech.abort(); } });
function record() {
  if (listening.value) { speech?.stop(); return; }
  const constructors = window as unknown as { SpeechRecognition?: new () => SpeechEngine; webkitSpeechRecognition?: new () => SpeechEngine };
  const Constructor = constructors.SpeechRecognition || constructors.webkitSpeechRecognition;
  if (!Constructor) { error.value = "当前浏览器不支持语音转文字，请直接输入歌词"; return; }
  error.value = "";
  speech = new Constructor(); speech.lang = "zh-CN"; speech.continuous = true; speech.interimResults = true;
  speech.onresult = event => { text.value = Array.from(event.results).map(row => row[0]?.transcript || "").join(""); };
  speech.onerror = () => { listening.value = false; error.value = "无法识别语音，请检查麦克风权限或输入歌词"; };
  speech.onend = () => { listening.value = false; };
  try { speech.start(); listening.value = true; } catch { error.value = "无法启用麦克风，请直接输入歌词"; }
}
async function submit() {
  if (busy.value || !text.value.trim()) return;
  speech?.stop(); busy.value = true; error.value = info.value = ""; song.value = undefined;
  try { const data = await recognize(text.value.trim()); song.value = data.data; info.value = data.songInfo || (typeof data.message === 'string' ? data.message : ""); }
  catch (e) { error.value = errorText(e); }
  finally { busy.value = false; }
}
</script>
<template>
  <section class="recognition-page"><header class="centered-heading"><h1>哼唱识曲</h1><p>记得旋律却忘了歌名？说出歌词，让律动帮你寻找</p></header>
    <div class="settings-card recognition-card"><p class="record-status" role="status">{{ listening ? '正在聆听，请说出你记得的歌词…' : busy ? '正在识别歌曲…' : '点击麦克风按钮开始录入歌词' }}</p><p v-if="listening" class="record-timer">{{ Math.floor(elapsed / 60).toString().padStart(2, '0') }}:{{ (elapsed % 60).toString().padStart(2, '0') }}</p>
      <div class="record-wave" :class="{ listening }" aria-hidden="true"><span v-for="(height, i) in [32,64,96,128,80,112,64,96,48,80]" :key="i" :style="{ height: height + 'px', animationDelay: i / 10 + 's' }"></span></div>
      <button class="record-button" :aria-pressed="listening" :disabled="busy" @click="record"><svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8"/></svg><span class="sr-only">{{ listening ? '停止录入' : '语音录入歌词' }}</span></button>
      <p class="subtle">{{ listening ? '再次点击停止录入' : '请允许浏览器使用麦克风，也可以直接输入歌词' }}</p><button v-if="listening" class="cancel-record" @click="cancel">取消录入</button>
      <form class="form-stack" @submit.prevent="submit"><label>歌词或歌曲线索<textarea v-model="text" required maxlength="1000" rows="3" placeholder="输入或说出你记得的歌词…"></textarea></label><button class="primary" :disabled="busy || listening || !text.trim()" type="submit">{{ busy ? '识别中…' : '识别歌曲' }}</button></form>
    </div><p v-if="error" class="form-error" role="alert">{{ error }}</p><section v-if="info || song" class="recognition-result"><h2>识别结果</h2><p v-if="info" role="status">{{ info }}</p><SongList v-if="song" :songs="[song]" /></section>
    <section class="recognition-help"><h2>使用帮助</h2><div class="help-steps"><article v-for="(step, i) in [['准备工作', '保持环境安静，检查麦克风权限。'], ['开始录制', '点击麦克风，说出你记得的清晰歌词。'], ['获取结果', '确认录入内容，点击识别歌曲查看结果。']]" :key="step[0]"><span>{{ i + 1 }}</span><div><h3>{{ step[0] }}</h3><p>{{ step[1] }}</p></div></article></div></section>
  </section>
</template>
