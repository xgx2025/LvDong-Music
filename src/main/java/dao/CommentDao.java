package dao;

import pojo.Comment;
import pojo.PageResult;

import java.sql.SQLException;
import java.util.List;

public interface CommentDao {
    /**
     * 分页查询评论
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param parentId 父评论ID(0表示主评论)
     * @param page 页码
     * @param size 每页大小
     * @param sortByHot 是否按热度排序
     * @return 评论列表
     */
    List<Comment> findComments(int resourceType, String resourceId, String parentId,
                               int page, int size, boolean sortByHot);

    /**
     * 查询评论总数
     */
    long countComments(int resourceType, String resourceId, String parentId);

    /**
     * 添加新评论
     * @param comment 评论对象
     * @return 插入成功的评论ID
     */
    long addComment(Comment comment) throws SQLException;

    /**
     * 更新评论的回复数
     * @param parentId 父评论ID
     * @param increment 增量（通常为+1）
     */
    void updateReplyCount(long parentId, int increment) throws SQLException;

    Comment findById(Long parentId) throws SQLException;

    PageResult<Comment> queryMainComments(int resourceType, String resourceId, int page, int size, boolean sortByHot);

    List<Comment> queryReplies(int resourceType, String resourceId, String id, int i, int i1);

    long queryCommentCount(int resourceType, String resourceId);

    /**
     * 点赞评论
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 是否点赞成功（true:新点赞，false:已取消点赞）
     */
    boolean likeComment(long commentId, String userId) throws SQLException;

    /**
     * 取消点赞
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 是否取消成功
     */
    boolean cancelLikeComment(long commentId, String userId) throws SQLException;

    /**
     * 检查用户是否已点赞该评论
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    boolean hasLiked(long commentId, String userId) throws SQLException;

    /**
     * 获取评论的点赞数
     * @param commentId 评论ID
     * @return 点赞数量
     */
    int getLikeCount(long commentId) throws SQLException;

    /**
     * 更新评论的点赞数
     * @param commentId 评论ID
     * @param increment 增量（+1或-1）
     */
    void updateLikeCount(long commentId, int increment) throws SQLException;
}
