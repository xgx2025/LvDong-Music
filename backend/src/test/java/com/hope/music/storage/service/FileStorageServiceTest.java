package com.hope.music.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/** Verifies independent legacy artwork/audio roots and rejects unrelated files. */
class FileStorageServiceTest {
    @TempDir Path directory;

    @Test
    void readsSplitLegacyRootsWithoutGrantingAudioAccessToOtherDirectories() throws Exception {
        Path current = Files.createDirectory(directory.resolve("new"));
        Path legacy = Files.createDirectory(directory.resolve("old-webapp"));
        Path audio = Files.createDirectory(directory.resolve("old-audio"));
        Path cover = legacy.resolve("static/images/songCovers/1.png");
        Files.createDirectories(cover.getParent());
        Files.write(cover, new byte[]{1});
        Path source = Files.write(audio.resolve("1.mp3"), new byte[]{2});
        Path outside = Files.write(directory.resolve("outside.mp3"), new byte[]{3});
        var storage = new FileStorageService(current.toString(), legacy.toString(), audio.toString());

        assertThat(storage.legacyFile("static/images/songCovers/1.png")).isEqualTo(cover.toRealPath());
        assertThat(storage.audioFile(source.toString())).isEqualTo(source.toRealPath());
        assertThatThrownBy(() -> storage.audioFile(outside.toString())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> storage.audioFile(cover.toString())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> storage.legacyFile("../old-audio/1.mp3")).isInstanceOf(ResponseStatusException.class);
        var fallback = new FileStorageService(current.toString(), audio.toString(), "");
        assertThat(fallback.audioFile(source.toString())).isEqualTo(source.toRealPath());
    }
}
