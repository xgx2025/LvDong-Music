package com.hope.music.search.controller;

import com.hope.music.common.Result;
import com.hope.music.search.domain.vo.SearchVO;
import com.hope.music.search.service.ISearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

/** New public API and a narrow adapter for the legacy JSON search client. */
@RestController
public class SearchController {
    private final ISearchService searchService;

    public SearchController(ISearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/search")
    public Result<SearchVO> search(@RequestParam(defaultValue = "") @Size(max = 200) String keyword,
                                   @RequestParam(defaultValue = "1") @Min(1) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(searchService.search(keyword, page, pageSize));
    }

    @PostMapping(value = "/searchServlet", params = "action=search", consumes = "application/json")
    public LegacyResult legacySearch(@Valid @RequestBody LegacySearchDTO request) {
        int page = request.page() == null ? 1 : Math.max(1, request.page());
        int size = request.pageSize() == null || request.pageSize() <= 0 ? 20 : Math.min(100, request.pageSize());
        return new LegacyResult(true, searchService.search(request.keyword(), page, size));
    }

    public record LegacySearchDTO(@Size(max = 200) String keyword, Integer page, Integer pageSize) { }
    public record LegacyResult(boolean success, Object message) { }
}
