package pojo;

public class LikeOperation {
    private Long commentId;
    private Boolean isLike;  // true-点赞 false-取消点赞

    public LikeOperation() {
    }

    public LikeOperation(Long commentId, Boolean isLike) {
        this.commentId = commentId;
        this.isLike = isLike;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Boolean getLike() {
        return isLike;
    }

    public void setLike(Boolean like) {
        isLike = like;
    }
}
