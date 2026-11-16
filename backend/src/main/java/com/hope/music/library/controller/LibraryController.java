package com.hope.music.library.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hope.music.library.service.LibraryService;
import com.hope.music.auth.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

/** Caller IDs are obtained exclusively from a verified Boot session. */
@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {
    private final LibraryService library;
    private final SessionService session;

    public LibraryController(LibraryService library, SessionService session) {
        this.library = library;
        this.session = session;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Query(String songId, String songIds, Integer page, Integer size) { }

    @ModelAttribute
    public void preventPrivateResponseCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/song")
    public Object song(@RequestBody Query query) {
        return library.song(query.songId());
    }

    @PostMapping("/favorites")
    public Object favorites(@RequestHeader(value = "Authorization", required = false) String auth,
                            @RequestBody Query query) {
        return library.favorites(session.identity(auth),
                query.page() == null ? 1 : query.page(), query.size() == null ? 20 : query.size());
    }

    @PostMapping("/states")
    public Object states(@RequestHeader(value = "Authorization", required = false) String auth,
                         @RequestBody Query query) {
        return library.states(session.identity(auth), query.songIds());
    }

    @PostMapping("/favorite")
    public Object add(@RequestHeader(value = "Authorization", required = false) String auth,
                      @RequestBody Query query) {
        return library.set(session.identity(auth), query.songId(), true);
    }

    @PostMapping("/unfavorite")
    public Object remove(@RequestHeader(value = "Authorization", required = false) String auth,
                         @RequestBody Query query) {
        return library.set(session.identity(auth), query.songId(), false);
    }
}
