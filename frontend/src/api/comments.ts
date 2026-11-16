import { useSessionStore } from "../stores/session";
import type { LegacyResult } from "./servlet";
export interface Comment {
  id: string; userId: string; username: string; avatar?: string; content: string;
  likeCount: number; userHasLiked: boolean; replyCount: number; replies?: Comment[]; createTime?: string; replyToUsername?: string;
}
export const comments = (resourceId: string, page: number, sortByHot: boolean, parentId = "0", replyPage = 1, replySize = 4) => useSessionStore().request<LegacyResult & { comments: { list: Comment[]; total: number } }>("commentServlet", "getComments", { resourceType: 1, resourceId, page, size: 10, replyPage, replySize, sortByHot, parentId });
export const publishComment = (resourceId: string, content: string, parentId = "0", replyToUserId = "") => useSessionStore().request("commentServlet", "createComment", { resourceType: 1, resourceId, content, parentId, replyToUserId });
export const likeComment = (commentId: string, like: boolean) => useSessionStore().request<LegacyResult & { likeCount: number; hasLiked: boolean }>("commentServlet", "likeComment", { commentId, like });
