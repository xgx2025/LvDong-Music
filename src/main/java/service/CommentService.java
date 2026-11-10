package service;

import pojo.Comment;
import pojo.PageResult;

public interface CommentService {
    /**
     * 分页查询评论
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param parentId 父评论ID，为null时查询根评论
     * @param page 页码
     * @param size 每页大小
     * @param sortByHot 是否按热度排序
     * @return 分页结果
     */
    PageResult<Comment> queryComments(int resourceType, String resourceId, String parentId, int page, int size, int replyPage,     // 回复页码（新增）
                                      int replySize, boolean sortByHot,String userId);

    long queryCommentCount(int resourceType, String resourceId);

    /**
     * 添加新评论
     * @param comment 评论对象
     * @return 包含完整信息的评论对象
     * @throws RuntimeException 处理过程中发生错误时抛出
     */
    Comment addComment(Comment comment) throws RuntimeException;

    void likeComment(long l, String userId);

    void cancelLikeComment(long l, String userId);

    int getCommentLikeCount(long l);

    boolean hasUserLikedComment(long commentIdLong, String userId);


//
//    /**
//     * 对评论进行点赞
//     * @param commentId 评论ID
//     * @param userId 用户ID
//     * @throws RuntimeException 处理过程中发生错误时抛出
//     */
//    void likeComment(Long commentId, String userId) throws RuntimeException;
//
//    /**
//     * 取消对评论的点赞
//     * @param commentId 评论ID
//     * @param userId 用户ID
//     * @throws RuntimeException 处理过程中发生错误时抛出
//     */
//    void cancelLikeComment(Long commentId, String userId) throws RuntimeException;

//    /**
//     * 获取评论的点赞数量
//     * @param commentId 评论ID
//     * @return 点赞数量
//     */
//    int getCommentLikeCount(Long commentId);
}
