<script setup lang="ts">
import { provide } from "vue";
provide("original-icon-version", 6);
import AppIcon from "../components/AppIcon.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { applyMusician, musicianStatus } from "../api/account";
import ImageUploadField from "../components/ImageUploadField.vue";
import { errorText } from "../api/servlet";
import { creatorLimits, validateApplication } from "../validation/creator";
const status = ref(""), error = ref(""), notice = ref(""), busy = ref(false);
const termsAccepted = ref(false);
const form = reactive({ singerName: "", gender: "未知", nationality: "中国", singerBio: "", singerAvatarBase64: "", idCardFrontBase64: "", idCardBackBase64: "" });
async function load() { try { status.value = (await musicianStatus()).status; } catch (e) { error.value = errorText(e); } }
onMounted(load);
const imageBusy = reactive<Record<string, boolean>>({});
const readingImages = computed(() => Object.values(imageBusy).some(Boolean));
async function submit() {
  if (busy.value || readingImages.value) return;
  busy.value = true; error.value = "";
  try {
    validateApplication(form);
    if (!termsAccepted.value) throw new Error("请先阅读并同意音乐人服务协议和隐私政策");
    if (!form.singerAvatarBase64 || !form.idCardFrontBase64 || !form.idCardBackBase64) throw new Error("请提供歌手头像及身份证正反面");
    await applyMusician({ ...form });
    form.idCardFrontBase64 = form.idCardBackBase64 = "";
    status.value = "pending"; notice.value = "申请已提交，请等待审核";
  } catch (e) { error.value = errorText(e); }
  finally { busy.value = false; }
}
</script>
<template>
  <section class="profile-page"><div class="profile-card"><div class="dialog-heading"><h1>申请成为音乐人</h1><p>分享你的音乐，让更多人听见</p></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="status === 'approved'"><h2><AppIcon name="check" />你已成为认证音乐人</h2><RouterLink class="primary" to="/upload">上传音乐</RouterLink></div>
    <div v-else-if="status === 'pending'"><h2><AppIcon name="clock" />申请审核中</h2><p>审核通过后即可上传作品。</p></div>
    <div v-else-if="status === 'rejected'"><h2><AppIcon name="warning" />申请未通过</h2><p>请联系站点维护者确认审核原因与重新申请方式。</p></div>
    <form v-else-if="status === 'normal'" class="form-stack" @submit.prevent="submit">
      <label>歌手名称<input :disabled="busy" v-model="form.singerName" required :maxlength="creatorLimits.singerName" /></label>
      <label>性别<select :disabled="busy" v-model="form.gender"><option>未知</option><option>男</option><option>女</option></select></label>
      <label>国籍<input :disabled="busy" v-model="form.nationality" required :maxlength="creatorLimits.nationality" /></label>
      <label>歌手简介<textarea :disabled="busy" v-model="form.singerBio" required maxlength="500" rows="4"></textarea></label>
      <ImageUploadField v-model="form.singerAvatarBase64" label="歌手头像" required :disabled="busy" @busy="imageBusy.singerAvatarBase64 = $event" />
      <div class="identity-materials">
      <ImageUploadField v-model="form.idCardFrontBase64" label="身份证正面" required :disabled="busy" @busy="imageBusy.idCardFrontBase64 = $event" />
      <ImageUploadField v-model="form.idCardBackBase64" label="身份证反面" required :disabled="busy" @busy="imageBusy.idCardBackBase64 = $event" />
      </div>
      <p class="subtle">材料仅用于音乐人身份审核，每张图片不超过3MB。</p>
      <label class="checkbox-label"><input :disabled="busy" v-model="termsAccepted" type="checkbox" required /><span>我已确认资料真实，同意提交身份材料用于审核，并已阅读和同意<RouterLink to="/agreements/musician" target="_blank" rel="noopener">《音乐人服务协议》</RouterLink>和<RouterLink to="/agreements/musician-privacy" target="_blank" rel="noopener">《隐私政策》</RouterLink></span></label>
      <button type="submit" class="orange-button" :disabled="busy || readingImages"><AppIcon :name="busy ? 'spinner' : 'send'" />{{ busy ? '提交中…' : '提交申请' }}</button>
    </form>
    <button v-else @click="load">重新查询资格</button>
    <p><RouterLink to="/profile">返回个人中心</RouterLink></p>
  </div></section>
</template>
