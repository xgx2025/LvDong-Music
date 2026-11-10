package pojo;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class Comment {

    private String id;                   // 评论ID
    private String userId;               // 评论用户ID
    private String username;           // 评论用户名
    private String avatar;             // 评论用户头像
    private Integer resourceType;      // 资源类型(1-歌曲/2-专辑/3-歌单)
    private String resourceId;           // 资源ID
    private String content;            // 评论内容
    private String parentId;             // 父评论ID(0表示主评论)
    private String replyToUserId;        // 回复目标用户ID
    private String replyToUsername;    // 回复目标用户名
    private Integer likeCount;         // 点赞数
    private Integer replyCount;        // 回复数
    private Timestamp createTime;       // 创建时间
    private Boolean hasReplies;
    private List<Comment> replies;     // 回复列表(二级评论)
    private boolean userHasLiked;

    public Comment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getResourceType() {
        return resourceType;
    }

    public void setResourceType(Integer resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(String replyToUserId) {
        this.replyToUserId = replyToUserId;
    }

    public String getReplyToUsername() {
        return replyToUsername;
    }

    public void setReplyToUsername(String replyToUsername) {
        this.replyToUsername = replyToUsername;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public void setReplies(List<Comment> replies) {
        this.replies = replies;
    }



    public void setUserHasLiked(boolean userHasLiked) {
        this.userHasLiked = userHasLiked;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", avatar='" + avatar + '\'' +
                ", resourceType=" + resourceType +
                ", resourceId='" + resourceId + '\'' +
                ", content='" + content + '\'' +
                ", parentId='" + parentId + '\'' +
                ", replyToUserId='" + replyToUserId + '\'' +
                ", replyToUsername='" + replyToUsername + '\'' +
                ", likeCount=" + likeCount +
                ", replyCount=" + replyCount +
                ", createTime=" + createTime +
                ", hasReplies=" + hasReplies +
                ", replies=" + replies +
                ", userHasLiked=" + userHasLiked +
                '}';
    }

    public void setHasReplies(boolean b) {
        this.replies = replies;
        // 自动根据回复列表是否为空设置hasReplies
        this.hasReplies = replies != null && !replies.isEmpty();
    }
}
