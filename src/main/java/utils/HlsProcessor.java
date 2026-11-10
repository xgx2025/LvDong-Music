package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * HLS媒体处理器，使用FFmpeg将MP3文件转换为HLS格式
 */
public class HlsProcessor {
    private static final Logger log = LoggerFactory.getLogger(HlsProcessor.class);

    private final String ffmpegPath;
    private final ExecutorService executor;

    public HlsProcessor(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * 将MP3文件转换为HLS格式
     * @param inputMp3Path 输入MP3文件路径
     * @param outputDir 输出目录
     * @param segmentDuration 切片时长（秒）
     * @param hlsListSize HLS播放列表大小
     * @param songId 歌曲ID（用于生成密钥URL）
     * @return 转换任务的CompletableFuture
     */
    public CompletableFuture<Void> convertToHls(String inputMp3Path, String outputDir,
                                                int segmentDuration, int hlsListSize,
                                                String songId) {
        // 清理输出目录（保留原逻辑）
        try {
            Files.walk(Paths.get(outputDir))
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        try { Files.delete(p); }
                        catch (IOException e) { /* 忽略删除失败 */ }
                    });
        } catch (IOException e) {
            log.error("清理HLS目录失败: {}", e.getMessage(), e);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // 确保输出目录存在
                Path outputPath = Paths.get(outputDir);
                if (!Files.exists(outputPath)) {
                    Files.createDirectories(outputPath);
                }
                log.info("生成HLS加密密钥");
                // 步骤1：生成加密密钥（128位AES）和IV（16字节）
                // 步骤1：生成加密密钥（128位AES）和IV（16字节）
                generateEncryptionKeyAndIV(outputDir);
                String ivHex = readIVAsHexString(outputDir); // IV的16进制格式

                // 确保IV格式正确（32个十六进制字符，不带0x前缀）
                if (ivHex.startsWith("0x")) {
                    ivHex = ivHex.substring(2); // 移除0x前缀
                }
                if (ivHex.length() != 32) {
                    throw new IllegalArgumentException("IV长度必须为32个十六进制字符");
                }

                // 步骤2：创建FFmpeg加密所需的key_info.txt
                Path keyInfoPath = Paths.get(outputDir, "key_info.txt");
                String keyUrl ="hls?action=serveHls&songId=" + songId + "&fileName=encryption.key";
                String localKeyPath = Paths.get(outputDir, "encryption.key").toString();

                // 写入key_info.txt：格式为"密钥URL\n本地密钥路径\nIV的16进制值"
                Files.write(keyInfoPath,
                        (keyUrl + "\n" + localKeyPath + "\n" + ivHex)
                                .getBytes(StandardCharsets.UTF_8));

                // 构建FFmpeg命令
                List<String> command = new ArrayList<>();
                command.add(ffmpegPath);
                command.add("-i");
                command.add(inputMp3Path);
                command.add("-map");
                command.add("0:a");
                command.add("-vn");
                command.add("-c:a");
                command.add("aac");
                command.add("-profile:a");
                command.add("aac_low");
                command.add("-b:a");
                command.add("128k");
                command.add("-ar");
                command.add("44100");
                command.add("-ac");
                command.add("2");


                command.add("-frame_size");
                command.add("1024");


                command.add("-fflags");
                command.add("+genpts");
                command.add("-avoid_negative_ts");
                command.add("make_zero");
                command.add("-mpegts_flags");
                command.add("resend_headers+initial_discontinuity");
                command.add("-packet_size");
                command.add("188");
                command.add("-muxrate");
                command.add("2000k");
                command.add("-mpegts_copyts");
                command.add("1");

                // 切片参数
                command.add("-hls_time");
                command.add(String.valueOf(segmentDuration));
                command.add("-hls_list_size");
                command.add(String.valueOf(hlsListSize));
                command.add("-start_number");
                command.add("0");

                // 修复：仅使用split_by_time标志
                command.add("-hls_flags");
                command.add("split_by_time");

                command.add("-hls_segment_type");
                command.add("mpegts");

                // 加密参数：只保留-hls_key_info_file
                command.add("-hls_key_info_file");
                command.add(keyInfoPath.toString());

                // 输出文件
                command.add("-f");
                command.add("hls");
                command.add("-loglevel");
                command.add("warning");
                command.add(Paths.get(outputDir, "index.m3u8").toString());

                // 输出完整命令用于调试
                log.info("执行FFmpeg命令: {}", String.join(" ", command));

                // 执行命令
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();


                // 同时读取标准输出和错误流
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        log.info("[FFmpeg] {}", line);
                    }
                    while ((line = errorReader.readLine()) != null) {
                        log.error("[FFmpeg Error] {}", line);
                    }
                }

                // 等待进程完成

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IOException("FFmpeg加密失败，退出码: " + exitCode);
                }
                log.info("加密HLS转换完成: {}", outputDir);

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("加密HLS转换失败", e);
            }
        });
    }

    /**
     * 监控HLS切片目录，自动删除旧切片
     * @param hlsDir HLS目录
     * @param maxFiles 最大保留文件数
     * @param checkInterval 检查间隔（毫秒）
     * @return 监控任务的CompletableFuture
     */
    public CompletableFuture<Void> monitorHlsDirectory(String hlsDir, int maxFiles, long checkInterval) {
        return CompletableFuture.runAsync(() -> {
            try {
                Path dir = Paths.get(hlsDir);
                Path m3u8Path = dir.resolve("index.m3u8"); // m3u8文件路径
                while (!Thread.currentThread().isInterrupted()) {
                    // 1. 读取当前m3u8中引用的TS文件（播放列表中正在使用的切片）
                    Set<String> activeTsNames = new HashSet<>();
                    if (Files.exists(m3u8Path)) {
                        List<String> lines = Files.readAllLines(m3u8Path);
                        for (String line : lines) {
                            line = line.trim();
                            if (line.endsWith(".ts")) { // 提取TS文件名（如index0.ts）
                                activeTsNames.add(line);
                            }
                        }
                    }

                    // 2. 列出目录中所有TS文件
                    List<Path> tsFiles = new ArrayList<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.ts")) {
                        stream.forEach(tsFiles::add);
                    }

                    // 3. 按文件名数字排序（index0 < index1 < ...）
                    tsFiles.sort(Comparator.comparing(p -> {
                        try {
                            String num = p.getFileName().toString().replace("index", "").replace(".ts", "");
                            return Integer.parseInt(num);
                        } catch (Exception e) {
                            return Integer.MAX_VALUE;
                        }
                    }));

                    // 4. 只删除：超出maxFiles 且 不在播放列表中的切片
                    if (tsFiles.size() > maxFiles) {
                        int toDelete = tsFiles.size() - maxFiles;
                        for (int i = 0; i < toDelete; i++) {
                            Path tsFile = tsFiles.get(i);
                            String tsName = tsFile.getFileName().toString();
                            // 仅删除播放列表中不引用的旧切片
                            if (!activeTsNames.contains(tsName)) {
                                Files.delete(tsFile);
                                log.info("删除旧切片（已不被播放列表引用）: {}", tsFile);
                            }
                        }
                    }

                    TimeUnit.MILLISECONDS.sleep(checkInterval);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("HLS目录监控已停止: {}", hlsDir);
            } catch (Exception e) {
                log.error("HLS目录监控错误: {}", e.getMessage(), e);
            }
        }, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void generateEncryptionKeyAndIV(String outputDir) throws IOException {
        try {
            Path keyPath = Paths.get(outputDir, "encryption.key");
            Path ivPath = Paths.get(outputDir, "encryption.iv");

            // 生成128位AES密钥
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            SecretKey secretKey = keyGen.generateKey();
            byte[] keyBytes = secretKey.getEncoded();

            // 生成随机IV（16字节）
            SecureRandom secureRandom = new SecureRandom();
            byte[] ivBytes = new byte[16];
            secureRandom.nextBytes(ivBytes);

            // 保存密钥和IV到文件
            Files.write(keyPath, keyBytes);
            Files.write(ivPath, ivBytes);

            log.info("加密密钥和IV已生成: {}, {}", keyPath, ivPath);
        } catch (Exception e) {
            throw new IOException("生成加密密钥失败", e);
        }
    }

    // 读取IV文件并转换为HLS需要的16进制格式字符串
    // 改进readIVAsHexString方法，确保生成正确格式的IV
    private String readIVAsHexString(String outputDir) throws IOException {
        Path ivPath = Paths.get(outputDir, "encryption.iv");
        if (Files.exists(ivPath)) {
            byte[] ivBytes = Files.readAllBytes(ivPath);

            // 转换为32个十六进制字符的字符串（不含0x前缀）
            StringBuilder sb = new StringBuilder();
            for (byte b : ivBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        throw new IOException("IV文件不存在: " + ivPath);
    }
}