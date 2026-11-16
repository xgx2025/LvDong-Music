export interface LyricLine { time: number; text: string }
export function parseLyrics(source: string): LyricLine[] {
  const offset = Number(source.match(/\[offset:([+-]?\d+)\]/i)?.[1] || 0) / 1000;
  const lines: LyricLine[] = [];
  for (const row of source.split(/\r?\n/)) {
    const tags = [...row.matchAll(/\[(\d+):([0-5]\d)(?:[.:](\d{1,3}))?\]/g)];
    const text = row.replace(/\[[^\]]*\]/g, "").trim();
    for (const tag of tags) lines.push({ time: Math.max(0, Number(tag[1]) * 60 + Number(tag[2]) + Number(`0.${tag[3] || 0}`) - offset), text });
  }
  return lines.sort((a, b) => a.time - b.time);
}
export function lyricIndex(lines: LyricLine[], position: number) {
  let low = 0, high = lines.length;
  while (low < high) { const middle = (low + high) >>> 1; if (lines[middle]!.time <= position) low = middle + 1; else high = middle; }
  return low - 1;
}
