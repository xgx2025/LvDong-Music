package servlet;

import annotation.ParamName;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.MusicService;
import service.impl.MusicServiceImpl;
import utils.ConfigUtil;
import utils.HlsProcessor;

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

        // 增强 CORS 支持
        response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
        response.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        // 验证文件路径
        Path tsPath = Paths.get(hlsRootDir, songId, fileName).normalize();
        if (!tsPath.startsWith(Paths.get(hlsRootDir).normalize())) {
            response.sendError(403, "Access denied");
            return;
        }

        File tsFile = tsPath.toFile();
        if (!tsFile.exists()) {
            response.sendError(404, "TS file not found");
            return;
        }

        // 正确处理 Range 请求（关键！）
        long length = tsFile.length();
        long start = 0;
        long end = length - 1;

        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {
            String[] range = rangeHeader.substring("bytes=".length()).split("-");
            start = Long.parseLong(range[0]);
            if (range.length > 1) end = Long.parseLong(range[1]);
            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        }

        response.setContentType("video/mp2t");
        response.setContentLengthLong(end - start + 1);
        response.setHeader("Accept-Ranges", "bytes");

        try (InputStream in = new BufferedInputStream(new FileInputStream(tsFile));
             OutputStream out = response.getOutputStream()) {
            in.skip(start);
            byte[] buffer = new byte[8192];
            long remaining = end - start + 1;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
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