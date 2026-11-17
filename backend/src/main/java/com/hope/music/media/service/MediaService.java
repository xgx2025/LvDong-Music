package com.hope.music.media.service;

import com.hope.music.library.service.LibraryService;
import com.hope.music.media.dao.MediaDao;
import com.hope.music.storage.service.FileStorageService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Independent bounded HLS tasks write exclusively to the configured new output root. */
@Service
public class MediaService {
    private final MediaDao dao;
    private final FileStorageService storage;
    private final String root;
    private final HlsProcessor processor;
    public MediaService(MediaDao dao, FileStorageService storage,@Value("${app.storage.hls-root:}") String root,
            @Value("${app.media.ffmpeg:ffmpeg}") String ffmpeg) {
        this.dao=dao;this.storage=storage;this.root=root;this.processor=new HlsProcessor(ffmpeg);
    }
    private Path directory(String songId) throws IOException {
        LibraryService.id(songId);
        if (dao.source(songId)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"歌曲不可播放");
        return outputDirectory(songId);
    }
    private Path outputDirectory(String songId) throws IOException {
        LibraryService.id(songId);
        if(root.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"媒体存储尚未配置");
        Path base=Path.of(root).toAbsolutePath().normalize(); Files.createDirectories(base);base=base.toRealPath();
        Path dir=base.resolve(songId);Files.createDirectories(dir);
        if(!dir.toRealPath().startsWith(base))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return dir;
    }
    // Internal committed-upload path; public status/files still require an approved song.
    public CompletableFuture<Void> preprocessUploaded(String id, String source) throws IOException {
        Path input = storage.audioFile(source), dir = outputDirectory(id);
        return processor.convertToHls(input.toString(), dir.toString(), 10, 0, id);
    }
    public Object prepare(String id) throws IOException {
        Path dir=directory(id), input=storage.audioFile(dao.source(id));
        processor.convertToHls(input.toString(),dir.toString(),10,0,id);
        return Map.of("success",true);
    }
    public Object status(String id) throws IOException {
        var state=processor.state(directory(id));
        return Map.of("success",true,"ready",state==HlsProcessor.State.READY,"state",state==null?"NOT_REQUESTED":state.name());
    }
    public Path file(String id,String name) throws IOException {
        if(name==null||!(name.equals("index.m3u8")||name.matches("seg-[a-f0-9]{32}-[0-9]+\\.ts")||name.matches("key-[a-f0-9]{32}\\.key")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"媒体文件名无效");
        Path dir=directory(id).toRealPath(), path=dir.resolve(name).toRealPath();
        if(!path.startsWith(dir)||!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return path;
    }
    @PreDestroy public void close() { processor.close(); }
}
