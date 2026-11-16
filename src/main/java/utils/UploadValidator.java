package utils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传文件校验：大小上限 + MIME 白名单。
 *
 * 之前未知 MIME 会被直接当成后缀拼进文件路径（mimeType.replace("/", "")），
 * 等于允许客户端写入任意扩展名的文件；这里改为白名单，不认识就拒绝。
 */
public final class UploadValidator {

    public static final String KIND_AUDIO = "audio";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_LYRIC = "lyric";

    /** 音频上限 30MB */
    public static final long MAX_AUDIO_BYTES = 30L * 1024 * 1024;
    /** 封面上限 3MB */
    public static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;
    /** 歌词上限 512KB */
    public static final long MAX_LYRIC_BYTES = 512L * 1024;

    private static final Map<String, String> AUDIO_MIME = Map.of(
            "audio/mpeg", "mp3",
            "audio/mp3", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav",
            "audio/wave", "wav",
            "audio/ogg", "ogg",
            "audio/flac", "flac",
            "audio/x-flac", "flac",
            "audio/mp4", "m4a",
            "audio/aac", "aac"
    );

    private static final Map<String, String> IMAGE_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    /** 歌词文件各浏览器给的 MIME 不统一，统一落成 .lrc */
    private static final Set<String> LYRIC_MIME = Set.of(
            "text/plain", "application/octet-stream", "application/x-lrc", "text/x-lrc", ""
    );

    private UploadValidator() {
    }

    /**
     * 根据 MIME 解析安全的文件后缀，未知类型直接拒绝
     */
    public static String resolveExtension(String kind, String mimeType) {
        String mime = normalizeMime(mimeType);
        switch (kind) {
            case KIND_AUDIO: {
                String ext = AUDIO_MIME.get(mime);
                if (ext == null) {
                    throw new IllegalArgumentException("不支持的音频格式: " + displayMime(mime));
                }
                return ext;
            }
            case KIND_IMAGE: {
                String ext = IMAGE_MIME.get(mime);
                if (ext == null) {
                    throw new IllegalArgumentException("不支持的封面图片格式: " + displayMime(mime));
                }
                return ext;
            }
            case KIND_LYRIC: {
                if (!LYRIC_MIME.contains(mime)) {
                    throw new IllegalArgumentException("不支持的歌词文件格式: " + displayMime(mime));
                }
                return "lrc";
            }
            default:
                throw new IllegalArgumentException("未知的上传类型: " + kind);
        }
    }

    public static long maxBytes(String kind) {
        switch (kind) {
            case KIND_AUDIO:
                return MAX_AUDIO_BYTES;
            case KIND_IMAGE:
                return MAX_IMAGE_BYTES;
            case KIND_LYRIC:
                return MAX_LYRIC_BYTES;
            default:
                return MAX_LYRIC_BYTES;
        }
    }

    /**
     * 校验 Base64 内容大小（按长度估算，不额外解码一次，节省内存）
     */
    public static void checkBase64Size(String base64Content, String kind, String label) {
        if (base64Content == null || base64Content.isEmpty()) {
            throw new IllegalArgumentException(label + "内容为空");
        }
        String data = stripDataUrlPrefix(base64Content);
        // 每 4 个 Base64 字符约等于 3 字节，估算值只会偏大，用作上限判断足够
        long estimatedBytes = (long) data.length() * 3 / 4;
        long max = maxBytes(kind);
        if (estimatedBytes > max) {
            throw new IllegalArgumentException(label + "超过大小上限（最大 " + humanReadable(max) + "）");
        }
    }

    /** 去掉 data:xxx;base64, 前缀 */
    public static String stripDataUrlPrefix(String base64Content) {
        int comma = base64Content.indexOf(',');
        return comma >= 0 ? base64Content.substring(comma + 1) : base64Content;
    }

    private static String normalizeMime(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String mime = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolon = mime.indexOf(';');
        if (semicolon > 0) {
            mime = mime.substring(0, semicolon).trim();
        }
        return mime;
    }

    private static String displayMime(String mime) {
        return mime.isEmpty() ? "未知类型" : mime;
    }

    private static String humanReadable(long bytes) {
        return bytes >= 1024 * 1024 ? (bytes / 1024 / 1024) + "MB" : (bytes / 1024) + "KB";
    }
}
