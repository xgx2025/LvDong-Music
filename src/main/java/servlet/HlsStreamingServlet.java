package servlet;

import annotation.ParamName;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.MusicService;
import service.impl.MusicServiceImpl;
import utils.ConfigUtil;
import utils.HlsProcessor;
import utils.JwtTokenUtil;
import utils.TokenBlacklist;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class HlsStreamingServlet extends BaseServlet {
    private static final Logger log = LoggerFactory.getLogger(HlsStreamingServlet.class);

    private HlsProcessor hlsProcessor;
    private String ffmpegPath;
    private String hlsRootDir; // HLS文件存储根目录

    MusicService musicService = new MusicServiceImpl();
    @Override
    public void init() throws ServletException {
        super.init();
        // 从web.xml初始化参数获取配置（需在web.xml中配置）
        ffmpegPath = getServletConfig().getInitParameter("ffmpegPath");
        hlsRootDir = getServletConfig().getInitParameter("hlsRootDir");

        // 初始化默认值（如果web.xml未配置）
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            ffmpegPath = ConfigUtil.get("FFMPEG_PATH", "ffmpeg.path");
        }
        if (hlsRootDir == null || hlsRootDir.trim().isEmpty()) {
            hlsRootDir = ConfigUtil.get("HLS_ROOT_DIR", "hls.root.dir");
        }

        // 确保HLS根目录存在
        try {
            Files.createDirectories(Paths.get(hlsRootDir));
        } catch (IOException e) {
            throw new ServletException("无法创建HLS存储目录: " + hlsRootDir, e);
        }

        // 初始化HLS处理器
        hlsProcessor = new HlsProcessor(ffmpegPath);
    }

    @Override
    public void destroy() {
        if (hlsProcessor != null) hlsProcessor.shutdown();
        super.destroy();
    }


    /**
     * 处理MP3文件切片请求
     * 调用示例: /hls?action=processMp3&songId=123
     */
    public Object processMp3(@ParamName("songId") String songId) {
        if (songId == null || (!songId.matches("[0-9]{1,19}") || new java.math.BigInteger(songId).bitLength() > 63)) throw new IllegalArgumentException("非法歌曲ID");
        String sourcePath = musicService.getMusicPath(Long.valueOf(songId));
        if (sourcePath == null || !Files.isRegularFile(Path.of(sourcePath))) {
            throw new IllegalArgumentException("歌曲文件不存在或未通过审核");
        }
        hlsProcessor.convertToHls(sourcePath, Path.of(hlsRootDir, songId).toString(), 10, 0, songId);
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "媒体任务已接收");
        result.addProperty("hlsUrl", "hls?action=serveHls&songId=" + songId + "&fileName=index.m3u8");
        return result;
    }

    /**
     * 提供HLS文件访问（M3U8索引文件和TS切片文件）
     * 调用示例: /hls?action=serveHls&songId=123&fileName=index.m3u8
     */
    public void serveHls(@ParamName("songId") String songId,
                         @ParamName("fileName") String fileName,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {

        // 1. 播放鉴权：允许 Authorization 头，或 ?token= 查询参数（Safari 原生 HLS 无法自定义请求头）
        if (!authorizePlayback(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"播放凭证缺失或已失效\"}");
            response.getWriter().flush();
            return;
        }

        // 2. 参数白名单校验，避免把任意路径拼进文件系统
        if (songId == null || (!songId.matches("[0-9]{1,19}") || new java.math.BigInteger(songId).bitLength() > 63)
                || fileName == null || !(fileName.equals("index.m3u8") || fileName.matches("[A-Za-z0-9_-]{1,64}\\.ts")
                    || fileName.matches("key-[a-f0-9]{32}\\.key") || fileName.equals("encryption.key"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "非法请求参数");
            return;
        }

        // 即使已生成缓存，撤回审核后也不能继续从缓存播放。
        if (musicService.getMusicPath(Long.valueOf(songId)) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "歌曲不可播放");
            return;
        }

        // 3. 验证文件路径（normalize + 前缀校验，双保险）
        Path hlsRoot = Paths.get(hlsRootDir).normalize();
        Path tsPath = hlsRoot.resolve(songId).resolve(fileName).normalize();
        if (!tsPath.startsWith(hlsRoot)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return;
        }

        File tsFile = tsPath.toFile();
        if (!tsFile.exists() || !tsFile.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "TS file not found");
            return;
        }

        boolean isPlaylist = fileName.endsWith(".m3u8");
        long length = tsFile.length();

        // 4. 按文件类型返回正确的 Content-Type（m3u8 不能标成 video/mp2t）
        if (isPlaylist) {
            response.setContentType("application/vnd.apple.mpegurl");
            response.setHeader("Cache-Control", "no-store");
            // Rewrite all child requests through the authenticated servlet (including native HLS).
            String token = request.getParameter("token");
            String suffix = "&stream=1" + (token == null ? "" : "&token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));
            String prefix = "hls?action=serveHls&songId=" + songId + "&fileName=";
            StringBuilder manifest = new StringBuilder();
            for (String line : Files.readAllLines(tsPath)) {
                if (line.matches("[A-Za-z0-9_-]{1,64}\\.ts")) line = prefix + line + suffix;
                else if (line.startsWith("#EXT-X-KEY:")) {
                    line = line.replaceAll("URI=\"[^\"]*fileName=([A-Za-z0-9_-]+\\.key)\"", "URI=\"" + prefix + "$1" + java.util.regex.Matcher.quoteReplacement(suffix) + "\"");
                }
                manifest.append(line).append('\n');
            }
            response.getWriter().write(manifest.toString());
            return;
        }

        if (fileName.endsWith(".key")) {
            response.setContentType("application/octet-stream");
            response.setHeader("Cache-Control", "no-store");
            if (length != 16) { response.sendError(404); return; }
            response.setContentLengthLong(length);
            response.getOutputStream().write(Files.readAllBytes(tsPath));
            return;
        }

        // 5. TS 分片支持 Range 请求
        response.setContentType("video/mp2t");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "private, no-store");

        long start = 0;
        long end = length - 1;
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] range = rangeHeader.substring("bytes=".length()).split("-", -1);
            try {
                if (range.length > 0 && !range[0].trim().isEmpty()) {
                    start = Long.parseLong(range[0].trim());
                }
                if (range.length > 1 && !range[1].trim().isEmpty()) {
                    end = Long.parseLong(range[1].trim());
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + length);
                return;
            }
            if (start < 0 || start >= length || end < start) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + length);
                return;
            }
            if (end >= length) {
                end = length - 1;
            }
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        }

        response.setContentLengthLong(end - start + 1);

        try (RandomAccessFile raf = new RandomAccessFile(tsFile, "r");
             OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[8192];
            long remaining = end - start + 1;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    /**
     * 校验播放凭证：优先读 Authorization 头，其次读 token 查询参数
     */
    private boolean authorizePlayback(HttpServletRequest request) {
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring("Bearer ".length()).trim();
        }
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            if (TokenBlacklist.contains(token)) {
                return false;
            }
            if (JwtTokenUtil.isTokenExpired(token, JwtTokenUtil.ACCESS_TOKEN_SECRET)) {
                return false;
            }
            return JwtTokenUtil.getClaimsFromToken(token, JwtTokenUtil.ACCESS_TOKEN_SECRET).get("id") != null;
        } catch (Exception e) {
            log.debug("播放凭证校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查HLS处理状态
     */
    public Object checkStatus(@ParamName("songId") String songId) {
        if (songId == null || (!songId.matches("[0-9]{1,19}") || new java.math.BigInteger(songId).bitLength() > 63)) throw new IllegalArgumentException("非法歌曲ID");
        if (musicService.getMusicPath(Long.valueOf(songId)) == null) throw new IllegalArgumentException("歌曲不可播放");
        Path output = Path.of(hlsRootDir, songId);
        HlsProcessor.State state = hlsProcessor.state(output);
        Map<String, Object> result = new HashMap<>();
        result.put("ready", state == HlsProcessor.State.READY);
        result.put("state", state == null ? "NOT_REQUESTED" : state.name());
        if (state == HlsProcessor.State.FAILED) result.put("message", "媒体转换失败，请重试");
        return result;
    }
}
