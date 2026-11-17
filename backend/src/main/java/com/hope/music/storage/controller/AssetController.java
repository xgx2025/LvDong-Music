package com.hope.music.storage.controller;

import com.hope.music.storage.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

/** Only allowlisted public artwork and lyrics can be served, never audio or identity documents. */
@RestController
public class AssetController {
    private final FileStorageService storage;
    public AssetController(FileStorageService storage) { this.storage=storage; }
    @GetMapping("/api/v1/assets/{*path}")
    public ResponseEntity<?> asset(@PathVariable String path) throws IOException {
        String relative=path.replaceFirst("^/", "");
        try {
            var file=relative.startsWith("static/")?storage.legacyFile(relative):storage.publicFile(relative);
            String ext=relative.substring(relative.lastIndexOf('.')+1);
            String type=switch(ext) { case "jpg","jpeg" -> "image/jpeg";case "png" -> "image/png";case "gif" -> "image/gif";case "webp" -> "image/webp";case "lrc" -> "text/plain;charset=UTF-8";default -> "application/octet-stream"; };
            return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(file));
        } catch(java.nio.file.NoSuchFileException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
