package com.hope.music.recognition.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.recognition.service.RecognitionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Authenticated lyric-based recognition retaining the original feature semantics. */
@RestController
public class RecognitionController {
    private final RecognitionService service;
    private final SessionService session;
    public RecognitionController(RecognitionService service,SessionService session) { this.service=service;this.session=session; }
    public record RecognitionDTO(String text) { }
    @PostMapping("/api/v1/recognition")
    public ResponseEntity<Object> recognize(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody RecognitionDTO body) {
        session.identity(auth);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.recognize(body.text()));
    }
}
