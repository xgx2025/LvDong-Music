import { defineStore } from "pinia";
import { ref, watch } from "vue";
import { useSessionStore } from "./session";
import { getFavoriteStates, setFavorite } from "../api/account";

export const useFavoritesStore = defineStore("favorites", () => {
  const session = useSessionStore();
  const states = ref<Record<string, boolean>>({});
  const pending = ref<Record<string, boolean>>({});
  watch(() => session.revision, () => { states.value = {}; pending.value = {}; }, { flush: "sync" });
  async function load(ids: string[]) {
    if (!session.authenticated || !ids.length) return;
    const data = await getFavoriteStates(ids.slice(0, 100));
    Object.assign(states.value, data.states);
  }
  async function toggle(id: string) {
    if (pending.value[id]) return;
    pending.value[id] = true;
    try {
      if (states.value[id] === undefined) await load([id]);
      const next = !states.value[id];
      await setFavorite(id, next);
      states.value[id] = next;
    } finally { delete pending.value[id]; }
  }
  return { states, pending, load, toggle };
});
