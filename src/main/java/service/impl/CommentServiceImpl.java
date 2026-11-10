package service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dao.CommentDao;
import dao.UserDao;
import dao.impl.CommentDaoImpl;
import dao.impl.UserDaoImpl;
import pojo.Comment;
import pojo.PageResult;
import pojo.User;
import service.CommentService;
import utils.DatabaseUtil;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class CommentServiceImpl implements CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    UserDao userDao = new UserDaoImpl();
    CommentDao commentDao = new CommentDaoImpl();

    @Override
    public PageResult<Comment> queryComments(int resourceType, String resourceId, String parentId,
                                             int page, int size, int replyPage, int replySize,
                                             boolean sortByHot, String userId) {
        PageResult<Comment> result;

        // 区分查询主评论还是回复
        if (parentId == null || "0".equals(parentId)) {
            // 查询主评论（parentId=0）
            result = commentDao.queryMainComments(resourceType, resourceId, page, size, sortByHot);
        } else {
            // 查询回复（指定parentId）
            List<Comment> replies = commentDao.queryReplies(
                    resourceType, resourceId, parentId, replyPage, replySize);
            long total = commentDao.countComments(resourceType, resourceId, parentId);
            result = new PageResult<>(replyPage, replySize, total, replies);
        }

        // 补充用户信息和点赞状态
        for (Comment comment : result.getList()) {
            enrichUserInfo(comment);
            setCommentLikeStatus(comment, userId);
        }

        return result;
    }

    /**
     * 为评论设置当前用户的点赞状态
     */
    private void setCommentLikeStatus(Comment comment, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            // 未登录用户，默认未点赞
            comment.setUserHasLiked(false);
            return;
        }

        try {
            // 检查当前用户是否点赞了该评论
            boolean hasLiked = commentDao.hasLiked(Long.parseLong(comment.getId()), userId);
            comment.setUserHasLiked(hasLiked);
        } catch (SQLException e) {
            // 异常情况下默认设置为未点赞，不影响主流程
            comment.setUserHasLiked(false);
            log.error("查询评论点赞状态失败: {}", e.getMessage());
        } catch (NumberFormatException e) {
            comment.setUserHasLiked(false);
            log.error("评论ID格式错误: {}", e.getMessage());
        }
    }

    @Override
    public long queryCommentCount(int resourceType, String resourceId){
        return commentDao.queryCommentCount(resourceType,resourceId);
    }

    @Override
    public Comment addComment(Comment comment) throws RuntimeException {
        try {
            // 验证父评论是否存在（如果是回复）
            if (comment.getParentId() != null) {
                validateParentComment(comment);
            }

            // 设置创建时间
            comment.setCreateTime(new Timestamp(System.currentTimeMillis()));
            // 保存评论
            long commentId = commentDao.addComment(comment);
            comment.setId(String.valueOf(commentId));

            // 如果是回复，更新父评论的回复数
            if (comment.getParentId() != null) {
                commentDao.updateReplyCount(Long.parseLong(comment.getParentId()), 1);
            }

            // 补充用户信息
            enrichUserInfo(comment);

            return comment;

        } catch (SQLException e) {
            throw new RuntimeException("添加评论失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void likeComment(long commentId, String userId) {
        try {
            // 检查评论是否存在
            Comment comment = commentDao.findById(commentId);
            if (comment == null) {
                throw new RuntimeException("评论不存在");
            }

            // 开启事务，执行点赞操作
            DatabaseUtil.beginTransaction();
            try {
                boolean result = commentDao.likeComment(commentId, userId);
                if (!result) {
                    throw new RuntimeException("点赞失败，可能已点赞");
                }
                DatabaseUtil.commit();
            } catch (Exception e) {
                DatabaseUtil.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("点赞操作失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelLikeComment(long commentId, String userId) {
        try {
            // 检查评论是否存在
            Comment comment = commentDao.findById(commentId);
            if (comment == null) {
                throw new RuntimeException("评论不存在");
            }

            // 开启事务，执行取消点赞操作
            DatabaseUtil.beginTransaction();
            try {
                boolean result = commentDao.cancelLikeComment(commentId, userId);
                if (!result) {
                    throw new RuntimeException("取消点赞失败，可能未点赞");
                }
                DatabaseUtil.commit();
            } catch (Exception e) {
                DatabaseUtil.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("取消点赞操作失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int getCommentLikeCount(long commentId) {
        try {
            return commentDao.getLikeCount(commentId);
        } catch (SQLException e) {
            throw new RuntimeException("获取点赞数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查用户是否已点赞某评论
     */
    public boolean hasUserLikedComment(long commentId, String userId) {
        try {
            return commentDao.hasLiked(commentId, userId);
        } catch (SQLException e) {
            throw new RuntimeException("检查点赞状态失败: " + e.getMessage(), e);
        }
    }

    private void validateParentComment(Comment comment) throws SQLException, RuntimeException {
        // 实现验证父评论是否存在且属于同一资源
        Comment parent = commentDao.findById(Long.valueOf(comment.getParentId()));
        if (parent == null) {
            throw new RuntimeException("父评论不存在");
        }
        if (!parent.getResourceId().equals(comment.getResourceId()) ||
                parent.getResourceType() != comment.getResourceType()) {
            throw new RuntimeException("评论资源不匹配");
        }
    }

    private void enrichUserInfo(Comment comment) {
        // 从userDao获取用户信息并设置到comment对象
        User user = userDao.findById(Long.valueOf(comment.getUserId()));
        if (user != null) {
            comment.setUsername(user.getUsername());
            comment.setAvatar(user.getAvatar());
        }
    }
}
