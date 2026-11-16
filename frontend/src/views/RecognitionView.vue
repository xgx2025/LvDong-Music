<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
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
<template><section class="content-page"><h1>哼唱识曲</h1><p class="subtle">沿用原站的语音转文字与歌词识别方式，也可以直接输入记得的歌词。</p><div class="settings-card recognition-card"><button class="record-button" :aria-pressed="listening" @click="record">{{ listening ? '停止录入' : '语音录入歌词' }}</button><form class="form-stack" @submit.prevent="submit"><label>歌词或歌曲线索<textarea v-model="text" required maxlength="1000" rows="5" placeholder="输入或说出你记得的歌词…"></textarea></label><button class="primary" :disabled="busy || listening || !text.trim()" type="submit">{{ busy ? '识别中…' : '识别歌曲' }}</button></form></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><p v-if="info" role="status">{{ info }}</p><SongList v-if="song" :songs="[song]" /></section></template>
