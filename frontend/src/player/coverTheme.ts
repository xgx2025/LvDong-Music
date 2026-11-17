import { ref, watch, type Ref } from "vue";

// Canvas reads are best-effort: cross-origin or unreadable artwork keeps the original theme.
export function useCoverTheme(source: Ref<string | undefined>) {
  const color = ref<string>();
  watch(source, (url, _previous, cleanup) => {
    color.value = undefined;
    if (!url) return;
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.referrerPolicy = "no-referrer";
    let active = true;
    cleanup(() => { active = false; image.onload = image.onerror = null; });
    image.onload = () => {
      if (!active) return;
      try {
        const canvas = document.createElement("canvas");
        canvas.width = canvas.height = 16;
        const context = canvas.getContext("2d", { willReadFrequently: true });
        if (!context) return;
        context.drawImage(image, 0, 0, 16, 16);
        const pixels = context.getImageData(0, 0, 16, 16).data;
        let red = 0, green = 0, blue = 0, weight = 0;
        for (let i = 0; i < pixels.length; i += 4) {
          const alpha = pixels[i + 3]! / 255;
          red += pixels[i]! * alpha; green += pixels[i + 1]! * alpha; blue += pixels[i + 2]! * alpha; weight += alpha;
        }
        if (weight) color.value = `rgb(${[red, green, blue].map(value => Math.round(value / weight * .55)).join(", ")})`;
      } catch { /* Tainted canvas and malformed images must never interrupt playback. */ }
    };
    image.onerror = () => { if (active) color.value = undefined; };
    image.src = url;
  }, { immediate: true });
  return color;
}
