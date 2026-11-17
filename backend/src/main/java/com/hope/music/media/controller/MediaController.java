package com.hope.music.media.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.media.service.MediaService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.*;

/** HLS children and keys are rewritten through the same authenticated Boot endpoint. */
@RestController
@RequestMapping("/api/v1/media/hls")
public class MediaController {
    private final MediaService media;
    private final SessionService session;
    public MediaController(MediaService media,SessionService session) { this.media=media;this.session=session; }
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control","private, no-store"); }
    private void authorize(String auth,String token) { session.identity(auth==null&&token!=null?"Bearer "+token:auth); }
    @PostMapping(params="action=processMp3")
    public Object prepare(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam String songId) throws IOException {
        authorize(auth,null);return media.prepare(songId);
    }
    @GetMapping(params="action=checkStatus")
    public ResponseEntity<Object> status(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam String songId) throws IOException {
        authorize(auth,null);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(media.status(songId));
    }
    @GetMapping(params="action=serveHls")
    public ResponseEntity<?> serve(@RequestHeader(value="Authorization",required=false) String auth,
            @RequestParam(required=false) String token,@RequestParam String songId,@RequestParam String fileName) throws IOException {
        authorize(auth,token);
        try {
            var path=media.file(songId,fileName);
            var response=ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff");
            if(fileName.equals("index.m3u8")) {
                String prefix="hls?action=serveHls&songId="+songId+"&fileName=";
                String suffix=token==null?"":"&token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
                StringBuilder manifest=new StringBuilder();
                for(String line:Files.readAllLines(path)) {
                    if(line.matches("seg-[a-f0-9]{32}-[0-9]+\\.ts"))line=prefix+line+suffix;
                    else if(line.startsWith("#EXT-X-KEY:")) {
                        Matcher key=Pattern.compile("fileName=(key-[a-f0-9]{32}\\.key)").matcher(line);
                        if(!key.find())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                        line=line.replaceFirst("URI=\"[^\"]*\"", Matcher.quoteReplacement("URI=\""+prefix+key.group(1)+suffix+"\""));
                    }
                    manifest.append(line).append('\n');
                }
                return response.contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl")).body(manifest.toString());
            }
            if(fileName.endsWith(".key")) {
                if(Files.size(path)!=16)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                return response.contentType(MediaType.APPLICATION_OCTET_STREAM).body(Files.readAllBytes(path));
            }
            return response.contentType(MediaType.parseMediaType("video/mp2t")).body(new FileSystemResource(path));
        } catch(java.nio.file.NoSuchFileException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
