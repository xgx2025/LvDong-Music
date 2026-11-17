package com.hope.music.comment.service;

import com.hope.music.comment.dao.CommentDao;
import com.hope.music.comment.domain.dto.CommentQueryDTO;
import com.hope.music.library.service.LibraryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Owns publication checks, reply relationships and comment transactions. */
@Service
public class CommentService {
    private final CommentDao dao;
    public CommentService(CommentDao dao) { this.dao = dao; }

    private String song(CommentQueryDTO query, boolean lock) {
        if (!Integer.valueOf(1).equals(query.resourceType())) throw bad("仅支持歌曲评论");
        String id = LibraryService.id(query.resourceId());
        if (!dao.publishedSong(id, lock)) throw missing();
        return id;
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "歌曲或评论不存在"); }
    private static String parent(String value) { return value == null || value.isBlank() || value.equals("0") ? "0" : LibraryService.id(value); }
    private static int page(Integer value) { return value == null ? 1 : Math.max(1, value); }
    private static int size(Integer value, int fallback) { return value == null || value < 1 ? fallback : Math.min(100, value); }

    private Map<String, Object> root(String id, String songId, boolean lock) {
        var root = dao.findComment(id, lock);
        if (root == null || !Integer.valueOf(1).equals(root.get("status"))
                || !Integer.valueOf(1).equals(root.get("resource_type")) || !songId.equals(root.get("resource_id"))
                || !(root.get("parent_id") == null || "0".equals(root.get("parent_id")))) throw bad("回复必须属于同一首歌曲的主评论");
        return root;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object getComments(String userId, CommentQueryDTO query) {
        String songId = song(query, false), parentId = parent(query.parentId());
        boolean replies = !parentId.equals("0");
        if (replies) root(parentId, songId, false);
        int page = page(replies ? query.replyPage() : query.page());
        int size = size(replies ? query.replySize() : query.size(), replies ? 4 : 10);
        return Map.of("success", true, "comments", Map.of("list", dao.getComments(songId, parentId, userId, page, size,
                !replies && Boolean.TRUE.equals(query.sortByHot())), "total", dao.countComments(songId, parentId), "page", page, "size", size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object countComments(CommentQueryDTO query) {
        return Map.of("success", true, "totalCount", String.valueOf(dao.countComments(song(query, false), null)));
    }

    @Transactional
    public Object createComment(String userId, CommentQueryDTO query) {
        String content = query.content() == null ? "" : query.content().trim();
        if (content.isEmpty() || content.length() > 500) throw bad("评论内容须为1至500个字符");
        String songId = song(query, true), parentId = parent(query.parentId());
        String target = parent(query.replyToUserId());
        if (parentId.equals("0")) {
            if (!target.equals("0")) throw bad("主评论不能指定回复对象");
        } else {
            var root = root(parentId, songId, true);
            if (target.equals("0")) target = (String) root.get("user_id");
            if (!dao.threadContainsUser(parentId, songId, target)) throw bad("回复对象不属于当前评论会话");
        }
        String id = dao.insertComment(userId, songId, parentId, target, content);
        if (!parentId.equals("0")) dao.incrementReplyCount(parentId);
        return Map.of("success", true, "comment", Map.of("id", id, "userId", userId, "content", content, "parentId", parentId));
    }

    @Transactional
    public Object likeComment(String userId, CommentQueryDTO query) {
        String id = LibraryService.id(query.commentId());
        if (query.like() == null) throw bad("缺少点赞状态");
        var original = dao.findComment(id, false);
        if (original == null || !Integer.valueOf(1).equals(original.get("resource_type"))) throw missing();
        String songId = (String) original.get("resource_id");
        if (!dao.publishedSong(songId, true)) throw missing();
        var comment = dao.findComment(id, true);
        if (comment == null || !Integer.valueOf(1).equals(comment.get("status"))) throw missing();
        String parentId = (String) comment.get("parent_id");
        if (parentId != null && !parentId.equals("0")) root(parentId, songId, false);
        return dao.setLike(id, userId, query.like());
    }
}
