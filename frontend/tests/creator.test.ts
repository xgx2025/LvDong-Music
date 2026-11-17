import { describe, expect, it } from "vitest";
import { validateUpload, validateApplication } from "../src/validation/creator";
const upload = { songName: "曲".repeat(30), album: "辑".repeat(30), duration: "99:59", singerIdStr: "9223372036854775807;2" };
describe("creator request boundaries", () => {
  it("accepts database limits and preserves 64-bit IDs", () => {
    expect(() => validateUpload(upload, "9223372036854775807")).not.toThrow();
    expect(() => validateApplication({ singerName: "人".repeat(30), nationality: "国".repeat(10), singerBio: "介".repeat(500) })).not.toThrow();
  });
  it("rejects oversize names rather than truncating submitted data", () => {
    expect(() => validateUpload({ ...upload, songName: "曲".repeat(31) }, "2")).toThrow("歌曲名称不能超过30");
    expect(() => validateUpload({ ...upload, album: "辑".repeat(31) }, "2")).toThrow("专辑名称不能超过30");
    expect(() => validateApplication({ singerName: "人".repeat(31), nationality: "中国", singerBio: "简介" })).toThrow("歌手名称不能超过30");
    expect(() => validateApplication({ singerName: "歌手", nationality: "国".repeat(11), singerBio: "简介" })).toThrow("国籍不能超过10");
  });
  it("rejects invalid seconds and minutes that cannot fit the duration column", () => {
    for (const duration of ["100:00", "1:60", "1:1", "-1:00"]) expect(() => validateUpload({ ...upload, duration }, "2")).toThrow("时长");
    expect(() => validateUpload({ ...upload, duration: "1:00" }, "2")).not.toThrow();
  });
  it("rejects whitespace-only required values and supports an optional album", () => {
    expect(() => validateUpload({ ...upload, songName: "  " }, "2")).toThrow("请填写歌曲名称");
    expect(() => validateUpload({ ...upload, album: "" }, "2")).not.toThrow();
    expect(() => validateApplication({ singerName: "歌手", nationality: "中国", singerBio: "  " })).toThrow("请填写歌手简介");
  });
  it("checks ID ownership, range and unique singer count before reading files", () => {
    for (const singerIdStr of ["0;2", "9223372036854775808;2", "1;;2"]) expect(() => validateUpload({ ...upload, singerIdStr }, "2")).toThrow("歌手ID");
    expect(() => validateUpload(upload, "3")).toThrow("必须包含");
    expect(() => validateUpload({ ...upload, singerIdStr: Array.from({ length: 101 }, (_, i) => String(i + 1)).join(";") }, "2")).toThrow("最多填写100");
  });
});
