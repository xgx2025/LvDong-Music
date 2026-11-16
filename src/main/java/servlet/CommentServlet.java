package servlet;

import annotation.ParamName;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Comment;
import pojo.PageResult;
import service.CommentService;
import service.impl.CommentServiceImpl;
import utils.JwtTokenUtil;
import utils.PaginationUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CommentServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(CommentServlet.class);

    CommentService commentService = new CommentServiceImpl();

    /**
     * 创建评论或回复
     */
    protected Object createComment(
            @ParamName("accessToken") String accessToken,
            @ParamName("resourceType") int resourceType,
            @ParamName("resourceId") String resourceId,
            @ParamName("content") String content,
            @ParamName("parentId") String parentId,
            @ParamName("replyToUserId") String replyToUserId) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证token
            if (accessToken == null || accessToken.isEmpty()) {
                result.put("success", false);
                result.put("message", "未提供令牌");
                return result;
            }

            Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
            String userId = (String) claims.get("id");

            if (userId == null || userId.isEmpty()) {
                log.warn("令牌无效，userId为空");
                result.put("success", false);
                result.put("message", "令牌无效");
                return result;
            }

            // 验证内容
            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "评论内容不能为空");
                return result;
            }
            if (content.trim().length() > 500) {
                result.put("success", false);
                result.put("message", "评论内容不能超过500个字符");
                return result;
            }
            if (resourceId == null || !resourceId.matches("^\\d+$")) {
                result.put("success", false);
                result.put("message", "资源ID格式不正确");
                return result;
            }

            // 构建Comment对象
            Comment comment = new Comment();
            comment.setUserId(userId);
            comment.setResourceType(resourceType);
            comment.setResourceId(resourceId);
            comment.setContent(content.trim());
            comment.setParentId(parentId);
            comment.setReplyToUserId(replyToUserId);

            // 调用Service添加评论
            Comment savedComment = commentService.addComment(comment);

            result.put("success", true);
            result.put("comment", savedComment);

        } catch (Exception e) {
            log.error("发表评论失败: resourceType={}, resourceId={}, parentId={}",
                    resourceType, resourceId, parentId, e);
            result.put("success", false);
            result.put("message", "评论失败，请稍后再试");
        }
        return result;
    }

    /**
     *
     * @param resourceType
     * @param resourceId
     * @throws ServletException
     * @throws IOException
     */
    protected Object getCommentCount(@ParamName("resourceType")int resourceType,@ParamName("resourceId")String resourceId) {
        Map<String,Object> result = new HashMap<>();
        try {

            long totalCount = commentService.queryCommentCount(resourceType, resourceId);
            result.put("success", true);
            result.put("totalCount", String.valueOf(totalCount));
            return result;
        }catch (Exception e){
            result.put("success", false);
            result.put("message", "评论总数查询失败，请稍后再试");
            return result;
        }
    }

    /**
     * 获取评论列表
     */
    protected Object getComments(
            @ParamName("accessToken") String accessToken,
            @ParamName("resourceType") int resourceType,
            @ParamName("resourceId") String resourceId,
            @ParamName("parentId") String parentId,
            @ParamName("page") int page,
            @ParamName("size") int size,
            @ParamName("replyPage") int replyPage,
            @ParamName("replySize") int replySize,
            @ParamName("sortByHot") boolean sortByHot) {

        Map<String, Object> response = new HashMap<>();
        try {
            // 分页参数钳制：客户端可以随便传 size，必须在这里设上限
            page = PaginationUtil.page(page);
            size = PaginationUtil.size(size);
            replyPage = PaginationUtil.page(replyPage);
            replySize = PaginationUtil.size(replySize, 4);

            // 验证token
            if (accessToken == null || accessToken.isEmpty()) {
                response.put("success", false);
                response.put("message", "未提供令牌");
                return response;
            }

            Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
            String userId = (String) claims.get("id");
            // 参数验证与修正
            if (resourceId == null || resourceId.isEmpty()) {
                response.put("success", false);
                response.put("message", "资源ID不能为空");
                return response;
            }

            // 调用Service
            PageResult<Comment> result = commentService.queryComments(
                    resourceType,
                    resourceId,
                    parentId,
                    page,
                    size,
                    replyPage,
                    replySize,
                    sortByHot,
                    userId
            );
            log.debug("评论查询结果: {}", result);

            response.put("success", true);
            response.put("comments", result);
            return response;

        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "资源ID格式错误");
            return error;
        } catch (Exception e) {
            log.error("获取评论失败: resourceType={}, resourceId={}, parentId={}",
                    resourceType, resourceId, parentId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "获取评论失败，请稍后再试");
            return error;
        }
    }

    /**
     * 评论点赞/取消点赞
     */
    protected Object likeComment(
            @ParamName("accessToken") String accessToken,
            @ParamName("commentId") String commentId,
            @ParamName("like") boolean like) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证token
            if (accessToken == null || accessToken.isEmpty()) {
                result.put("success", false);
                result.put("message", "未提供令牌");
                return result;
            }

            Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
            String userId = (String) claims.get("id");

            if (userId == null || userId.isEmpty()) {
                result.put("success", false);
                result.put("message", "令牌无效");
                return result;
            }

            // 验证评论ID
            if (commentId == null || commentId.isEmpty()) {
                result.put("success", false);
                result.put("message", "评论ID不能为空");
                return result;
            }

            long commentIdLong;
            try {
                commentIdLong = Long.parseLong(commentId);
            } catch (NumberFormatException e) {
                result.put("success", false);
                result.put("message", "评论ID格式错误");
                return result;
            }

            // 处理点赞业务
            if (like) {
                commentService.likeComment(commentIdLong, userId);
                result.put("message", "点赞成功");
            } else {
                commentService.cancelLikeComment(commentIdLong, userId);
                result.put("message", "取消点赞成功");
            }

            // 获取更新后的点赞数和点赞状态
            int likeCount = commentService.getCommentLikeCount(commentIdLong);
            boolean hasLiked = commentService.hasUserLikedComment(commentIdLong, userId);

            result.put("likeCount", likeCount);
            result.put("hasLiked", hasLiked);
            result.put("success", true);

        } catch (Exception e) {
            log.error("评论点赞操作失败: commentId={}, like={}", commentId, like, e);
            result.put("success", false);
            result.put("message", "操作失败，请稍后再试");
        }

        return result;
    }
}
