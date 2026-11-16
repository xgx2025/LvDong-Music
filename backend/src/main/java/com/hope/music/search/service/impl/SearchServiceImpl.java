package com.hope.music.search.service.impl;

import com.hope.music.search.dao.SearchDao;
import com.hope.music.search.domain.vo.SearchVO;
import com.hope.music.search.service.ISearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Runs counts and page reads on one connection and consistent database snapshot. */
@Service
public class SearchServiceImpl implements ISearchService {
    private final SearchDao searchDao;

    public SearchServiceImpl(SearchDao searchDao) {
        this.searchDao = searchDao;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchVO search(String keyword, int page, int pageSize) {
        return searchDao.search(keyword == null ? "" : keyword, page, pageSize);
    }
}
