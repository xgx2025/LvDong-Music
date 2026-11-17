package com.hope.music.comment.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.util.*;

/** SQL adapter for visible song comments and serialized reaction counters. */
@Repository
public class CommentDao {
    private final JdbcTemplate jdbc;

    public CommentDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean publishedSong(String songId, boolean lock) {
        return !jdbc.query("SELECT id FROM songs WHERE id=? AND state=1" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> rs.getString(1), songId).isEmpty();
    }

    public Map<String, Object> findComment(String id, boolean lock) {
        return jdbc.query("SELECT id,user_id,resource_type,resource_id,parent_id,status FROM comments WHERE id=?"
                + (lock ? " FOR UPDATE" : ""), (rs, n) -> {
            Map<String, Object> row = new HashMap<>();
            for (String key : List.of("id", "user_id", "resource_id", "parent_id")) row.put(key, rs.getString(key));
            row.put("resource_type", rs.getInt("resource_type"));
            row.put("status", rs.getInt("status"));
            return row;
        }, id).stream().findFirst().orElse(null);
    }

    public long countComments(String songId, String parentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comments c WHERE resource_type=1 AND resource_id=? AND status=1"
                + " AND (COALESCE(parent_id,0)=0 OR EXISTS(SELECT 1 FROM comments p WHERE p.id=c.parent_id AND p.status=1 AND p.resource_type=c.resource_type AND p.resource_id=c.resource_id AND COALESCE(p.parent_id,0)=0))"
                + (parentId == null ? "" : " AND COALESCE(parent_id,0)=?"), Long.class,
                parentId == null ? new Object[]{songId} : new Object[]{songId, parentId});
    }

    public List<Map<String, Object>> getComments(String songId, String parentId, String userId,
                                                int page, int size, boolean hot) {
        return jdbc.query("SELECT c.*,u.username,u.avatar,t.username reply_name,"
                + "EXISTS(SELECT 1 FROM comment_likes l WHERE l.comment_id=c.id AND l.user_id=?) liked,"
                + "(SELECT COUNT(*) FROM comments r WHERE r.parent_id=c.id AND r.status=1 AND r.resource_type=1 AND r.resource_id=c.resource_id) visible_replies "
                + "FROM comments c LEFT JOIN users u ON u.id=c.user_id LEFT JOIN users t ON t.id=c.reply_to_user_id "
                + "WHERE c.resource_type=1 AND c.resource_id=? AND COALESCE(c.parent_id,0)=? AND c.status=1 ORDER BY "
                + (hot ? "COALESCE(c.like_count,0) DESC," : "") + "c.created_at DESC,c.id DESC LIMIT ? OFFSET ?", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id")); row.put("userId", rs.getString("user_id"));
            row.put("resourceType", 1); row.put("resourceId", rs.getString("resource_id"));
            row.put("parentId", rs.getString("parent_id")); row.put("replyToUserId", rs.getString("reply_to_user_id"));
            row.put("username", rs.getString("username")); row.put("avatar", rs.getString("avatar"));
            row.put("replyToUsername", rs.getString("reply_name")); row.put("content", rs.getString("content"));
            row.put("likeCount", rs.getInt("like_count")); row.put("replyCount", rs.getLong("visible_replies"));
            row.put("userHasLiked", rs.getBoolean("liked")); row.put("createTime", rs.getString("created_at"));
            row.put("replies", List.of());
            return row;
        }, userId, songId, parentId, size, ((long) page - 1) * size);
    }

    public String insertComment(String userId, String songId, String parentId, String target, String content) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO comments(user_id,resource_type,resource_id,content,parent_id,reply_to_user_id,like_count,reply_count,status,created_at) VALUES(?,1,?,?,?,?,0,0,1,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, userId); statement.setString(2, songId); statement.setString(3, content);
            statement.setString(4, parentId); statement.setString(5, target);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).toString();
    }

    public boolean threadContainsUser(String parentId, String songId, String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE resource_type=1 AND resource_id=? AND status=1 AND (id=? OR parent_id=?) AND user_id=?",
                Long.class, songId, parentId, parentId, userId) > 0;
    }

    public void incrementReplyCount(String parentId) {
        jdbc.update("UPDATE comments SET reply_count=COALESCE(reply_count,0)+1 WHERE id=?", parentId);
    }

    public Map<String, Object> setLike(String commentId, String userId, boolean like) {
        long existing = jdbc.queryForObject("SELECT COUNT(*) FROM comment_likes WHERE comment_id=? AND user_id=?", Long.class, commentId, userId);
        if (like && existing == 0) {
            jdbc.update("INSERT INTO comment_likes(comment_id,user_id,created_at) VALUES(?,?,CURRENT_TIMESTAMP)", commentId, userId);
            jdbc.update("UPDATE comments SET like_count=COALESCE(like_count,0)+1 WHERE id=?", commentId);
        } else if (!like) {
            int removed = jdbc.update("DELETE FROM comment_likes WHERE comment_id=? AND user_id=?", commentId, userId);
            if (removed > 0) jdbc.update("UPDATE comments SET like_count=GREATEST(COALESCE(like_count,0)-?,0) WHERE id=?", removed, commentId);
        }
        return Map.of("success", true, "hasLiked", like, "likeCount",
                jdbc.queryForObject("SELECT COALESCE(like_count,0) FROM comments WHERE id=?", Integer.class, commentId));
    }
}
