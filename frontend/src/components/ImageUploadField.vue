<script setup lang="ts">
import AppIcon from "./AppIcon.vue";
import { onBeforeUnmount, ref, watch } from "vue";
import { readFile } from "../api/account";
import { errorText } from "../api/servlet";
const props = defineProps<{ modelValue: string; label: string; disabled?: boolean; required?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; busy: [value: boolean] }>();
const input = ref<HTMLInputElement>(), error = ref(""), reading = ref(false), dragging = ref(false);
let generation = 0;
onBeforeUnmount(() => generation++);
watch(() => props.modelValue, value => { if (!value && input.value) input.value.value = ""; });
async function select(file?: File) {
  if (!file || props.disabled) return;
  const version = ++generation;
  error.value = ""; reading.value = true; emit('busy', true);
  emit('update:modelValue', '');
  try {
    const result = await readFile(file, 'image');
    if (version === generation) emit('update:modelValue', result.data);
  } catch (e) { if (version === generation) error.value = errorText(e); }
  finally { if (version === generation) { reading.value = false; emit('busy', false); } }
}
function remove() {
  if (props.disabled) return;
  generation++; reading.value = false; error.value = "";
  emit('busy', false); emit('update:modelValue', '');
  if (input.value) input.value.value = "";
}
</script>
<template>
  <div class="image-upload-field">
    <label class="material-upload" :class="{ 'drag-active': dragging, 'has-image': modelValue }" @dragover.prevent="dragging = !disabled" @dragleave.prevent="dragging = false" @drop.prevent="dragging = false; select($event.dataTransfer?.files[0])">
      <span class="material-label">{{ label }}<span v-if="required"> *</span></span>
      <img v-if="modelValue" :src="modelValue" :alt="`${label}预览`" />
      <span v-else class="material-placeholder" aria-hidden="true"><AppIcon :name="label.includes('身份证') ? 'id-card' : 'camera'" /></span>
      <span>{{ reading ? '正在读取图片…' : modelValue ? '点击更换图片' : '点击选择或拖入图片' }}</span>
      <small>图片最大3MB</small>
      <input ref="input" class="sr-only" type="file" :aria-label="label" accept="image/png,image/jpeg,image/webp,image/gif" :required="required && !modelValue" :disabled="disabled" @change="select(($event.target as HTMLInputElement).files?.[0])" />
    </label>
    <button v-if="modelValue" class="material-remove" type="button" :disabled="disabled" :aria-label="`移除${label}`" @click="remove"><AppIcon name="trash" />移除图片</button>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  </div>
</template>
