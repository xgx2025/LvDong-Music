package com.hope.music.search.service;

import com.hope.music.search.domain.vo.SearchVO;

/** Anonymous public catalogue search. */
public interface ISearchService {
    SearchVO search(String keyword, int page, int pageSize);
}
