package com.hope.music.comment.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.comment.domain.dto.CommentQueryDTO;
import com.hope.music.comment.service.CommentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

/** Authenticated comment API retaining the existing Vue response shape. */
@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {
    private final CommentService comments;
    private final SessionService session;
    public CommentController(CommentService comments, SessionService session) { this.comments = comments; this.session = session; }
    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @PostMapping("/page")
    public Object page(@RequestHeader(value="Authorization", required=false) String auth, @RequestBody CommentQueryDTO query) {
        return comments.getComments(session.identity(auth), query);
    }
    @PostMapping("/count")
    public Object count(@RequestHeader(value="Authorization", required=false) String auth, @RequestBody CommentQueryDTO query) {
        session.identity(auth); return comments.countComments(query);
    }
    @PostMapping
    public Object create(@RequestHeader(value="Authorization", required=false) String auth, @RequestBody CommentQueryDTO query) {
        return comments.createComment(session.identity(auth), query);
    }
    @PostMapping("/like")
    public Object like(@RequestHeader(value="Authorization", required=false) String auth, @RequestBody CommentQueryDTO query) {
        return comments.likeComment(session.identity(auth), query);
    }
}
