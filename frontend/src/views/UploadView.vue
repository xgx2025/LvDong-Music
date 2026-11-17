<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useSessionStore } from "../stores/session";
import { musicianStatus, readFile, uploadMusic } from "../api/account";
import { errorText } from "../api/servlet";
const session = useSessionStore();
const status = ref(""), error = ref(""), notice = ref(""), busy = ref(false);
const form = reactive({ songName: "", singerIdStr: session.user?.id || "", duration: "", album: "" });
const audio = ref<File>(), cover = ref<File>(), lyric = ref<File>();
const coverPreview = ref("");
onBeforeUnmount(() => URL.revokeObjectURL(coverPreview.value));
const formElement = ref<HTMLFormElement>();
function file(event: Event, kind: string) {
  const value = (event.target as HTMLInputElement).files?.[0];
  if (kind === 'audio') audio.value = value;
  else if (kind === 'cover') { cover.value = value; URL.revokeObjectURL(coverPreview.value); coverPreview.value = value ? URL.createObjectURL(value) : ''; }
  else lyric.value = value;
}
async function load() { try { status.value = (await musicianStatus()).status; } catch (e) { error.value = errorText(e); } }
onMounted(load);
async function submit() {
  if (busy.value) return;
  error.value = notice.value = ""; busy.value = true;
  try {
    if (!audio.value || !cover.value) throw new Error("请选择音频和封面");
    if (!form.singerIdStr.split(';').map(id => id.trim()).includes(session.user!.id)) throw new Error("歌手ID必须包含你自己的用户ID");
    const [musicFile, coverImage, lyricFile] = await Promise.all([readFile(audio.value, "audio"), readFile(cover.value, "image"), lyric.value ? readFile(lyric.value, "lyric") : null]);
    await uploadMusic({ ...form, musicFile, coverImage, lyricFile });
    notice.value = "歌曲上传成功，请等待审核后发布";
    Object.assign(form, { songName: "", singerIdStr: session.user?.id || "", duration: "", album: "" });
    audio.value = cover.value = lyric.value = undefined;
    URL.revokeObjectURL(coverPreview.value); coverPreview.value = "";
    formElement.value?.reset();
  } catch (e) { error.value = errorText(e); }
  finally { busy.value = false; }
}
</script>
<template>
  <section class="content-page upload-page"><h1>上传音乐</h1><p class="subtle">分享你的音乐，让更多人听见</p>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="status && status !== 'approved'" class="settings-card musician-gate"><span class="feature-icon" aria-hidden="true">♪</span><h2>成为音乐人，分享你的音乐</h2><p>{{ status === 'pending' ? '音乐人申请正在审核中' : '仅认证音乐人可以上传歌曲' }}</p><div v-if="status !== 'pending'" class="feature-grid"><article><h3>作品上传</h3><p>发布你的原创音乐</p></article><article><h3>粉丝互动</h3><p>通过评论交流音乐</p></article><article><h3>音乐人认证</h3><p>展示你的音乐人身份</p></article></div><RouterLink class="primary" to="/musician">查看音乐人申请</RouterLink></div>
    <form v-else-if="status === 'approved'" ref="formElement" class="settings-card form-stack" @submit.prevent="submit">
      <fieldset :disabled="busy" class="form-stack">
        <label>歌曲名称<input v-model="form.songName" required maxlength="100" /></label>
        <label>歌手ID（多位以分号分隔）<input v-model="form.singerIdStr" required pattern="[0-9]+(;[0-9]+)*" /></label>
        <label>时长<input v-model="form.duration" placeholder="mm:ss" required pattern="[0-9]{1,3}:[0-5][0-9]" /></label>
        <label>专辑名称<input v-model="form.album" maxlength="100" /></label>
        <label class="upload-file">音乐文件（最大30MB）<input type="file" accept="audio/mpeg,audio/wav,audio/ogg,audio/flac,audio/mp4,audio/aac" required @change="file($event, 'audio')" /></label>
        <label class="upload-file">封面图片（最大3MB）<input type="file" accept="image/png,image/jpeg,image/webp" required @change="file($event, 'cover')" /></label>
        <label class="upload-file">歌词文件（可选，最大512KB）<input type="file" accept=".lrc,.txt" @change="file($event, 'lyric')" /></label>
        <img v-if="coverPreview" :src="coverPreview" class="upload-cover-preview" alt="上传封面预览" />
        <label class="checkbox-label"><input type="checkbox" required />我确认拥有该作品的上传和分享权利</label>
        <button class="primary" type="submit">{{ busy ? '上传中…' : '上传音乐' }}</button>
      </fieldset>
    </form><button v-else @click="load">查询上传资格</button>
  </section>
</template>
