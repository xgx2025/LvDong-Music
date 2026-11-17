package com.hope.music.creator.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.creator.service.CreatorService;
import com.hope.music.creator.service.CreatorService.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

/** The verified caller owns all profile, application and upload operations. */
@RestController
@RequestMapping("/api/v1/creator")
public class CreatorController {
    private final CreatorService service;
    private final SessionService session;
    public CreatorController(CreatorService service,SessionService session) { this.service=service;this.session=session; }
    @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @PostMapping("/profile") public Object profile(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody ProfileDTO body) { return service.profile(session.identity(auth),body); }
    @PostMapping("/status") public Object status(@RequestHeader(value="Authorization",required=false) String auth) { return service.status(session.identity(auth)); }
    @PostMapping("/application") public Object apply(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody ApplicationDTO body) { return service.apply(session.identity(auth),body); }
    @PostMapping("/upload") public Object upload(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody UploadDTO body) { return service.upload(session.identity(auth),body); }
}
