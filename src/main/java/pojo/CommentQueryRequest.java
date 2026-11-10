package pojo;

public class CommentQueryRequest {
    private Integer resourceType;
    private Long resourceId;

    private Long parentId = 0L;  // 默认查询主评论
    private Integer sortType = 1; // 1-按时间倒序 2-按热度
    private Integer page = 1;
    private Integer size = 20;

    public CommentQueryRequest() {
    }

    public CommentQueryRequest(Integer resourceType, Long resourceId, Long parentId, Integer sortType, Integer page, Integer size) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.parentId = parentId;
        this.sortType = sortType;
        this.page = page;
        this.size = size;
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortType() {
        return sortType;
    }

    public void setSortType(Integer sortType) {
        this.sortType = sortType;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
