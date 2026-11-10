package pojo;


public class CommentCreateRequest {

    private Integer resourceType;
    private Long resourceId;

    private String content;

    private Long parentId;       // 可为null，表示主评论
    private Long replyToUserId;  // 回复目标用户ID

    public CommentCreateRequest() {
    }

    public CommentCreateRequest(Integer resourceType, Long resourceId, String content, Long parentId, Long replyToUserId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.content = content;
        this.parentId = parentId;
        this.replyToUserId = replyToUserId;
    }

    public Integer getResourceType() {
        return resourceType;
    }

    public void setResourceType(Integer resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(Long replyToUserId) {
        this.replyToUserId = replyToUserId;
    }
}
