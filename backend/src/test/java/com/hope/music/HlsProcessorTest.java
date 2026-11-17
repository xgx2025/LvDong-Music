package com.hope.music;

import com.hope.music.media.service.HlsProcessor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

/** Opt-in real FFmpeg regression: mvn -Pmedia-regression verify. Synthetic audio only. */
@EnabledIfSystemProperty(named="media.regression",matches="true")
class HlsProcessorTest {
    private final String ffmpeg = System.getProperty("ffmpeg.test.path", "ffmpeg");
    private Path root;
    @BeforeEach void setUp() throws Exception { root = Files.createTempDirectory(Path.of("target"), "hls-regression-"); }
    private Path audio(int seconds) throws Exception {
        Path file = root.resolve("sample-" + seconds + ".wav");
        Process p = new ProcessBuilder(ffmpeg, "-nostdin", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "sine=frequency=440:sample_rate=44100", "-t", String.valueOf(seconds), file.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("fixture.log").toFile()).start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue());
        return file;
    }
    @Test public void testShortAudioBecomesReadyAndDecrypts() throws Exception {
        Path source = audio(1), out = root.resolve("short");
        try (HlsProcessor processor = new HlsProcessor(ffmpeg)) {
            processor.convertToHls(source.toString(), out.toString(), 10, 50, "1").get(30, TimeUnit.SECONDS);
            assertEquals(HlsProcessor.State.READY, processor.state(out));
            assertTrue(HlsProcessor.isReady(out));
            List<String> lines = Files.readAllLines(out.resolve("index.m3u8"));
            assertEquals(1, lines.stream().filter(l -> l.endsWith(".ts")).count());
            // Replace only the key URI in a test manifest to verify real encrypted decoding locally.
            String manifest = String.join("\n", lines).replaceAll("URI=\"[^\"]*fileName=([^\"]+)\"", "URI=\"$1\"");
            Path local = out.resolve("decode.m3u8");
            Files.writeString(local, manifest);
            Process decode = new ProcessBuilder(ffmpeg, "-nostdin", "-loglevel", "error", "-allowed_extensions", "ALL",
                    "-i", local.toString(), "-f", "null", "-").redirectErrorStream(true)
                    .redirectOutput(root.resolve("decode.log").toFile()).start();
            assertTrue(decode.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, decode.exitValue());
        }
    }
    @Test public void testLongAudioKeepsAllSegmentsAndDeduplicates() throws Exception {
        Path source = audio(55), out = root.resolve("long");
        Files.createDirectories(out);
        Files.writeString(out.resolve("existing-media.txt"), "preserve");
        try (HlsProcessor processor = new HlsProcessor(ffmpeg)) {
            CompletableFuture<Void> first = processor.convertToHls(source.toString(), out.toString(), 1, 50, "2");
            for (int i = 0; i < 12; i++) assertSame(first, processor.convertToHls(source.toString(), out.toString(), 1, 50, "2"));
            first.get(30, TimeUnit.SECONDS);
            assertTrue(Files.readAllLines(out.resolve("index.m3u8")).stream().filter(l -> l.endsWith(".ts")).count() > 50);
            assertEquals("preserve", Files.readString(out.resolve("existing-media.txt")));
            try (var files = Files.list(out)) { assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".key")).count()); }
        }
    }
    @Test public void testFailureRemainsFailedAndCanRetry() throws Exception {
        Path source = root.resolve("broken.wav"), out = root.resolve("retry");
        Files.writeString(source, "not audio");
        try (HlsProcessor processor = new HlsProcessor(ffmpeg)) {
            try { processor.convertToHls(source.toString(), out.toString(), 1, 0, "3").get(30, TimeUnit.SECONDS); fail(); }
            catch (ExecutionException expected) { assertEquals(HlsProcessor.State.FAILED, processor.state(out)); }
            assertFalse(HlsProcessor.isReady(out));
            Files.copy(audio(1), source, StandardCopyOption.REPLACE_EXISTING);
            processor.convertToHls(source.toString(), out.toString(), 1, 0, "3").get(30, TimeUnit.SECONDS);
            assertTrue(HlsProcessor.isReady(out));
        }
    }
}
