package com.hope.music.provider.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.common.Result;
import com.hope.music.provider.service.QqMusicConnectionService;
import com.hope.music.provider.service.QqMusicConnectionService.Capabilities;
import com.hope.music.provider.service.QqMusicConnectionService.ConnectionStatus;
import com.hope.music.provider.service.QqMusicConnectionService.QrView;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

/** QQ connections always use the authenticated website identity. */
@RestController
@RequestMapping("/api/v1/music-providers/qq")
public class QqMusicConnectionController {
    private final SessionService sessions;
    private final QqMusicConnectionService connections;
    public QqMusicConnectionController(SessionService sessions, QqMusicConnectionService connections) {
        this.sessions = sessions; this.connections = connections;
    }
    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/capabilities")
    public Result<Capabilities> capabilities(@RequestHeader(value = "Authorization", required = false) String auth) {
        sessions.identity(auth); return Result.success(connections.capabilities());
    }
    @GetMapping("/connection")
    public Result<ConnectionStatus> status(@RequestHeader(value = "Authorization", required = false) String auth) {
        return Result.success(connections.status(sessions.identity(auth)));
    }
    @PostMapping("/login")
    public Result<QrView> start(@RequestHeader(value = "Authorization", required = false) String auth,
                               @Valid @RequestBody StartRequest request) {
        return Result.success(connections.start(sessions.identity(auth), request.channel()));
    }
    @PostMapping("/login/{id}/check")
    public Result<QrView> check(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return Result.success(connections.check(sessions.identity(auth), id));
    }
    @DeleteMapping("/login/{id}")
    public Result<Void> cancel(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        connections.cancel(sessions.identity(auth), id); return Result.success(null);
    }
    @DeleteMapping("/connection")
    public Result<Void> disconnect(@RequestHeader(value = "Authorization", required = false) String auth) {
        connections.disconnect(sessions.identity(auth)); return Result.success(null);
    }
    /** Only an advertised login channel can be requested. */
    public record StartRequest(@jakarta.validation.constraints.NotNull @Pattern(regexp = "qqmusic|qq|wechat") String channel) { }
}
