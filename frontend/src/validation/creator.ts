// Match CreatorService and the existing database columns; never truncate submissions.
export const creatorLimits = { songName: 30, album: 30, singerName: 30, nationality: 10, singerBio: 500, singerIds: 2100 } as const;
export const durationPattern = "[0-9]{1,2}:[0-5][0-9]";
function text(value: string, label: string, max: number, required = true) {
  if (required && !value.trim()) throw new Error(`请填写${label}`);
  if (value.trim().length > max) throw new Error(`${label}不能超过${max}个字符`);
}
export function validateUpload(form: { songName: string; album: string; duration: string; singerIdStr: string }, userId: string) {
  text(form.songName, "歌曲名称", creatorLimits.songName);
  text(form.album, "专辑名称", creatorLimits.album, false);
  if (!new RegExp(`^(?:${durationPattern})$`).test(form.duration)) throw new Error("时长应为mm:ss，分钟为1至2位，秒数为00至59");
  const ids = form.singerIdStr.split(";").map(id => id.trim());
  if (form.singerIdStr.length > creatorLimits.singerIds || ids.some(id => !/^[0-9]{1,19}$/.test(id) || BigInt(id) <= 0n || BigInt(id) > 9223372036854775807n)) throw new Error("歌手ID应为有效数字，多位歌手以分号分隔");
  if (new Set(ids).size > 100) throw new Error("最多填写100位歌手");
  if (!ids.includes(userId)) throw new Error("歌手ID必须包含你自己的用户ID");
}
export function validateApplication(form: { singerName: string; nationality: string; singerBio: string }) {
  text(form.singerName, "歌手名称", creatorLimits.singerName);
  text(form.nationality, "国籍", creatorLimits.nationality);
  text(form.singerBio, "歌手简介", creatorLimits.singerBio);
}
