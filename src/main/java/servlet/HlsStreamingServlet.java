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
    // 使用线程池管理异步任务
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    // 使用ConcurrentMap管理监控任务
   // private final ConcurrentMap<String, Future<?>> monitoringTasks = new ConcurrentHashMap<>();


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
        // 关闭时清理所有资源
        taskExecutor.shutdownNow();
     //   monitoringTasks.values().forEach(task -> task.cancel(true));
        super.destroy();
    }


    /**
     * 处理MP3文件切片请求
     * 调用示例: /hls?action=processMp3&songId=123
     */
    public Object processMp3(@ParamName("songId") String songId) {
        log.info("处理HLS切片: songId={}", songId);
        String sourcePath = musicService.getMusicPath(Long.parseLong(songId));
        log.debug("音乐文件路径: {}", sourcePath);
        //查询数据库得到sourcePath
        if (sourcePath == null || !new File(sourcePath).exists()) {
            throw new IllegalArgumentException("歌曲文件不存在");
        }

         Map<String,Object> map =(Map<String, Object>)checkStatus(songId);
         boolean flag = (boolean)map.get("ready");
        JsonObject result = new JsonObject();
         if (flag) {
             result.addProperty("success", true);
             result.addProperty("message", "HLS切片已存在");
             result.addProperty("hlsUrl", "hls?action=serveHls&songId=" + songId + "&fileName=index.m3u8");
             return result;
         }
        // 参数校验
        if (songId == null) {
            throw new IllegalArgumentException("缺少必要参数: songId");
        }

        // 设置默认值
        int segDuration =  10; // 默认10秒切片
        int listSize =  50; // 默认保留40个切片

        // 为每个歌曲创建独立目录，避免文件冲突
        String outputDir = hlsRootDir + File.separator + songId;
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            throw new RuntimeException("无法创建输出目录", e);
        }

        log.info("------------------------- 开始HLS切片处理");
        try {
            // 启动异步切片任务
            CompletableFuture<Void> conversionTask = hlsProcessor.convertToHls(
                    sourcePath,
                    outputDir,
                    segDuration,
                    listSize,
                    songId
            ).exceptionally(e -> {
                log.error("HLS转换失败: {}", e.getMessage(), e);
                return null;
            });

            // 启动切片监控任务（自动清理旧切片）
//            CompletableFuture<Void> monitorTask = hlsProcessor.monitorHlsDirectory(
//                    outputDir,
//                    50, // 最多保留50个切片
//                    60000 // 每分钟检查一次
//            ).exceptionally(e -> {
//                System.err.println("监控任务异常: " + e.getMessage());
//                return null;
//            });


            // 保存监控任务以便后续管理
//            monitoringTasks.put(songId, monitorTask);

            // 任务完成后处理
            conversionTask.thenRun(() -> {
                log.info("歌曲[{}]HLS切片完成", songId);
                // 从活动任务列表中移除
               // monitoringTasks.remove(songId);
                // 可以在这里添加通知逻辑，如更新数据库状态等
            }).exceptionally(ex -> {
                log.error("歌曲[{}]HLS切片失败: {}", songId, ex.getMessage(), ex);
                // 从活动任务列表中移除
              //  monitoringTasks.remove(songId);
                return null;
            });

            // 返回处理状态
            result.addProperty("success", true);
            result.addProperty("message", "正在处理HLS切片");
            result.addProperty("hlsUrl", "hls?action=serveHls&songId=" + songId + "&fileName=index.m3u8");
            return result;

        } catch (Exception e) {
            throw new RuntimeException("HLS切片处理失败: " + e.getMessage(), e);
        }
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
        if (songId == null || !songId.matches("\\d{1,20}")
                || fileName == null || !fileName.matches("[A-Za-z0-9_-]{1,64}\\.(m3u8|ts)")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "非法请求参数");
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
            response.setContentLengthLong(length);
            try (InputStream in = new FileInputStream(tsFile);
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
            return;
        }

        // 5. TS 分片支持 Range 请求
        response.setContentType("video/mp2t");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "public, max-age=3600");

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
        String outputDir = hlsRootDir + File.separator + songId;
        File m3u8File = new File(outputDir + File.separator + "index.m3u8");
        // 检查前3个切片是否已生成（避免播放时早期切片缺失）
        File ts0File = new File(outputDir + File.separator + "index0.ts");
        File ts1File = new File(outputDir + File.separator + "index1.ts");

        Map<String, Object> result = new HashMap<>();
        // 必须同时满足：m3u8存在 + 前2个切片存在
        result.put("ready", m3u8File.exists() && m3u8File.length() > 0
                && ts0File.exists() && ts1File.exists());
        return result;
    }
}