import { defineStore } from "pinia";
import { ref } from "vue";
export const useSessionStore = defineStore("session", () => {
  const mediaToken = ref("");
  function clear() {
    mediaToken.value = "";
  }
  return { mediaToken, clear };
});
