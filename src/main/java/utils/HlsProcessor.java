package utils;

import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, deduplicated VOD conversion; existing media is never removed. */
public class HlsProcessor implements AutoCloseable {
    public enum State { QUEUED, RUNNING, READY, FAILED }
    private static final class Task {
        final String source;
        final CompletableFuture<Void> future = new CompletableFuture<>();
        volatile State state = State.QUEUED;
        Task(String source) { this.source = source; }
    }
    private final String ffmpegPath;
    private final ThreadPoolExecutor executor;
    private final Map<Path, Task> tasks = new LinkedHashMap<>();

    public HlsProcessor(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
        executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> {
            Thread thread = new Thread(r, "hls-conversion");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized CompletableFuture<Void> convertToHls(String input, String output, int segmentDuration,
                                                            int ignoredWindow, String songId) {
        if (segmentDuration < 1 || songId == null || !songId.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("非法转换参数");
        }
        Path dir = Path.of(output).toAbsolutePath().normalize();
        String source;
        try { source = fingerprint(Path.of(input)); }
        catch (IOException e) { return CompletableFuture.failedFuture(e); }
        Task existing = tasks.get(dir);
        if (existing != null && !existing.future.isDone()) return existing.future;
        if (isReady(dir) && matchesSource(dir, source)) {
            return existing != null && existing.source.equals(source) && existing.state == State.READY
                    ? existing.future : CompletableFuture.completedFuture(null);
        }
        // Keep terminal diagnostics bounded. Active work is already bounded by the executor.
        if (tasks.size() >= 256) tasks.entrySet().removeIf(e -> e.getValue().future.isDone());
        Task task = new Task(source);
        tasks.put(dir, task);
        try {
            executor.execute(() -> {
                task.state = State.RUNNING;
                try {
                    convert(input, dir, segmentDuration, songId, source);
                    task.state = State.READY;
                    task.future.complete(null);
                } catch (Exception error) {
                    task.state = State.FAILED;
                    task.future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            task.state = State.FAILED;
            task.future.completeExceptionally(error);
        }
        return task.future;
    }

    public synchronized State state(Path dir) {
        Task task = tasks.get(dir.toAbsolutePath().normalize());
        if (task != null) return task.state == State.READY && !isReady(dir) ? State.FAILED : task.state;
        return isReady(dir) ? State.READY : null;
    }

    private void convert(String input, Path dir, int duration, String songId, String source) throws Exception {
        Files.createDirectories(dir);
        String version = UUID.randomUUID().toString().replace("-", "");
        String keyName = "key-" + version + ".key";
        Path key = dir.resolve(keyName);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        Files.write(key, generator.generateKey().getEncoded(), StandardOpenOption.CREATE_NEW);
        Path keyInfo = dir.resolve("keyinfo-" + version + ".txt");
        Files.writeString(keyInfo, "hls?action=serveHls&songId=" + songId + "&fileName=" + keyName + "\n" + key + "\n");
        Path playlist = dir.resolve("manifest-" + version + ".m3u8");
        Path diagnostic = dir.resolve("conversion-" + version + ".log");
        Process process = new ProcessBuilder(ffmpegPath, "-nostdin", "-y", "-loglevel", "error", "-i", input,
                "-map", "0:a:0", "-vn", "-c:a", "aac", "-b:a", "128k", "-f", "hls",
                "-hls_time", String.valueOf(duration), "-hls_list_size", "0", "-hls_playlist_type", "vod",
                "-hls_key_info_file", keyInfo.toString(), "-hls_segment_filename",
                dir.resolve("seg-" + version + "-%05d.ts").toString(), playlist.toString())
                .redirectErrorStream(true).redirectOutput(diagnostic.toFile()).start();
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) throw new IOException("媒体转换超时");
            if (process.exitValue() != 0) throw new IOException("FFmpeg转换失败，退出码=" + process.exitValue());
            if (!fingerprint(Path.of(input)).equals(source)) throw new IOException("转换期间源文件已变更");
            if (!validPlaylist(playlist)) throw new IOException("转换结果不完整");
            // All versioned files already exist. Publish only the manifest, atomically.
            Files.move(playlist, dir.resolve("index.m3u8"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(dir.resolve("source.version"), source, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String fingerprint(Path input) throws IOException {
        return input.toRealPath() + "|" + Files.size(input) + "|" + Files.getLastModifiedTime(input).toMillis();
    }
    private static boolean matchesSource(Path dir, String source) {
        try { return Files.readString(dir.resolve("source.version")).equals(source); }
        catch (IOException e) { return false; }
    }
    public static boolean isReady(Path dir) { return validPlaylist(dir.resolve("index.m3u8")); }
    private static boolean validPlaylist(Path playlist) {
        try {
            List<String> lines = Files.readAllLines(playlist);
            if (!lines.contains("#EXTM3U") || !lines.contains("#EXT-X-ENDLIST")) return false;
            int segments = 0;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#EXT-X-KEY:")) {
                    java.util.regex.Matcher key = java.util.regex.Pattern.compile("fileName=([A-Za-z0-9_-]+\\.key)").matcher(line);
                    if (!key.find() || Files.size(playlist.getParent().resolve(key.group(1))) != 16) return false;
                }
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!line.matches("[A-Za-z0-9_-]{1,64}\\.ts")) return false;
                if (Files.size(playlist.getParent().resolve(line)) == 0) return false;
                segments++;
            }
            return segments > 0;
        } catch (IOException e) { return false; }
    }
    public synchronized void shutdown() {
        executor.shutdownNow();
        for (Task task : tasks.values()) {
            if (!task.future.isDone()) {
                task.state = State.FAILED;
                task.future.completeExceptionally(new CancellationException("媒体服务已关闭"));
            }
        }
    }
    @Override public void close() { shutdown(); }
}
