package com.hope.music.comment.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Legacy request fields retained while identity comes from the verified session. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentQueryDTO(Integer resourceType, String resourceId, String parentId,
        Integer page, Integer size, Integer replyPage, Integer replySize, Boolean sortByHot,
        String content, String replyToUserId, String commentId, Boolean like) { }
