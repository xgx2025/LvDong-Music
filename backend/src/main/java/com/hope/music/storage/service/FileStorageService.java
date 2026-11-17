package com.hope.music.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Writes only unique new files; legacy roots are used exclusively for reads. */
@Service
public class FileStorageService {
    private final String root;
    private final String legacy;
    public FileStorageService(@Value("${app.storage.root:}") String root,
            @Value("${app.storage.legacy-root:}") String legacy) { this.root = root; this.legacy = legacy; }
    public record FilePayload(String name, String type, String data) { }
    public record SavedFile(Path path, String url) { }
    private static ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件格式或大小无效"); }
    public static FilePayload image(String data) {
        if (data == null || !data.startsWith("data:image/") || !data.contains(";base64,")) throw bad();
        return new FilePayload("", data.substring(5, data.indexOf(';')), data);
    }
    public SavedFile save(FilePayload file, String kind, boolean privateFile) {
        if (root.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储尚未配置");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("File writes require a transaction");
        if (file == null || file.data() == null) throw bad();
        String mime = file.type() == null ? "" : file.type().toLowerCase(Locale.ROOT);
        String extension = switch (kind) {
            case "image" -> Map.of("image/png", "png", "image/jpeg", "jpg", "image/jpg", "jpg", "image/gif", "gif", "image/webp", "webp").get(mime);
            case "audio" -> Map.of("audio/mpeg", "mp3", "audio/mp3", "mp3", "audio/wav", "wav", "audio/x-wav", "wav", "audio/wave", "wav", "audio/ogg", "ogg", "audio/flac", "flac", "audio/x-flac", "flac", "audio/mp4", "m4a", "audio/aac", "aac").get(mime);
            case "lyric" -> Set.of("", "text/plain", "application/octet-stream", "application/x-lrc", "text/x-lrc").contains(mime) ? "lrc" : null;
            default -> null;
        };
        long max = kind.equals("audio") ? 30L*1024*1024 : kind.equals("image") ? 3L*1024*1024 : 512L*1024;
        if (extension == null || file.data().length() > (max+2)/3*4+200) throw bad();
        String data = file.data();
        int comma = data.indexOf(',');
        if (comma >= 0) {
            if (!data.substring(0, comma).equals("data:" + mime + ";base64")) throw bad();
            data = data.substring(comma+1);
        }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(data); } catch (IllegalArgumentException ex) { throw bad(); }
        if (bytes.length == 0 || bytes.length > max) throw bad();
        if (kind.equals("image")) {
            String head = new String(bytes, 0, Math.min(bytes.length, 12), StandardCharsets.ISO_8859_1);
            boolean valid = switch (extension) {
                case "png" -> bytes.length >= 8 && HexFormat.of().formatHex(Arrays.copyOf(bytes, 8)).equals("89504e470d0a1a0a");
                case "jpg" -> bytes.length >= 3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
                case "gif" -> head.startsWith("GIF87a") || head.startsWith("GIF89a");
                case "webp" -> head.startsWith("RIFF") && head.endsWith("WEBP");
                default -> false;
            };
            if (!valid) throw bad();
        }
        if (kind.equals("lyric")) {
            try { StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); }
            catch (java.nio.charset.CharacterCodingException ex) { throw bad(); }
        }
        String relative = (privateFile ? "private/materials/" : kind.equals("audio") ? "audio/" : "public/" + kind + "/")
                + UUID.randomUUID() + "." + extension;
        try {
            Path base = Path.of(root).toAbsolutePath().normalize(); Files.createDirectories(base);
            base = base.toRealPath();
            Path path = base.resolve(relative);
            Files.createDirectories(path.getParent());
            if (!path.getParent().toRealPath().startsWith(base)) throw bad();
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    }
                }
            });
            return new SavedFile(path, "/api/v1/assets/" + relative.substring(relative.indexOf('/')+1));
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件保存失败"); }
    }
    public Path publicFile(String relative) throws IOException {
        if (!relative.matches("(?:image|lyric)/[a-f0-9-]{36}\\.(?:png|jpg|webp|gif|lrc)")) throw bad();
        return contained(root, "public/"+relative);
    }
    public Path legacyFile(String relative) throws IOException {
        if (!relative.matches("static/(?:images/(?:userAvatars|singerAvatars|songCovers)/[A-Za-z0-9_.-]+\\.(?:png|jpg|jpeg|webp|gif)|lrc/[A-Za-z0-9_-]+\\.lrc)")) throw bad();
        return contained(legacy, relative);
    }
    public Path audioFile(String input) throws IOException {
        if (input == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path path = Path.of(input).toRealPath();
        for (String configured : List.of(root, legacy)) {
            if (!configured.isBlank() && path.startsWith(Path.of(configured).toRealPath()) && Files.isRegularFile(path)) return path;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private Path contained(String configured, String relative) throws IOException {
        if (configured.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path base = Path.of(configured).toRealPath(), path = base.resolve(relative).toRealPath();
        if (!path.startsWith(base) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return path;
    }
}
