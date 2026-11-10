package dao.impl;

import dao.CommentDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Comment;
import pojo.PageResult;
import utils.DatabaseUtil;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class CommentDaoImpl implements CommentDao {
    private static final Logger log = LoggerFactory.getLogger(CommentDaoImpl.class);

    /**
     * 查询主评论（parentId为0的评论）且主动查询第 1页回复
     */
    @Override
    public PageResult<Comment> queryMainComments(int resourceType, String resourceId, int page, int size, boolean sortByHot) {
        // 1. 查询主评论列表（原有逻辑）
        List<Comment> mainComments = findComments(resourceType, resourceId, "0", page, size, sortByHot);
        // 2. 查询主评论总数（原有逻辑）
        long total = countComments(resourceType, resourceId, "0");

        // 3. 新增：为每个主评论查询第一页回复
        for (Comment mainComment : mainComments) {
            // 查询当前主评论的第一页回复（固定查询第1页，每页4条）
            List<Comment> replies = this.queryReplies(
                    resourceType,
                    resourceId,
                    mainComment.getId(),  // 主评论ID作为parentId
                    1,  // 固定第一页
                    4   // 固定每页4条（可根据需求调整）
            );
            // 将回复设置到主评论的replies字段
            mainComment.setReplies(replies);
        }

        return new PageResult<>(page, size, total, mainComments);
    }
    /**
     * 查询回复评论（指定parentId的评论）
     */
    @Override
    public List<Comment> queryReplies(int resourceType, String resourceId, String parentId, int page, int size) {
        // 回复按时间倒序排列（最新的回复在前面）
        return findComments(resourceType, resourceId, parentId, page, size, false);
    }

    @Override
    public long queryCommentCount(int resourceType, String resourceId) {
        // SQL查询：统计指定资源的所有评论（主评论+回复）
        String sql = "SELECT COUNT(*) AS total_count FROM comments " +
                "WHERE resource_type = ? AND resource_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置参数
            stmt.setInt(1, resourceType);
            stmt.setLong(2, Long.parseLong(resourceId));

            // 执行查询
            try (ResultSet resultSet = stmt.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("total_count");
                }
            }
        } catch (SQLException e) {
            // 记录异常日志（实际项目中建议使用日志框架）
            log.error("查询评论总数失败: {}", e.getMessage(), e);
        }
        // 查询失败时返回0
        return 0;
    }


    /**
     * 通用评论查询方法
     * 可根据parentId区分查询主评论(parentId="0")或回复评论(parentId=具体评论ID)
     */
    @Override
    public List<Comment> findComments(int resourceType, String resourceId, String parentId,
                                      int page, int size, boolean sortByHot) {
        String sql = "SELECT c.*, u.username, u.avatar FROM comments c " +
                "JOIN users u ON c.user_id = u.id " +
                "WHERE c.resource_type = ? AND c.resource_id = ? AND c.parent_id = ? " +
                "AND c.status = 1 " +
                (sortByHot ? "ORDER BY c.like_count DESC, c.created_at DESC "
                        : "ORDER BY c.created_at DESC ") +
                "LIMIT ?, ?";

        int offset = (page - 1) * size;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resourceType);
            stmt.setLong(2, Long.parseLong(resourceId));

            // 处理parentId参数，0表示主评论
            if (parentId == null || parentId.trim().isEmpty()) {
                stmt.setLong(3, 0);
            } else {
                stmt.setLong(3, Long.parseLong(parentId));
            }

            stmt.setInt(4, offset);
            stmt.setInt(5, size);

            try (ResultSet rs = stmt.executeQuery()) {
                List<Comment> comments = new ArrayList<>();
                // 收集所有 replyToUserId，用于后续批量查询
                Set<Long> replyToUserIds = new LinkedHashSet<>();

                while (rs.next()) {
                    Comment comment = new Comment();
                    comment.setId(String.valueOf(rs.getLong("id")));
                    comment.setUserId(String.valueOf(rs.getLong("user_id")));
                    comment.setUsername(rs.getString("username"));
                    comment.setAvatar(rs.getString("avatar"));
                    comment.setResourceType(rs.getInt("resource_type"));
                    comment.setResourceId(String.valueOf(rs.getLong("resource_id")));
                    comment.setContent(rs.getString("content"));
                    comment.setParentId(String.valueOf(rs.getLong("parent_id")));

                    // 处理可能为null的回复目标用户ID
                    long replyToUserId = rs.getLong("reply_to_user_id");
                    comment.setReplyToUserId(replyToUserId > 0 ? String.valueOf(replyToUserId) : null);
                    if (replyToUserId > 0) {
                        replyToUserIds.add(replyToUserId);
                    }

                    comment.setLikeCount(rs.getInt("like_count"));
                    comment.setReplyCount(rs.getInt("reply_count"));
                    comment.setCreateTime(rs.getTimestamp("created_at"));
                    comments.add(comment);
                }

                // 批量查询回复目标用户名，避免 N+1 查询
                if (!replyToUserIds.isEmpty()) {
                    Map<Long, String> usernameMap = batchGetUsernamesByIds(replyToUserIds);
                    for (Comment comment : comments) {
                        String replyToUserIdStr = comment.getReplyToUserId();
                        if (replyToUserIdStr != null) {
                            long uid = Long.parseLong(replyToUserIdStr);
                            comment.setReplyToUsername(usernameMap.getOrDefault(uid, "未知用户"));
                        }
                    }
                }

                return comments;
            }

        } catch (SQLException e) {
            throw new RuntimeException("查询评论失败: " + e.getMessage(), e);
        }
    }

    /**
     * 统计评论数量
     */
    @Override
    public long countComments(int resourceType, String resourceId, String parentId) {
        String sql = "SELECT COUNT(*) FROM comments " +
                "WHERE resource_type = ? AND resource_id = ? AND parent_id = ? " +
                "AND status = 1";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resourceType);
            stmt.setLong(2, Long.parseLong(resourceId));

            // 处理parentId参数，0表示主评论
            if (parentId == null || parentId.trim().isEmpty()) {
                stmt.setLong(3, 0);
            } else {
                stmt.setLong(3, Long.parseLong(parentId));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("统计评论数量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加评论
     */
    @Override
    public long addComment(Comment comment) throws SQLException {
        String sql = "INSERT INTO comments (user_id, resource_type, resource_id, content, " +
                "parent_id, reply_to_user_id, like_count, reply_count, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, Long.parseLong(comment.getUserId()));
            stmt.setInt(2, comment.getResourceType());
            stmt.setLong(3, Long.parseLong(comment.getResourceId()));
            stmt.setString(4, comment.getContent());

            // 处理parentId，0表示主评论
            if (comment.getParentId() == null || comment.getParentId().trim().isEmpty()) {
                stmt.setLong(5, 0);
            } else {
                stmt.setLong(5, Long.parseLong(comment.getParentId()));
            }

            // 处理回复目标用户ID，0表示无
            if (comment.getReplyToUserId() == null || comment.getReplyToUserId().trim().isEmpty()) {
                stmt.setLong(6, 0);
            } else {
                stmt.setLong(6, Long.parseLong(comment.getReplyToUserId()));
            }

            stmt.setInt(7, 0); // 初始点赞数
            stmt.setInt(8, 0); // 初始回复数
            stmt.setInt(9, 1); // 状态：1-正常
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("创建评论失败，没有行被影响");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException("创建评论失败，无法获取ID");
                }
            }
        }
    }

    /**
     * 更新回复数量
     */
    @Override
    public void updateReplyCount(long parentId, int increment) throws SQLException {
        String sql = "UPDATE comments SET reply_count = reply_count + ? WHERE id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, increment);
            stmt.setLong(2, parentId);
            stmt.executeUpdate();
        }
    }

    /**
     * 根据ID查询评论
     */
    @Override
    public Comment findById(Long id) throws SQLException {
        String sql = "SELECT c.*, u.username, u.avatar FROM comments c " +
                "LEFT JOIN users u ON c.user_id = u.id " +
                "WHERE c.id = ? AND c.status = 1";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Comment comment = new Comment();
                    comment.setId(String.valueOf(rs.getLong("id")));
                    comment.setUserId(String.valueOf(rs.getLong("user_id")));
                    comment.setUsername(rs.getString("username"));
                    comment.setAvatar(rs.getString("avatar"));
                    comment.setResourceType(rs.getInt("resource_type"));
                    comment.setResourceId(String.valueOf(rs.getLong("resource_id")));
                    comment.setContent(rs.getString("content"));
                    comment.setParentId(String.valueOf(rs.getLong("parent_id")));

                    long replyToUserId = rs.getLong("reply_to_user_id");
                    comment.setReplyToUserId(replyToUserId > 0 ? String.valueOf(replyToUserId) : null);

                    // 设置回复目标用户名
                    if (replyToUserId > 0) {
                        comment.setReplyToUsername(getUsernameById(replyToUserId));
                    }

                    comment.setLikeCount(rs.getInt("like_count"));
                    comment.setReplyCount(rs.getInt("reply_count"));
                    comment.setCreateTime(rs.getTimestamp("created_at"));
                    return comment;
                }
                return null; // 没有找到记录
            }
        }
    }

    /**
     * 辅助方法：根据用户ID查询用户名
     */
    private String getUsernameById(long userId) throws SQLException {
        String sql = "SELECT username FROM users WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("username");
                }
                return "未知用户";
            }
        }
    }

    /**
     * 辅助方法：批量根据用户ID查询用户名，解决 N+1 查询问题
     */
    private Map<Long, String> batchGetUsernamesByIds(Collection<Long> userIds) throws SQLException {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = userIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        String sql = "SELECT id, username FROM users WHERE id IN (" + placeholders + ")";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (Long id : userIds) {
                stmt.setLong(index++, id);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Long, String> map = new HashMap<>();
                while (rs.next()) {
                    map.put(rs.getLong("id"), rs.getString("username"));
                }
                return map;
            }
        }
    }

    @Override
    public boolean likeComment(long commentId, String userId) throws SQLException {
        if (hasLiked(commentId, userId)) {
            return false;
        }
        String sql = "INSERT INTO comment_likes (comment_id, user_id,created_at) VALUES (?, ?,?)";
        Connection conn = DatabaseUtil.getCurrentConnection() != null ? DatabaseUtil.getCurrentConnection() : DatabaseUtil.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, commentId);
            stmt.setLong(2, Long.parseLong(userId));
            stmt.setTimestamp(3,new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            updateLikeCount(commentId, 1);
            return true;
        } catch (SQLException e) {
            log.error("点赞失败: commentId={}, userId={}", commentId, userId, e);
            throw new RuntimeException("点赞失败"+e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelLikeComment(long commentId, String userId) throws SQLException {
        // 先检查是否已经点赞
        if (!hasLiked(commentId, userId)) {
            return false; // 未点赞，无需取消
        }
        String sql = "DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?";
        Connection conn = DatabaseUtil.getCurrentConnection() != null ? DatabaseUtil.getCurrentConnection() : DatabaseUtil.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 1. 删除点赞记录
            stmt.setLong(1, commentId);
            stmt.setLong(2, Long.parseLong(userId));
            stmt.executeUpdate();
            // 2. 更新评论的点赞数
            updateLikeCount(commentId, -1);
            return true;
        } catch (SQLException e) {
            throw e;
        }
    }

    @Override
    public boolean hasLiked(long commentId, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM comment_likes WHERE comment_id = ? AND user_id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, commentId);
            stmt.setLong(2, Long.parseLong(userId));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getLikeCount(long commentId) throws SQLException {
        String sql = "SELECT like_count FROM comments WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, commentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("like_count");
                }
                return 0;
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateLikeCount(long commentId, int increment) throws SQLException {
        String sql = "UPDATE comments SET like_count = like_count + ? WHERE id = ?";
        try (PreparedStatement stmt = DatabaseUtil.getCurrentConnection().prepareStatement(sql)) {
            stmt.setInt(1, increment);
            stmt.setLong(2, commentId);
            stmt.executeUpdate();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
