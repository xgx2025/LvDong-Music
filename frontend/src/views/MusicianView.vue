<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { applyMusician, musicianStatus, readFile } from "../api/account";
import { errorText } from "../api/servlet";
const status = ref(""), error = ref(""), notice = ref(""), busy = ref(false);
const form = reactive({ singerName: "", gender: "未知", nationality: "中国", singerBio: "", singerAvatarBase64: "", idCardFrontBase64: "", idCardBackBase64: "" });
async function load() { try { status.value = (await musicianStatus()).status; } catch (e) { error.value = errorText(e); } }
onMounted(load);
async function image(event: Event, key: "singerAvatarBase64" | "idCardFrontBase64" | "idCardBackBase64") {
  const file = (event.target as HTMLInputElement).files?.[0];
  form[key] = "";
  if (file) try { form[key] = (await readFile(file, "image")).data; error.value = ""; } catch (e) { error.value = errorText(e); (event.target as HTMLInputElement).value = ""; }
}
async function submit() {
  if (busy.value) return;
  busy.value = true; error.value = "";
  try {
    if (!form.singerAvatarBase64 || !form.idCardFrontBase64 || !form.idCardBackBase64) throw new Error("请提供歌手头像及身份证正反面");
    await applyMusician({ ...form });
    form.idCardFrontBase64 = form.idCardBackBase64 = "";
    status.value = "pending"; notice.value = "申请已提交，请等待审核";
  } catch (e) { error.value = errorText(e); }
  finally { busy.value = false; }
}
</script>
<template>
  <section class="profile-page"><div class="profile-card"><h1>申请成为音乐人</h1>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="status === 'approved'"><h2>你已成为认证音乐人</h2><RouterLink class="primary" to="/upload">上传音乐</RouterLink></div>
    <div v-else-if="status === 'pending'"><h2>申请审核中</h2><p>审核通过后即可上传作品。</p></div>
    <div v-else-if="status === 'rejected'"><h2>申请未通过</h2><p>请联系站点维护者确认审核原因与重新申请方式。</p></div>
    <form v-else-if="status === 'normal'" class="form-stack" @submit.prevent="submit">
      <label>歌手名称<input v-model="form.singerName" required maxlength="50" /></label>
      <label>性别<select v-model="form.gender"><option>未知</option><option>男</option><option>女</option></select></label>
      <label>国籍<input v-model="form.nationality" required maxlength="50" /></label>
      <label>歌手简介<textarea v-model="form.singerBio" required maxlength="500" rows="4"></textarea></label>
      <label>歌手头像<input type="file" accept="image/png,image/jpeg,image/webp" required @change="image($event, 'singerAvatarBase64')" /></label>
      <label>身份证正面<input type="file" accept="image/png,image/jpeg,image/webp" required @change="image($event, 'idCardFrontBase64')" /></label>
      <label>身份证反面<input type="file" accept="image/png,image/jpeg,image/webp" required @change="image($event, 'idCardBackBase64')" /></label>
      <p class="subtle">材料仅用于音乐人身份审核，每张图片不超过3MB。</p>
      <label class="checkbox-label"><input type="checkbox" required />我已确认资料真实，并同意提交身份材料用于审核</label>
      <button type="submit" class="orange-button" :disabled="busy">{{ busy ? '提交中…' : '提交申请' }}</button>
    </form>
    <button v-else @click="load">重新查询资格</button>
    <p><RouterLink to="/profile">返回个人中心</RouterLink></p>
  </div></section>
</template>
