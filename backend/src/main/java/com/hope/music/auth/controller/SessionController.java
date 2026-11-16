package com.hope.music.auth.controller;

import com.hope.music.auth.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

/** JSON compatibility fields let Vue migrate individual actions without layout changes. */
@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    public record Credentials(String email, String password) { }
    public record Renewal(String refreshToken) { }

    @ModelAttribute
    public void preventCredentialCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/login")
    public Object login(@RequestBody Credentials body) {
        return service.login(body.email(), body.password());
    }

    @PostMapping("/refresh")
    public Object refresh(@RequestBody Renewal body) {
        return service.refresh(body.refreshToken());
    }

    @PostMapping("/logout")
    public Object logout(@RequestHeader(value = "Authorization", required = false) String auth,
                         @RequestBody Renewal body) {
        return service.logout(auth, body.refreshToken());
    }

    @PostMapping("/profile")
    public Object profile(@RequestHeader(value = "Authorization", required = false) String auth) {
        return service.profile(auth);
    }
}
