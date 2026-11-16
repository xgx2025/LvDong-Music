import { apiRequest } from "./http";
import type { SearchResult } from "../types";
export function search(keyword: string, page = 1, signal?: AbortSignal) {
  return apiRequest<SearchResult>(
    `/api/v1/search?${new URLSearchParams({ keyword, page: String(page), pageSize: "20" })}`,
    signal,
  );
}
